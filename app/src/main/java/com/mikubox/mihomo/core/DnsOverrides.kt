package com.mikubox.mihomo.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured overrides for the profile's `dns:` section, in the spirit of the
 * desktop clients' DNS panel: the fine-grained switches (fake-ip, resolvers,
 * filters) become editable one by one instead of only through raw YAML.
 *
 * Every option starts as [UNSET], meaning "leave whatever the profile says".
 * The bridge merges only the options the user actually touched into the
 * effective DNS block, so the defaults of an imported configuration survive.
 */
object DnsOverrides {

    /**
     * Tri-state for the switches that also have a profile-side value. The DNS
     * editor's raw YAML covers everything else.
     */
    const val UNSET = -1
    const val OFF = 0
    const val ON = 1

    enum class EnhancedMode(val value: String?) {
        FOLLOW(null), FAKE_IP("fake-ip"), REDIR_HOST("redir-host"), NORMAL("normal")
    }

    enum class CacheAlgorithm(val value: String?) { FOLLOW(null), ARC("arc"), LRU("lru") }

    private const val PREFS = "miku_dns_overrides"

    /** Plain resolvers used when respect-rules needs one and the user has none. */
    private const val DEFAULT_PROXY_NAMESERVER = "119.29.29.29, 1.1.1.1, 8.8.8.8, 9.9.9.9"

    private const val KEY_ENHANCED = "enhanced_mode"
    private const val KEY_FAKE_RANGE = "fake_ip_range"
    private const val KEY_FAKE_FILTER = "fake_ip_filter"
    private const val KEY_NAMESERVER = "nameserver"
    private const val KEY_DEFAULT_NS = "default_nameserver"
    private const val KEY_PROXY_NS = "proxy_server_nameserver"
    private const val KEY_DIRECT_NS = "direct_nameserver"
    private const val KEY_FALLBACK = "fallback"
    private const val KEY_FALLBACK_GEOIP = "fallback_geoip"
    private const val KEY_FALLBACK_CIDR = "fallback_ipcidr"
    private const val KEY_DIRECT_POLICY = "direct_follow_policy"
    private const val KEY_RESPECT_RULES = "respect_rules"
    private const val KEY_USE_HOSTS = "use_hosts"
    private const val KEY_USE_SYSTEM_HOSTS = "use_system_hosts"
    private const val KEY_PREFER_H3 = "prefer_h3"
    private const val KEY_IPV6 = "ipv6"
    private const val KEY_CACHE = "cache_algorithm"

    fun enhancedMode(context: Context): EnhancedMode =
        enumOr(prefs(context).getString(KEY_ENHANCED, null), EnhancedMode.FOLLOW)
    fun setEnhancedMode(context: Context, value: EnhancedMode) =
        edit(context) { putString(KEY_ENHANCED, value.name) }

    fun fakeIpRange(context: Context): String = text(context, KEY_FAKE_RANGE)
    fun setFakeIpRange(context: Context, value: String) = putText(context, KEY_FAKE_RANGE, value)

    /** One entry per line or comma separated, like the desktop clients' lists. */
    fun fakeIpFilter(context: Context): String = text(context, KEY_FAKE_FILTER)
    fun setFakeIpFilter(context: Context, value: String) = putText(context, KEY_FAKE_FILTER, value)

    fun nameserver(context: Context): String = text(context, KEY_NAMESERVER)
    fun setNameserver(context: Context, value: String) = putText(context, KEY_NAMESERVER, value)

    fun defaultNameserver(context: Context): String = text(context, KEY_DEFAULT_NS)
    fun setDefaultNameserver(context: Context, value: String) = putText(context, KEY_DEFAULT_NS, value)

    fun proxyServerNameserver(context: Context): String = text(context, KEY_PROXY_NS)
    fun setProxyServerNameserver(context: Context, value: String) = putText(context, KEY_PROXY_NS, value)

    fun directNameserver(context: Context): String = text(context, KEY_DIRECT_NS)
    fun setDirectNameserver(context: Context, value: String) = putText(context, KEY_DIRECT_NS, value)

    fun fallback(context: Context): String = text(context, KEY_FALLBACK)
    fun setFallback(context: Context, value: String) = putText(context, KEY_FALLBACK, value)

    fun fallbackIpCidr(context: Context): String = text(context, KEY_FALLBACK_CIDR)
    fun setFallbackIpCidr(context: Context, value: String) = putText(context, KEY_FALLBACK_CIDR, value)

    fun fallbackGeoIp(context: Context): Int = tri(context, KEY_FALLBACK_GEOIP)
    fun setFallbackGeoIp(context: Context, value: Int) = putTri(context, KEY_FALLBACK_GEOIP, value)

    fun directFollowPolicy(context: Context): Int = tri(context, KEY_DIRECT_POLICY)
    fun setDirectFollowPolicy(context: Context, value: Int) = putTri(context, KEY_DIRECT_POLICY, value)

    fun respectRules(context: Context): Int = tri(context, KEY_RESPECT_RULES)

    /**
     * "Respect rules" makes the core resolve node hostnames through the rules,
     * which needs a resolver that works outside the proxy path. Turning it on
     * without one makes mihomo refuse to start, so the bootstrap resolvers are
     * filled in the moment it is enabled.
     */
    fun setRespectRules(context: Context, value: Int) {
        putTri(context, KEY_RESPECT_RULES, value)
        if (value == ON && proxyServerNameserver(context).isBlank()) {
            setProxyServerNameserver(context, DEFAULT_PROXY_NAMESERVER)
        }
    }

