package com.miku.ray.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.miku.ray.AppConfig
import com.miku.ray.contracts.Tun2SocksControl
import com.miku.ray.extension.delay
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class TProxyService(
    private val context: Context,
    private val vpnInterface: ParcelFileDescriptor,
    private val isRunningProvider: () -> Boolean,
    private val restartCallback: () -> Unit
) : Tun2SocksControl {
    companion object {
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val STATS_LOG_INTERVAL_TICKS = 3

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyStopService(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyIsRunning(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        private external fun TProxyGetStats(): LongArray?

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null
    private var started = false

    override fun startTun2Socks() {

        val configContent = buildConfig()
        val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(configContent)
        }
        LogUtil.d(AppConfig.TAG, "HevSocks5Tunnel Config content:\n$configContent")

        try {
            val ok = TProxyStartService(configFile.absolutePath, vpnInterface.fd)
            started = ok
            LogUtil.i(AppConfig.TAG, "HevSocks5Tunnel TProxyStartService result: $ok")
        } catch (e: Exception) {
            started = false
            LogUtil.e(AppConfig.TAG, "HevSocks5Tunnel exception: ${e.message}")
        }

        startWatchdog()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            var tick = 0
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)

                if (!isRunningProvider()) continue

                val running = isTunnelRunning()
                if (!running) {
                    LogUtil.w(AppConfig.TAG, "HevSocks5Tunnel: engine not running, requesting restart")

                    restartCallback()
                    break
                }

                tick++
                if (tick >= STATS_LOG_INTERVAL_TICKS) {
                    tick = 0
                    getTunnelStats()?.let { stats ->
                        if (stats.size >= 4) {
                            LogUtil.d(
                                AppConfig.TAG,
                                "HevSocks5Tunnel stats: txPackets=${stats[0]} txBytes=${stats[1]} " +
                                "rxPackets=${stats[2]} rxBytes=${stats[3]}"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildConfig(): String {
        val socksPort = SettingsManager.getSocksPort()
        val socksUsername = SettingsManager.getSocksUsername()
        val socksPassword = SettingsManager.getSocksPassword()
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val escapedSocksUsername = socksUsername?.replace("'", "''")
        val escapedSocksPassword = socksPassword?.replace("'", "''")
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
            appendLine("  ipv4: ${vpnConfig.ipv4Client}")

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)) {
                appendLine("  ipv6: '${vpnConfig.ipv6Client}'")
            }

            val icmpMode = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_ICMP) ?: "off"
            appendLine("  icmp: '${icmpMode}'")

            appendLine("socks5:")
            appendLine("  port: ${socksPort}")
            appendLine("  address: ${AppConfig.LOOPBACK}")
            appendLine("  udp: '${MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_UDP_MODE) ?: "udp"}'")
            val udpAddress = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_UDP_ADDRESS)
            if (!udpAddress.isNullOrBlank()) {
                appendLine("  udp-address: '${udpAddress.trim()}'")
            }
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_HEV_TUNNEL_PIPELINE, false)) {
                appendLine("  pipeline: true")
            }
            if (escapedSocksUsername != null && escapedSocksPassword != null) {
                appendLine("  username: '${escapedSocksUsername}'")
                appendLine("  password: '${escapedSocksPassword}'")
            }
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_HEV_TUNNEL_TCP_FASTOPEN, true)) {
                appendLine("  tcp-fastopen: true")
            }

            val timeoutSetting = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT) ?: AppConfig.HEVTUN_RW_TIMEOUT
            val parts = timeoutSetting.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            val tcpTimeout = parts.getOrNull(0)?.toIntOrNull() ?: 300
            val udpTimeout = parts.getOrNull(1)?.toIntOrNull() ?: 60

            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: ${tcpTimeout * 1000}")
            appendLine("  udp-read-write-timeout: ${udpTimeout * 1000}")
            appendLine("  log-level: ${MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL)}")
        }
    }

    override fun stopTun2Socks() {
        watchdogJob?.cancel()
        watchdogJob = null

        try {
            LogUtil.i(AppConfig.TAG, "TProxyStopService...")
            TProxyStopService()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to stop hev-socks5-tunnel", e)
        } finally {
            started = false
        }
    }

    override fun isTunnelRunning(): Boolean {
        return try {
            TProxyIsRunning()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TProxyIsRunning exception: ${e.message}")
            false
        }
    }

    override fun getTunnelStats(): LongArray? {
        return try {
            TProxyGetStats()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TProxyGetStats exception: ${e.message}")
            null
        }
    }
}
