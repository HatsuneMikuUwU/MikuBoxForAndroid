package top.uwu.mikubox.core

import android.content.Context
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
    enum class LogLevel(val value: String) { SILENT("silent"), WARNING("warning"), INFO("info"), DEBUG("debug") }
    enum class TunStack(val value: String) { SYSTEM("system"), GVISOR("gvisor"), MIXED("mixed") }

    private const val PREFS = "mihomo_core_settings"
    private const val KEY_LOG = "log_level"
    private const val KEY_MODE = "mode"
    private const val KEY_ALLOW_LAN = "allow_lan"
    private const val KEY_TUN_STACK = "tun_stack"
    private const val KEY_IPV6 = "ipv6"
    private const val KEY_TEST_URL = "test_url"
    private const val KEY_TEST_TIMEOUT = "test_timeout"
    private const val KEY_AUTOCONNECT = "autoconnect_on_start"
    private const val KEY_UNIFIED_DELAY = "unified_delay"
    private const val KEY_TCP_CONCURRENT = "tcp_concurrent"

    const val DEFAULT_TEST_URL = "https://cp.cloudflare.com"
    const val DEFAULT_TEST_TIMEOUT = 5000

    fun logLevel(context: Context): LogLevel = enumOr(prefs(context).getString(KEY_LOG, null), LogLevel.INFO)
    fun setLogLevel(context: Context, value: LogLevel) = putString(context, KEY_LOG, value.name)

    fun mode(context: Context): ProxyMode = enumOr(prefs(context).getString(KEY_MODE, null), ProxyMode.FOLLOW)
    fun setMode(context: Context, value: ProxyMode) = putString(context, KEY_MODE, value.name)

    fun allowLan(context: Context): Boolean = prefs(context).getBoolean(KEY_ALLOW_LAN, false)
    fun setAllowLan(context: Context, value: Boolean) = putBool(context, KEY_ALLOW_LAN, value)

    fun tunStack(context: Context): TunStack = enumOr(prefs(context).getString(KEY_TUN_STACK, null), TunStack.SYSTEM)
    fun setTunStack(context: Context, value: TunStack) = putString(context, KEY_TUN_STACK, value.name)

    fun ipv6(context: Context): Boolean = prefs(context).getBoolean(KEY_IPV6, true)
    fun setIpv6(context: Context, value: Boolean) = putBool(context, KEY_IPV6, value)

    fun testUrl(context: Context): String =
        prefs(context).getString(KEY_TEST_URL, null)?.ifBlank { null } ?: DEFAULT_TEST_URL

    fun setTestUrl(context: Context, value: String) = putString(context, KEY_TEST_URL, value.trim())

    fun testTimeout(context: Context): Int =
        prefs(context).getInt(KEY_TEST_TIMEOUT, DEFAULT_TEST_TIMEOUT).coerceIn(1000, 30_000)

    fun setTestTimeout(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_TEST_TIMEOUT, value.coerceIn(1000, 30_000)).commit().let {}

    fun autoConnectOnStart(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTOCONNECT, false)
    fun setAutoConnectOnStart(context: Context, value: Boolean) = putBool(context, KEY_AUTOCONNECT, value)

    fun unifiedDelay(context: Context): Boolean = prefs(context).getBoolean(KEY_UNIFIED_DELAY, false)
    fun setUnifiedDelay(context: Context, value: Boolean) = putBool(context, KEY_UNIFIED_DELAY, value)

    fun tcpConcurrent(context: Context): Boolean = prefs(context).getBoolean(KEY_TCP_CONCURRENT, false)
    fun setTcpConcurrent(context: Context, value: Boolean) = putBool(context, KEY_TCP_CONCURRENT, value)

    /** Config-key overrides merged into the profile by the bridge, as a JSON object. */
    fun overridesJson(context: Context): String = JSONObject().apply {
        put("log-level", logLevel(context).value)
        put("allow-lan", allowLan(context))
        put("tun-stack", tunStack(context).value)
        put("unified-delay", unifiedDelay(context))
        put("tcp-concurrent", tcpConcurrent(context))
        mode(context).value?.let { put("mode", it) }
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
