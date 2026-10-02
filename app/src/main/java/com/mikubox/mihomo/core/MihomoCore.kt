package com.mikubox.mihomo.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.miku.ray.util.LogUtil
import com.mikubox.mihomo.R
import java.io.File

/** JNI entry point for the bundled Mihomo Alpha core. */
object MihomoCore {

    const val NO_TUN = -1
    internal const val DEFERRED_TUN = -2

    data class Traffic(
        val uploadPerSecond: Long,
        val downloadPerSecond: Long,
        val uploadTotal: Long,
        val downloadTotal: Long,
        /** Moved since this core was started, i.e. during the current connection. */
        val uploadSession: Long,
        val downloadSession: Long,
    )

    /** Traffic one node or proxy group carried during the current session. */
    data class ProxyTraffic(val upload: Long, val download: Long)

    /**
     * One live connection as the core reports it: what asked for what, the rule
     * that matched, the proxy chain that carried it and how much moved.
     */
    data class Connection(
        val id: String,
        val network: String,
        val source: String,
        val destination: String,
        val host: String,
        val rule: String,
        val rulePayload: String,
        val chains: List<String>,
        val upload: Long,
        val download: Long,
    ) {
        /** What the row shows as the target: the hostname when one is known. */
        val target: String get() = host.ifBlank { destination }

        /** The rule that decided this connection, as a readable label. */
        val matchedRule: String
            get() = when {
                rulePayload.isBlank() -> rule
                else -> "$rule($rulePayload)"
            }
    }

    /**
     * A proxy or proxy-group exposed by the running core. Groups populate [all]
     * (their members) and [now] (the selected member); plain nodes leave them empty.
     * Auto groups (url-test/fallback) expose [fixed] when a member is pinned.
     */
    data class Proxy(
        val name: String,
        val type: String,
        val now: String?,
        val all: List<String>,
        val delay: Int,
        val udp: Boolean,
        val fixed: String? = null,
        val hidden: Boolean = false,
    ) {
        val isGroup: Boolean get() = all.isNotEmpty()
        val isSelector: Boolean get() = type.equals("Selector", ignoreCase = true)

        /** Groups whose member can be chosen or pinned manually (not load-balance). */
        val isSelectableGroup: Boolean get() = type.equals("Selector", ignoreCase = true) ||
            type.equals("URLTest", ignoreCase = true) ||
            type.equals("Fallback", ignoreCase = true)

        /** Groups that pick their member automatically and support pinning one node. */
        val isAutoGroup: Boolean get() = type.equals("URLTest", ignoreCase = true) ||
            type.equals("Fallback", ignoreCase = true)

        /** The pinned member on auto groups, or null/blank when running automatically. */
        val pinnedNode: String? get() = fixed?.takeIf { it.isNotBlank() }
    }

    /** One parsed routing rule from the running configuration. */
    data class Rule(val type: String, val payload: String, val target: String)

    init {
        System.loadLibrary("mihomo")
        System.loadLibrary("mikubox_core")
    }

    fun start(
        context: Context,
        config: String,
        tunFd: Int,
        dnsOverride: String = "",
        overridesJson: String = "",
        homeName: String = "mihomo",
    ): Result<Unit> = runCatching {
        val home = File(context.filesDir, homeName).apply { mkdirs() }
        ensureGeodata(context, home)
        if (nativeStart(config, home.absolutePath, tunFd, dnsOverride, overridesJson) != 0) {
            val detail = nativeLastError().ifBlank { context.getString(R.string.mihomo_start_failed) }
            LogUtil.e(message = "core start failed: $detail")
            error(detail)
        }
    }

    /**
     * Restores the bundled GeoSite/GeoIP databases into the core home directory.
     * Full Clash configurations routinely route through GEOSITE/GEOIP rules, and
     * the core aborts startup when they reference data it cannot load. Only
     * missing files are written so core-managed updates survive.
     */
    private fun ensureGeodata(context: Context, home: File) {
        for (name in GEODATA_FILES) {
            val target = File(home, name)
            if (target.exists()) continue
            val partial = File(home, "$name.part")
            runCatching {
                context.assets.open(name).use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                if (!partial.renameTo(target)) partial.delete()
            }.onFailure { partial.delete() }
        }
    }

