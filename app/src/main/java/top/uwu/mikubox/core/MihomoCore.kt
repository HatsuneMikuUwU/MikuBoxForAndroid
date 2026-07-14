package top.uwu.mikubox.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import top.uwu.mikubox.R
import java.io.File

/** JNI entry point for the bundled Mihomo Alpha core. */
object MihomoCore {

    const val NO_TUN = -1

    data class Traffic(
        val uploadPerSecond: Long,
        val downloadPerSecond: Long,
        val uploadTotal: Long,
        val downloadTotal: Long,
    )

    /**
     * A proxy or proxy-group exposed by the running core. Groups populate [all]
     * (their members) and [now] (the selected member); plain nodes leave them empty.
     */
    data class Proxy(
        val name: String,
        val type: String,
        val now: String?,
        val all: List<String>,
        val delay: Int,
        val udp: Boolean,
    ) {
        val isGroup: Boolean get() = all.isNotEmpty()
        val isSelector: Boolean get() = type.equals("Selector", ignoreCase = true)
    }

    init {
        System.loadLibrary("mihomo")
        System.loadLibrary("mikubox_core")
    }

    fun start(context: Context, config: String, tunFd: Int): Result<Unit> = runCatching {
        val home = File(context.filesDir, "mihomo").apply { mkdirs() }
        check(nativeStart(config, home.absolutePath, tunFd) == 0) {
            nativeLastError().ifBlank { context.getString(R.string.mihomo_start_failed) }
        }
    }

    fun stop() {
        nativeStop()
    }

    fun version(): String = nativeVersion()

    fun traffic(): Traffic = JSONObject(nativeTraffic()).let {
        Traffic(
            uploadPerSecond = it.optLong("upload"),
            downloadPerSecond = it.optLong("download"),
            uploadTotal = it.optLong("uploadTotal"),
            downloadTotal = it.optLong("downloadTotal"),
        )
    }

    /** Live proxies/groups from the running core, keyed by name. Empty when stopped. */
    fun proxies(): Map<String, Proxy> = runCatching {
        val root = JSONObject(nativeProxies()).optJSONObject("proxies") ?: return emptyMap()
        buildMap {
            root.keys().forEach { key ->
                val obj = root.getJSONObject(key)
                val all = obj.optJSONArray("all").toStringList()
                put(
                    key,
                    Proxy(
                        name = obj.optString("name", key),
                        type = obj.optString("type"),
                        now = obj.optString("now").ifBlank { null },
                        all = all,
                        delay = obj.optJSONArray("history").lastDelay(),
                        udp = obj.optBoolean("udp"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    /** Points a selector [group] at one of its members. Returns true on success. */
    fun selectProxy(group: String, name: String): Boolean = nativeSelectProxy(group, name) == 0

    /** URL-tests a proxy, returning its delay in ms, or -1 on failure/timeout. */
    fun delay(name: String, url: String = "https://cp.cloudflare.com", timeoutMs: Int = 5000): Int =
        runCatching { JSONObject(nativeProxyDelay(name, url, timeoutMs)).optInt("delay", -1) }
            .getOrDefault(-1)

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }

    private fun JSONArray?.lastDelay(): Int {
        if (this == null || length() == 0) return 0
        return optJSONObject(length() - 1)?.optInt("delay", 0) ?: 0
    }

    private external fun nativeStart(config: String, home: String, tunFd: Int): Int
    private external fun nativeStop()
    private external fun nativeLastError(): String
    private external fun nativeVersion(): String
    private external fun nativeTraffic(): String
    private external fun nativeProxies(): String
    private external fun nativeSelectProxy(group: String, name: String): Int
    private external fun nativeProxyDelay(name: String, url: String, timeoutMs: Int): String
}
