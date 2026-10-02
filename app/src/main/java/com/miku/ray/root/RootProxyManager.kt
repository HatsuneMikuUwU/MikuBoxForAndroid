package com.miku.ray.root

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.PackageUidResolver
import com.miku.ray.util.Utils
import java.io.File

object RootProxyManager {

    private const val TAG = AppConfig.TAG

    private const val CHAIN = AppConfig.ROOT_IPTABLES_CHAIN
    private const val TUN = AppConfig.ROOT_TUN_NAME
    private const val TABLE = AppConfig.ROOT_ROUTE_TABLE
    private const val PRIORITY = AppConfig.ROOT_RULE_PRIORITY
    private const val FWMARK = AppConfig.ROOT_FWMARK
    private const val MARK = AppConfig.ROOT_MARK_ROUTE

    private val bypassCidrs = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
    )

    private val bypassCidrsV6 = listOf(
        "::1/128", "fe80::/10", "fc00::/7", "ff00::/8"
    )

    fun start(context: Context): Boolean {
        LogUtil.i(TAG, "RootProxyManager: start")
        teardown(context)
        val script = buildTun2socksSetup(context) ?: return false
        val result = RootShell.runScript(context, "setup_rules.sh", script)
        if (!result.success) {
            LogUtil.e(TAG, "RootProxyManager: setup failed, rolling back:\n${result.output}")
            teardown(context)
            return false
        }
        LogUtil.i(TAG, "RootProxyManager: rules installed")
        return true
    }

    fun startClientSharing(context: Context): Boolean {
        LogUtil.i(TAG, "RootProxyManager: startClientSharing")
        teardown(context)
        val script = buildTun2socksSetup(context, captureDeviceTraffic = false, forceLanShare = true)
        ?: return false
        val result = RootShell.runScript(context, "setup_rules.sh", script)
        if (!result.success) {
            LogUtil.e(TAG, "RootProxyManager: client sharing setup failed:\n${result.output}")
            teardown(context)
            return false
        }
        LogUtil.i(TAG, "RootProxyManager: LAN client sharing installed")
        return true
    }

    fun stop(context: Context) {
        teardown(context)
        LogUtil.i(TAG, "RootProxyManager: rules removed")
    }

    fun stopFull(context: Context) {
        LogUtil.i(TAG, "RootProxyManager: stopFull")
        teardown(context)
    }

    private fun teardown(context: Context) {
        RootShell.runScript(context, "teardown_rules.sh", buildTeardown(context))
    }

    private fun buildTun2socksSetup(
        context: Context,
        captureDeviceTraffic: Boolean = true,
        forceLanShare: Boolean = false,
    ): String? {
        val bin = File(context.applicationInfo.nativeLibraryDir, AppConfig.ROOT_TUN2SOCKS_BIN)
        if (!bin.exists()) {
            LogUtil.e(TAG, "RootProxyManager: hev-socks5-tunnel binary missing at ${bin.absolutePath}")
            return null
        }
        val appUid = context.applicationInfo.uid
        val socksUsername = SettingsManager.getSocksUsername()
        val socksPassword = SettingsManager.getSocksPassword()
        val port = SettingsManager.getSocksPort()
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        if (!runDir.isDirectory && !runDir.mkdirs()) {
            LogUtil.e(TAG, "RootProxyManager: failed to create runtime directory at ${runDir.absolutePath}")
            return null
        }
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val logFile = File(runDir, "tun2socks.log").absolutePath
        val cfgFile = File(runDir, "tun2socks.yml")
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val lanShare = forceLanShare || MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)
        val corePid = Process.myPid()
        val config = buildHevConfig(socksUsername, socksPassword, port, ipv6)
        if (!writeHevConfig(cfgFile, config)) return null
        val cfgPath = cfgFile.absolutePath

        val perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)
        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        val selectedUids = if (perAppEnabled) {
            val pkgs = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toList().orEmpty()
            if (pkgs.isNotEmpty()) PackageUidResolver.packageNamesToUids(context, pkgs) else emptyList()
        } else {
            emptyList()
        }

        return buildString {
            appendLine("set -e")
            appendLine("BIN='${bin.absolutePath}'")
            appendLine("nohup sh -c 'while true; do echo ${AppConfig.ROOT_OOM_SCORE} > /proc/$corePid/oom_score_adj 2>/dev/null; sleep 5; done' >/dev/null 2>&1 &")
            appendLine("echo \$! > '$oomGuardPid'")
            appendLine("if [ ! -e /dev/net/tun ]; then mkdir -p /dev/net; mknod /dev/net/tun c 10 200; chmod 666 /dev/net/tun; fi")

            appendLine("nohup \"\$BIN\" '$cfgPath' >'$logFile' 2>&1 &")
            appendLine("T2S_PID=\$!")
            appendLine("echo \$T2S_PID > '$pidFile'")
            appendLine("echo ${AppConfig.ROOT_OOM_SCORE} > /proc/\$T2S_PID/oom_score_adj 2>/dev/null || true")
            appendLine("i=0; while [ \$i -lt 20 ]; do ip link show $TUN >/dev/null 2>&1 && break; sleep 0.3; i=\$((i+1)); done")
            appendLine("ip link show $TUN >/dev/null 2>&1 || { echo 'tun device did not come up'; cat '$logFile' 2>/dev/null; exit 1; }")
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/$TUN/rp_filter 2>/dev/null || true")
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
            appendLine("ip addr add ${AppConfig.ROOT_TUN_ADDR_V4} dev $TUN 2>/dev/null || true")
            appendLine("ip link set dev $TUN up")
            appendLine("ip route replace default dev $TUN table $TABLE")
            appendLine("ip rule add fwmark $MARK table $TABLE priority $PRIORITY")
            if (captureDeviceTraffic) {
                append(buildMangleMarking("iptables", appUid, perAppEnabled, bypassApps, selectedUids))
            }
            if (lanShare) {
                append(buildLanShareSetup(captureDeviceTraffic, ipv6))
            }
            if (captureDeviceTraffic) {
                appendLine("set +e")
                if (ipv6) {
                    appendLine("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $TUN 2>/dev/null || true")
                    appendLine("ip -6 route replace default dev $TUN table $TABLE 2>/dev/null || true")
                    appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                    append(buildMangleMarking("ip6tables", appUid, perAppEnabled, bypassApps, selectedUids))
                } else {
                    append(buildV6Blackhole(appUid, perAppEnabled, bypassApps, selectedUids))
                }
            }
        }
    }

    private fun buildHevConfig(socksUsername: String?, socksPassword: String?, socksPort: Int, ipv6: Boolean): String {
        val v4 = AppConfig.ROOT_TUN_ADDR_V4.substringBefore("/")
        val v6 = AppConfig.ROOT_TUN_ADDR_V6.substringBefore("/")
        val icmpMode = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_ICMP) ?: "off"
        val udpMode = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_UDP_MODE) ?: "udp"
        val udpAddress = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_UDP_ADDRESS)
        val pipeline = MmkvManager.decodeSettingsBool(AppConfig.PREF_HEV_TUNNEL_PIPELINE, false)
        val tcpFastOpen = MmkvManager.decodeSettingsBool(AppConfig.PREF_HEV_TUNNEL_TCP_FASTOPEN, true)
        val logLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL)
        return buildString {
            appendLine("tunnel:")
            appendLine("  name: '$TUN'")
            appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
            appendLine("  multi-queue: true")
            appendLine("  ipv4: '$v4'")
            if (ipv6) appendLine("  ipv6: '$v6'")
            appendLine("  icmp: '$icmpMode'")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: '${AppConfig.LOOPBACK}'")
            appendLine("  udp: '$udpMode'")
            if (!udpAddress.isNullOrBlank()) {
                appendLine("  udp-address: '${udpAddress.trim()}'")
            }
            if (pipeline) {
                appendLine("  pipeline: true")
            }
            if (socksUsername != null && socksPassword != null) {
                appendLine("  username: ${socksUsername.toSingleQuotedYamlScalar()}")
                appendLine("  password: ${socksPassword.toSingleQuotedYamlScalar()}")
            }
            if (tcpFastOpen) {
                appendLine("  tcp-fastopen: true")
            }
            appendLine("misc:")
            appendLine("  log-level: $logLevel")
        }
    }

    private fun buildMangleMarking(
        cmd: String,
        appUid: Int,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        selectedUids: List<String>,
    ): String {
        val allowMode = perAppEnabled && !bypassApps
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        return buildString {
            appendLine("$cmd -t mangle -N $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $CHAIN")
            appendLine("$cmd -t mangle -A $CHAIN -m mark --mark $FWMARK -j RETURN")
            appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $appUid -j RETURN")
            if (bypassSelected) {
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j RETURN") }
            }
            appendLine("$cmd -t mangle -A $CHAIN -p udp --dport 53 -j MARK --set-xmark $MARK")
            appendLine("$cmd -t mangle -A $CHAIN -p tcp --dport 53 -j MARK --set-xmark $MARK")
            val cidrs = if (cmd == "ip6tables") bypassCidrsV6 else bypassCidrs
            cidrs.forEach { appendLine("$cmd -t mangle -A $CHAIN -d $it -j RETURN") }
            if (allowMode) {
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j MARK --set-xmark $MARK") }
            } else {
                appendLine("$cmd -t mangle -A $CHAIN -j MARK --set-xmark $MARK")
            }
            appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A OUTPUT -j $CHAIN")
        }
    }

    private fun buildV6Blackhole(
        appUid: Int,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        selectedUids: List<String>,
    ): String {
        val chain = AppConfig.ROOT_V6_CHAIN
        val allowMode = perAppEnabled && !bypassApps
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        val reject = "-j REJECT --reject-with icmp6-adm-prohibited"
        return buildString {
            appendLine("ip6tables -t filter -N $chain 2>/dev/null || true")
            appendLine("ip6tables -t filter -F $chain")
            appendLine("ip6tables -t filter -A $chain -m mark --mark $FWMARK -j RETURN")
            appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $appUid -j RETURN")
            appendLine("ip6tables -t filter -A $chain -o lo -j RETURN")
            bypassCidrsV6.forEach { appendLine("ip6tables -t filter -A $chain -d $it -j RETURN") }
            if (bypassSelected) {
                selectedUids.forEach { appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $it -j RETURN") }
            }
            if (allowMode) {
                selectedUids.forEach { appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $it $reject") }
            } else {
                appendLine("ip6tables -t filter -A $chain $reject")
            }
            appendLine("ip6tables -t filter -D OUTPUT -j $chain 2>/dev/null || true")
            appendLine("ip6tables -t filter -A OUTPUT -j $chain")
        }
    }

    private fun buildLanShareSetup(captureDeviceTraffic: Boolean, ipv6: Boolean): String {
        val fwd = AppConfig.ROOT_FWD_CHAIN
        val dnsChain = AppConfig.ROOT_DNS_CHAIN
        val v6fwd = AppConfig.ROOT_V6_FWD_CHAIN
        val v6pre = AppConfig.ROOT_V6_PRE_CHAIN
        val dns = SettingsManager.getRemoteDnsServers()
        .firstOrNull { Utils.isPureIpAddress(it) && !it.contains(":") }
        ?: AppConfig.ROOT_LAN_DNS
        val lanCidrs = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
        return buildString {
            appendLine("set +e")
            appendLine("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")
            appendLine("iptables -N $fwd 2>/dev/null || true")
            appendLine("iptables -F $fwd")
            appendLine("iptables -A $fwd -i $TUN -j ACCEPT")
            appendLine("iptables -A $fwd -o $TUN -j ACCEPT")
            appendLine("iptables -D FORWARD -j $fwd 2>/dev/null || true")
            appendLine("iptables -I FORWARD -j $fwd")
            appendLine("iptables -t mangle -D FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350 2>/dev/null || true")
            appendLine("iptables -t mangle -A FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350")
            appendLine("iptables -t nat -N $dnsChain 2>/dev/null || true")
            appendLine("iptables -t nat -F $dnsChain")
            lanCidrs.forEach {
                appendLine("iptables -t nat -A $dnsChain ! -i $TUN -d $it -p udp --dport 53 -j DNAT --to $dns")
            }
            appendLine("iptables -t nat -D PREROUTING -j $dnsChain 2>/dev/null || true")
            appendLine("iptables -t nat -A PREROUTING -j $dnsChain")
            appendLine("ip rule add iif lo goto 6000 pref 5000 2>/dev/null || true")
            appendLine("ip rule add iif $TUN lookup main suppress_prefixlength 0 pref 5010 2>/dev/null || true")
            appendLine("ip rule add iif $TUN goto 6000 pref 5020 2>/dev/null || true")
            appendLine("ip rule add to 10.0.0.0/8 lookup main pref 5025 2>/dev/null || true")
            appendLine("ip rule add to 172.16.0.0/12 lookup main pref 5026 2>/dev/null || true")
            appendLine("ip rule add to 192.168.0.0/16 lookup main pref 5027 2>/dev/null || true")
            appendLine("ip rule add from 10.0.0.0/8 lookup $TABLE pref 5030 2>/dev/null || true")
            appendLine("ip rule add from 172.16.0.0/12 lookup $TABLE pref 5040 2>/dev/null || true")
            appendLine("ip rule add from 192.168.0.0/16 lookup $TABLE pref 5050 2>/dev/null || true")
            appendLine("ip rule add nop pref 6000 2>/dev/null || true")

            appendLine("ip6tables -N $v6fwd 2>/dev/null || true")
            appendLine("ip6tables -F $v6fwd")
            appendLine("ip6tables -D FORWARD -j $v6fwd 2>/dev/null || true")
            appendLine("ip6tables -I FORWARD -j $v6fwd")
            if (ipv6) {
                if (!captureDeviceTraffic) {
                    appendLine("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $TUN 2>/dev/null || true")
                    appendLine("ip -6 route replace default dev $TUN table $TABLE 2>/dev/null || true")
                    appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                }
                appendLine("ip6tables -A $v6fwd -i $TUN -j ACCEPT")
                appendLine("ip6tables -A $v6fwd -o $TUN -j ACCEPT")
                appendLine("ip6tables -t mangle -N $v6pre 2>/dev/null || true")
                appendLine("ip6tables -t mangle -F $v6pre")
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -p udp --dport 53 -j MARK --set-xmark $MARK")
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -p tcp --dport 53 -j MARK --set-xmark $MARK")
                bypassCidrsV6.forEach { appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -d $it -j RETURN") }
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -j MARK --set-xmark $MARK")
                appendLine("ip6tables -t mangle -D PREROUTING -j $v6pre 2>/dev/null || true")
                appendLine("ip6tables -t mangle -A PREROUTING -j $v6pre")
                appendLine("ip6tables -A $v6fwd -j REJECT --reject-with icmp6-no-route")
            } else {
                appendLine("ip6tables -A $v6fwd -j REJECT --reject-with icmp6-no-route")
            }
        }
    }

    private fun writeHevConfig(file: File, config: String): Boolean {
        val atomicFile = AtomicFile(file)
        val output = try {
            atomicFile.startWrite()
        } catch (e: Exception) {
            LogUtil.e(TAG, "RootProxyManager: failed to open HEV config at ${file.absolutePath}", e)
            return false
        }

        return try {
            output.write(config.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            true
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            LogUtil.e(TAG, "RootProxyManager: failed to write HEV config at ${file.absolutePath}", e)
            false
        }
    }

    private fun buildTeardown(context: Context): String {
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val corePid = Process.myPid()
        return buildString {
            for (cmd in listOf("iptables", "ip6tables")) {
                appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $CHAIN 2>/dev/null || true")
            }
            appendLine("ip6tables -t filter -D OUTPUT -j ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -F ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -X ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip -6 rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip route flush table $TABLE 2>/dev/null || true")
            appendLine("ip -6 route flush table $TABLE 2>/dev/null || true")
            appendLine("iptables -D FORWARD -j ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -F ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -X ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t mangle -D FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350 2>/dev/null || true")
            appendLine("iptables -t nat -D PREROUTING -j ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t nat -F ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t nat -X ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -D FORWARD -j ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -F ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -X ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -D PREROUTING -j ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -F ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -X ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
            for (pref in listOf(5000, 5010, 5020, 5025, 5026, 5027, 5030, 5040, 5050, 6000)) {
                appendLine("ip rule del pref $pref 2>/dev/null || true")
            }
            appendLine("ip link set dev $TUN down 2>/dev/null || true")
            appendLine("[ -f '$pidFile' ] && kill \$(cat '$pidFile') 2>/dev/null || true")
            appendLine("rm -f '$pidFile'")
            appendLine("[ -f '$oomGuardPid' ] && kill \$(cat '$oomGuardPid') 2>/dev/null || true")
            appendLine("rm -f '$oomGuardPid'")
            appendLine("echo 0 > /proc/$corePid/oom_score_adj 2>/dev/null || true")
        }
    }
}

internal fun String.toSingleQuotedYamlScalar(): String = "'${replace("'", "''")}'"