    fun stop() {
        nativeStop()
    }

    fun attachTun(fd: Int): Result<Unit> = runCatching {
        check(nativeAttachTun(fd) == 0) { nativeLastError() }
    }

    fun version(): String = nativeVersion()

    fun traffic(): Traffic = JSONObject(nativeTraffic()).let {
        Traffic(
            uploadPerSecond = it.optLong("upload"),
            downloadPerSecond = it.optLong("download"),
            uploadTotal = it.optLong("uploadTotal"),
            downloadTotal = it.optLong("downloadTotal"),
            uploadSession = it.optLong("uploadSession"),
            downloadSession = it.optLong("downloadSession"),
        )
    }

    /**
     * Traffic each node and proxy group carried during this session, keyed by
     * proxy name. A connection counts for every proxy it passed through, so a
     * group carries the sum of the nodes it handed traffic to. Empty when the
     * core is stopped.
     */
    fun trafficByProxy(): Map<String, ProxyTraffic> = runCatching {
        val root = JSONObject(nativeTrafficByProxy())
        buildMap {
            root.keys().forEach { key ->
                val entry = root.optJSONObject(key) ?: return@forEach
                put(
                    key,
                    ProxyTraffic(
                        upload = entry.optLong("upload"),
                        download = entry.optLong("download"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

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
                        fixed = obj.optString("fixed").ifBlank { null },
                        hidden = obj.optBoolean("hidden"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    /** Declared proxy-group order of the running config; empty when stopped. */
    fun groupOrder(): List<String> = runCatching {
        JSONArray(nativeGroupOrder()).let { array -> List(array.length()) { array.optString(it) } }
    }.getOrDefault(emptyList())

    /** Live routing rules of the running core. Empty when stopped. */
    fun rules(): List<Rule> = runCatching {
        val array = JSONArray(nativeRules())
        List(array.length()) { index ->
            array.getJSONObject(index).let {
                Rule(type = it.optString("type"), payload = it.optString("payload"), target = it.optString("target"))
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Points a selector [group] at one of its members; an empty [name] clears a
     * pinned node on auto groups. Returns true on success.
     */
    fun selectProxy(group: String, name: String): Boolean = nativeSelectProxy(group, name) == 0

    /**
     * The core's built-in selector whose members are every node and every proxy
     * group of the running configuration. Global mode routes all traffic here,
     * so picking one of its members chooses the exit for that mode.
     */
    fun globalGroup(): Proxy? = proxies()[GLOBAL_GROUP]

    /**
     * Switches the running core between "rule", "global" and "direct" without a
     * restart. Returns true when the core accepted the new mode. Safe to call
     * while the core is stopped: the next start applies the stored mode anyway.
     */
    fun setMode(mode: String): Boolean = nativeSetMode(mode) == 0

    fun proxyEndpoint(name: String): Pair<String, Int>? = runCatching {
        val json = JSONObject(nativeProxyEndpoint(name))
        if (json.optString("type").lowercase() in setOf("hysteria", "hysteria2", "tuic", "wireguard")) return null
        val address = json.optString("address")
        val separator = address.lastIndexOf(':')
        if (separator <= 0) return null
        val host = address.substring(0, separator).removeSurrounding("[", "]")
        val port = address.substring(separator + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        host to port
    }.getOrNull()

    /** URL-tests a proxy, returning its delay in ms, or -1 on failure/timeout. */
    fun delay(name: String, url: String = "https://cp.cloudflare.com", timeoutMs: Int = 5000): Int =
        runCatching { JSONObject(nativeProxyDelay(name, url, timeoutMs)).optInt("delay", -1) }
            .getOrDefault(-1)

    /**
     * Effective core configuration and build facts (stack, MTU, DNS mode,
     * gVisor availability, last error) as a JSON object, for log exports.
     */
    fun runtimeInfo(): String = runCatching { nativeRuntimeInfo() }.getOrDefault("{}")

    /**
     * The connections the core is carrying right now, newest first. Empty when
     * the core is stopped or when nothing is connected.
     */
    fun connections(): List<Connection> = try {
        val raw = nativeConnections()
        val root = JSONObject(raw)
        val array = root.optJSONArray("connections") ?: JSONArray()
        val parsed = List(array.length()) { index ->
            val entry = array.optJSONObject(index) ?: JSONObject()
            val metadata = entry.optJSONObject("metadata") ?: JSONObject()
            Connection(
                id = entry.optString("id"),
                network = metadata.optString("network"),
                source = hostPort(metadata.optString("sourceIP"), metadata.optString("sourcePort")),
                destination = hostPort(
                    metadata.optString("destinationIP"),
                    metadata.optString("destinationPort"),
                ),
                host = metadata.optString("host").ifBlank { metadata.optString("sniffHost") },
                rule = entry.optString("rule"),
                rulePayload = entry.optString("rulePayload"),
                chains = entry.optJSONArray("chains").toStringList(),
                upload = entry.optLong("upload"),
                download = entry.optLong("download"),
            )
        }
        parsed
    } catch (error: Throwable) {
        // A snapshot the app cannot read must not take the screen down, but it
        // has to be visible: swallowing this once hid a missing JNI symbol.
        LogUtil.w(message = "connection list unavailable", throwable = error)
        emptyList()
    }

    /** Drops one connection by id; the app offers this per row. */
    fun closeConnection(id: String): Boolean = nativeCloseConnection(id) == 0

    /** Drops every live connection, mirroring the controller's DELETE /connections. */
    fun closeConnections(): Boolean = nativeCloseConnections() == 0

    private fun hostPort(address: String, port: String): String =
        if (port.isBlank() || port == "0") address else "$address:$port"

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }

    private fun JSONArray?.lastDelay(): Int {
        if (this == null || length() == 0) return 0
        return optJSONObject(length() - 1)?.optInt("delay", 0) ?: 0
    }

    private val GEODATA_FILES = arrayOf("geosite.dat", "geoip.metadb")

    fun updateSystemDns(addresses: List<String>) = nativeUpdateSystemDns(org.json.JSONArray(addresses.map { if (it.contains(':')) "[$it]:53" else "$it:53" }).toString())
    private external fun nativeUpdateSystemDns(addresses: String)

    fun evaluateScript(config: String, source: String): JSONObject {
        val result = JSONObject(nativeEvaluateScript(config, source))
        if (result.has("error")) error(result.getString("error"))
        return result.getJSONObject("config")
    }
    private external fun nativeEvaluateScript(config: String, source: String): String

    private external fun nativeStart(config: String, home: String, tunFd: Int, dnsOverride: String, overridesJson: String): Int
    private external fun nativeStop()
    private external fun nativeAttachTun(fd: Int): Int
    private external fun nativeLastError(): String
    private external fun nativeVersion(): String
    private external fun nativeTraffic(): String
    private external fun nativeProxies(): String
    private external fun nativeSelectProxy(group: String, name: String): Int
    private external fun nativeProxyEndpoint(name: String): String
    private external fun nativeProxyDelay(name: String, url: String, timeoutMs: Int): String
    private external fun nativeGroupOrder(): String
    private external fun nativeRules(): String
    private external fun nativeRuntimeInfo(): String
    private external fun nativeSetMode(mode: String): Int
    private external fun nativeTrafficByProxy(): String
    private external fun nativeConnections(): String
    private external fun nativeCloseConnections(): Int
    private external fun nativeCloseConnection(id: String): Int

    /** Name of the built-in all-proxies selector described by [globalGroup]. */
    const val GLOBAL_GROUP = "GLOBAL"
}
