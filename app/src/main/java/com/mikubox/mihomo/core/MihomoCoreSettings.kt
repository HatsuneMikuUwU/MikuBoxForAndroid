package com.mikubox.mihomo.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-level overrides applied on top of the active profile at core start, plus a
 * few connection knobs. Config keys (log level, mode, allow-lan, tun stack) are
 * merged into the Mihomo config by the bridge; IPv6 and the latency-test options
 * are consumed directly by the Android layer.
 */
object MihomoCoreSettings {

    /** [FOLLOW] leaves the profile's own value untouched. */
    enum class ProxyMode(val value: String?) { FOLLOW(null), RULE("rule"), GLOBAL("global"), DIRECT("direct") }
    enum class LogLevel(val value: String) { SILENT("silent"), ERROR("error"), WARNING("warning"), INFO("info"), DEBUG("debug") }

    /**
     * [value] null means the profile's (or the core's default) stack is kept.
     * The app defaults to [GVISOR]: it is Mihomo's own default and the only
     * stack that stays entirely in user space, which Android's VPN file
     * descriptor cannot always support for the kernel-assisted alternatives.
     */
    enum class TunStack(val value: String?) {
        GVISOR("gvisor"), MIXED("mixed"), SYSTEM("system"), FOLLOW(null)
    }

    private const val PREFS = "mihomo_core_settings"
    private const val KEY_LOG = "log_level"
    private const val KEY_MODE = "mode"
    private const val KEY_ALLOW_LAN = "allow_lan"
    private const val KEY_TUN_STACK = "tun_stack"
    private const val KEY_IPV6 = "ipv6"
    private const val KEY_AUTOCONNECT = "autoconnect_on_start"
    private const val KEY_UNIFIED_DELAY = "unified_delay"
    private const val KEY_TCP_CONCURRENT = "tcp_concurrent"

    const val DEFAULT_TEST_URL = "https://cp.cloudflare.com"

    fun logLevel(context: Context): LogLevel = enumOr(prefs(context).getString(KEY_LOG, null), LogLevel.INFO)
    fun setLogLevel(context: Context, value: LogLevel) = putString(context, KEY_LOG, value.name)

    fun mode(context: Context): ProxyMode = enumOr(prefs(context).getString(KEY_MODE, null), ProxyMode.FOLLOW)
    fun setMode(context: Context, value: ProxyMode) = putString(context, KEY_MODE, value.name)

    fun allowLan(context: Context): Boolean = prefs(context).getBoolean(KEY_ALLOW_LAN, false)

    fun setAllowLan(context: Context, value: Boolean) = putBool(context, KEY_ALLOW_LAN, value)

    fun tunStack(context: Context): TunStack = enumOr(prefs(context).getString(KEY_TUN_STACK, null), TunStack.GVISOR)
    fun setTunStack(context: Context, value: TunStack) = putString(context, KEY_TUN_STACK, value.name)

    fun ipv6(context: Context): Boolean = prefs(context).getBoolean(KEY_IPV6, false)
    fun setIpv6(context: Context, value: Boolean) = putBool(context, KEY_IPV6, value)
    fun mixedPort(context: Context): Int = CoreOverrides.mixedPort(context).takeIf { it in 1..65535 } ?: 10808

    // A dynamic listener is an app feature, independent of the core's schema.
    // Freeze the chosen port for the session so all in-app clients use it too.
    @Volatile private var sessionMixedPort: Int? = null
    fun listeningPort(context: Context): Int = sessionMixedPort ?: mixedPort(context)
    fun prepareMixedPort(context: Context) {
        val dynamic = com.miku.ray.handler.MmkvManager.decodeSettingsBool(
            com.miku.ray.AppConfig.PREF_DYNAMIC_SOCKS_PORT, false,
        )
        sessionMixedPort = if (dynamic) java.net.ServerSocket(0).use { it.localPort } else mixedPort(context)
    }

    fun autoConnectOnStart(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTOCONNECT, false)
    fun setAutoConnectOnStart(context: Context, value: Boolean) = putBool(context, KEY_AUTOCONNECT, value)

    fun unifiedDelay(context: Context): Boolean = prefs(context).getBoolean(KEY_UNIFIED_DELAY, false)
    fun setUnifiedDelay(context: Context, value: Boolean) = putBool(context, KEY_UNIFIED_DELAY, value)

    fun tcpConcurrent(context: Context): Boolean = prefs(context).getBoolean(KEY_TCP_CONCURRENT, false)
    fun setTcpConcurrent(context: Context, value: Boolean) = putBool(context, KEY_TCP_CONCURRENT, value)

    /**
     * Config-key overrides merged into the profile by the bridge, as a JSON
     * object. [tunMtu] mirrors the MTU of the interface the VPN service created:
     * the core otherwise defaults its stack to 9000, which cannot survive the
     * Android tunnel and stalls large transfers.
     */
    fun overridesJson(context: Context, tunMtu: Int? = null, tunIpv4: String? = null, tunIpv6: String? = null, profileId: String? = com.mikubox.mihomo.profile.MihomoProfileStore.selected(context)?.id): String = JSONObject().apply {
        ScriptLibrary.source(context, profileId)?.let { put("miku-override-script", it) }
        put("miku-append-system-dns", CoreOverrides.extra(context, "dns.append-system") == "true")
        put("log-level", logLevel(context).value)
        put("allow-lan", allowLan(context))
        put("unified-delay", unifiedDelay(context))
        put("tcp-concurrent", tcpConcurrent(context))
        mode(context).value?.let { put("mode", it) }

        // "tun" and "dns" are merged into the profile's own sections by the
        // bridge, so the structured panels only touch what they list.
        val tun = CoreOverrides.tunJson(context)
        tunStack(context).value?.let { tun.put("stack", it) }
        tunMtu?.let { tun.put("mtu", it) }
        tunIpv4?.let { put("miku-tun-ipv4", it) }
        tunIpv6?.let { put("miku-tun-ipv6", it) }
        if (tun.length() > 0) put("tun", tun)

        val dns = DnsOverrides.json(context)
        if (dns.length() > 0) put("dns", dns)

        val sniffer = CoreOverrides.snifferJson(context)
        if (sniffer.length() > 0) put("sniffer", sniffer)

        put("mixed-port", listeningPort(context))
        put("ipv6", ipv6(context))

        // Core-wide extras (find-process-mode, geodata loader, TLS fingerprint,
        // keep-alive) are top-level keys.
        CoreOverrides.coreJson(context).also { core ->
            core.keys().forEach { key -> put(key, core.get(key)) }
        }

    }.toString()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)

    private fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).commit()
    }

    private fun putBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
