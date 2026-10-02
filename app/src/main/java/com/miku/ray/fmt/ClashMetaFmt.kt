package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import org.yaml.snakeyaml.Yaml

/** Parses the node portion of Clash/ClashMeta subscriptions. Routing groups are intentionally ignored. */
object ClashMetaFmt {
    fun parse(text: String, subscriptionId: String): List<ProfileItem> {
        val document = runCatching { Yaml().load<Any>(text) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val proxies = document["proxies"] as? Iterable<*> ?: return emptyList()
        return proxies.mapNotNull { it as? Map<*, *> }
            .mapNotNull { parseProxy(it, subscriptionId) }
    }

    private fun parseProxy(proxy: Map<*, *>, subscriptionId: String): ProfileItem? {
        val name = proxy.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val server = proxy.string("server")?.takeIf { it.isNotBlank() } ?: return null
        val port = proxy.int("port")?.takeIf { it in 1..65535 }?.toString() ?: return null
        val type = proxy.string("type")?.lowercase() ?: return null
        val config = when (type) {
            "vmess" -> ProfileItem.create(EConfigType.VMESS).apply {
                password = proxy.string("uuid")
                method = proxy.string("cipher") ?: "auto"
                network = proxy.string("network") ?: "tcp"
                security = if (proxy.bool("tls")) AppConfig.TLS else "none"
                sni = proxy.string("servername") ?: proxy.string("server-name")
                host = proxy.string("ws-opts", "headers", "Host")
                path = proxy.string("ws-opts", "path")
                alpn = proxy.stringList("alpn")
                insecure = proxy.boolOrNull("skip-cert-verify")
            }
            "vless" -> ProfileItem.create(EConfigType.VLESS).apply {
                password = proxy.string("uuid")
                method = "none"
                network = proxy.string("network") ?: "tcp"
                security = when {
                    proxy.string("reality-opts", "public-key") != null -> AppConfig.REALITY
                    proxy.bool("tls") -> AppConfig.TLS
                    else -> "none"
                }
                sni = proxy.string("servername") ?: proxy.string("server-name")
                publicKey = proxy.string("reality-opts", "public-key")
                shortId = proxy.string("reality-opts", "short-id")
                host = proxy.string("ws-opts", "headers", "Host")
                path = proxy.string("ws-opts", "path")
                alpn = proxy.stringList("alpn")
                insecure = proxy.boolOrNull("skip-cert-verify")
            }
            "trojan" -> ProfileItem.create(EConfigType.TROJAN).apply {
                password = proxy.string("password")
                network = proxy.string("network") ?: "tcp"
                security = AppConfig.TLS
                sni = proxy.string("sni") ?: proxy.string("servername")
                host = proxy.string("ws-opts", "headers", "Host")
                path = proxy.string("ws-opts", "path")
                alpn = proxy.stringList("alpn")
                insecure = proxy.boolOrNull("skip-cert-verify")
            }
            "ss", "shadowsocks" -> ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
                method = proxy.string("cipher") ?: proxy.string("method")
                password = proxy.string("password")
                host = proxy.string("plugin-opts", "host")
                path = proxy.string("plugin-opts", "path")
                headerType = proxy.string("plugin")
            }
            "socks5", "socks" -> ProfileItem.create(EConfigType.SOCKS).apply {
                username = proxy.string("username")
                password = proxy.string("password")
            }
            "http" -> ProfileItem.create(EConfigType.HTTP).apply {
                username = proxy.string("username")
                password = proxy.string("password")
            }
            else -> return null
        }
        config.remarks = name
        config.subscriptionId = subscriptionId
        config.server = server
        config.serverPort = port
        return config
    }

    private fun Map<*, *>.string(key: String): String? = this[key]?.toString()?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.string(vararg keys: String): String? {
        var current: Any? = this
        for (key in keys) current = (current as? Map<*, *>)?.get(key)
        return current?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        else -> value?.toString()?.toIntOrNull()
    }

    private fun Map<*, *>.bool(key: String): Boolean = boolOrNull(key) == true

    private fun Map<*, *>.boolOrNull(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        else -> value?.toString()?.toBooleanStrictOrNull()
    }

    private fun Map<*, *>.stringList(key: String): String? = when (val value = this[key]) {
        is Iterable<*> -> value.mapNotNull { it?.toString() }.joinToString(",").takeIf { it.isNotBlank() }
        else -> value?.toString()?.takeIf { it.isNotBlank() }
    }
}
