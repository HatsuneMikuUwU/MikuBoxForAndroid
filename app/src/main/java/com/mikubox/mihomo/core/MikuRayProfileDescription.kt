package com.mikubox.mihomo.core

import com.miku.ray.enums.EConfigType

/**
 * What a profile's configuration says about itself, in the terms MikuRay's rows
 * and editors use.
 *
 * MikuBox profiles are whole configurations, and MikuRay's rows describe a single
 * server: the protocol badge, the address line and the transport chips all come
 * from per-server fields. A configuration that holds exactly one proxy *is* a
 * server, so it is described as one — everything else stays what it is, a custom
 * configuration.
 */
object MikuRayProfileDescription {

    /** One proxy entry, as far as a row needs it. */
    data class Node(
        val type: String,
        val server: String,
        val port: String,
        val network: String?,
        val security: String?,
        val insecure: Boolean,
    ) {
        /** The row's badge: MikuRay names protocols after its config types. */
        val configType: EConfigType
            get() = when (type.lowercase()) {
                "vless" -> EConfigType.VLESS
                "vmess" -> EConfigType.VMESS
                "trojan" -> EConfigType.TROJAN
                "ss", "shadowsocks" -> EConfigType.SHADOWSOCKS
                "socks", "socks5" -> EConfigType.SOCKS
                "http", "https" -> EConfigType.HTTP
                "hysteria2", "hy2" -> EConfigType.HYSTERIA2
                "hysteria" -> EConfigType.HYSTERIA
                "wireguard" -> EConfigType.WIREGUARD
                else -> EConfigType.CUSTOM
            }
    }

    /**
     * The single proxy [config] describes, or null when it carries none, several,
     * or one this app has no protocol for.
     */
    fun singleNode(config: String): Node? {
        val proxies = parseProxies(config)
        if (proxies.size != 1) return null
        val proxy = proxies.single()
        val type = proxy["type"].orEmpty()
        val server = proxy["server"].orEmpty()
        if (type.isBlank() || server.isBlank()) return null
        val node = Node(
            type = type,
            server = server,
            port = proxy["port"].orEmpty(),
            network = proxy["network"]?.takeIf { it.isNotBlank() && it != "tcp" },
            security = proxy["tls"]?.let { if (it.toBoolean()) "tls" else null },
            insecure = proxy["skip-cert-verify"].toBoolean(),
        )
        return node.takeIf { it.configType != EConfigType.CUSTOM || it.type.equals("custom", true) }
    }

    /**
     * Reads the `proxies:` block into one map per entry.
     *
     * A line scan is enough for the same reason it is enough for rules: these are
     * ordinary Clash configurations, where a proxy is a `- name: …` item with one
     * `key: value` per line, or a single `- {…}` flow mapping. Anything the scan
     * cannot make sense of yields no entry, which leaves the profile described as
     * the custom configuration it then is.
     */
    private fun parseProxies(config: String): List<Map<String, String>> {
        val lines = config.lineSequence().toList()
        val start = lines.indexOfFirst { it.trimStart().startsWith("proxies:") }
        if (start < 0) return emptyList()

        val entries = mutableListOf<Map<String, String>>()
        var current: MutableMap<String, String>? = null
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            val trimmed = line.trim()
            // A key at column zero ends the block.
            if (!line.first().isWhitespace()) break
            if (trimmed.startsWith("#")) continue

            if (trimmed.startsWith("- ")) {
                current?.let { entries += it }
                val rest = trimmed.removePrefix("- ").trim()
                current = if (rest.startsWith("{")) {
                    // Flow mapping: the whole entry on one line.
                    entries += flowEntry(rest)
                    null
                } else {
                    mutableMapOf<String, String>().also { it += keyValue(rest) }
                }
            } else {
                current?.let { it += keyValue(trimmed) }
            }
        }
        current?.let { entries += it }
        return entries.filter { it.isNotEmpty() }
    }

    private fun flowEntry(text: String): Map<String, String> {
        val body = text.removePrefix("{").removeSuffix("}")
        return body.split(',')
            .mapNotNull { part -> keyValue(part.trim()).entries.firstOrNull() }
            .associate { it.key to it.value }
    }

    private fun keyValue(text: String): Map<String, String> {
        val separator = text.indexOf(':')
        if (separator <= 0) return emptyMap()
        val key = text.substring(0, separator).trim()
        val value = text.substring(separator + 1).trim().trim('"', '\'')
        if (key.isEmpty()) return emptyMap()
        return mapOf(key to value)
    }
}