    fun useHosts(context: Context): Int = tri(context, KEY_USE_HOSTS)
    fun setUseHosts(context: Context, value: Int) = putTri(context, KEY_USE_HOSTS, value)

    fun useSystemHosts(context: Context): Int = tri(context, KEY_USE_SYSTEM_HOSTS)
    fun setUseSystemHosts(context: Context, value: Int) = putTri(context, KEY_USE_SYSTEM_HOSTS, value)

    fun preferH3(context: Context): Int = tri(context, KEY_PREFER_H3)
    fun setPreferH3(context: Context, value: Int) = putTri(context, KEY_PREFER_H3, value)

    fun ipv6(context: Context): Int = tri(context, KEY_IPV6)
    fun setIpv6(context: Context, value: Int) = putTri(context, KEY_IPV6, value)

    fun cacheAlgorithm(context: Context): CacheAlgorithm =
        enumOr(prefs(context).getString(KEY_CACHE, null), CacheAlgorithm.FOLLOW)
    fun setCacheAlgorithm(context: Context, value: CacheAlgorithm) =
        edit(context) { putString(KEY_CACHE, value.name) }

    /**
     * The `dns:` object handed to the bridge. Only touched options are present,
     * so a profile's own values stay in charge of everything else.
     */
    fun json(context: Context): JSONObject = JSONObject().apply {
        CoreOverrides.extra(context, "dns.nameserver-policy").takeIf(String::isNotBlank)?.let { put("nameserver-policy", JSONObject(it)) }

        enhancedMode(context).value?.let { put("enhanced-mode", it) }
        cacheAlgorithm(context).value?.let { put("cache-algorithm", it) }
        fakeIpRange(context).takeIf { it.isNotBlank() }?.let { put("fake-ip-range", it) }
        fakeIpFilter(context).toList().takeIf { it.length() > 0 }?.let { put("fake-ip-filter", it) }
        nameserver(context).toList().takeIf { it.length() > 0 }?.let { put("nameserver", it) }
        defaultNameserver(context).toList().takeIf { it.length() > 0 }?.let { put("default-nameserver", it) }
        proxyServerNameserver(context).toList().takeIf { it.length() > 0 }?.let { put("proxy-server-nameserver", it) }
        directNameserver(context).toList().takeIf { it.length() > 0 }?.let { put("direct-nameserver", it) }
        fallback(context).toList().takeIf { it.length() > 0 }?.let { put("fallback", it) }

        val filter = JSONObject()
        when (fallbackGeoIp(context)) {
            ON -> filter.put("geoip", true)
            OFF -> filter.put("geoip", false)
        }
        fallbackIpCidr(context).toList().takeIf { it.length() > 0 }?.let { filter.put("ipcidr", it) }
        CoreOverrides.extra(context, "dns.fallback-filter.geoip-code").takeIf(String::isNotBlank)?.let { filter.put("geoip-code", it) }
        listOf("geosite", "domain").forEach { key ->
            CoreOverrides.extra(context, "dns.fallback-filter.$key").toList().takeIf { it.length() > 0 }?.let { filter.put(key, it) }
        }
        if (filter.length() > 0) put("fallback-filter", filter)

        putFlag(this, "respect-rules", respectRules(context))
        putFlag(this, "use-hosts", useHosts(context))
        putFlag(this, "use-system-hosts", useSystemHosts(context))
        putFlag(this, "prefer-h3", preferH3(context))
        putFlag(this, "ipv6", ipv6(context))
        putFlag(this, "direct-nameserver-follow-policy", directFollowPolicy(context))
    }

    /**
     * Writes a tri-state as a real boolean. Keeping this explicit avoids passing
     * boxed values through the JSON writer's overloaded puts.
     */
    private fun putFlag(target: JSONObject, key: String, state: Int) {
        if (state == ON) {
            target.put(key, true)
        } else if (state == OFF) {
            target.put(key, false)
        }
    }

    /** Entries of a list option, split on newlines and commas. */
    private fun String.toList(): JSONArray {
        val entries = split('\n', ',', ' ', '\t')
            .map(String::trim)
            .filter(String::isNotEmpty)
        return JSONArray().also { array -> entries.forEach(array::put) }
    }

    // region storage

    private fun tri(context: Context, key: String): Int =
        coerceTri(prefs(context).all[key])

    private fun putTri(context: Context, key: String, value: Int) = edit(context) {
        if (value == UNSET) remove(key) else putInt(key, value)
    }

    /**
     * Reads a stored tri-state without trusting its type. A value written by an
     * older build (a plain boolean, say) must not turn into a cast exception on
     * the way to the core: that would stop the connection from starting at all.
     */
    private fun coerceTri(stored: Any?): Int = when (stored) {
        is Int -> stored
        is Boolean -> if (stored) ON else OFF
        is String -> stored.toIntOrNull() ?: when (stored.lowercase()) {
            "true", "on" -> ON
            "false", "off" -> OFF
            else -> UNSET
        }
        else -> UNSET
    }

    private fun text(context: Context, key: String): String =
        prefs(context).all[key]?.toString().orEmpty()

    private fun putText(context: Context, key: String, value: String) = edit(context) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) remove(key) else putString(key, trimmed)
    }

    private inline fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(block).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // endregion
}
