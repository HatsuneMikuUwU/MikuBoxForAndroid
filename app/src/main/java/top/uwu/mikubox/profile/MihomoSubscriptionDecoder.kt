package top.uwu.mikubox.profile

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Converts the common non-UI subscription formats used by UwU into Mihomo YAML. */
object MihomoSubscriptionDecoder {

    fun toMihomoConfig(source: String): String {
        val text = source.trim().removePrefix("\uFEFF")
        if (text.contains("proxies:") || text.contains("proxy-providers:") || text.contains("proxy-groups:")) {
            return text
        }

        val links = decodeBase64Subscription(text)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        check(links.isNotEmpty()) { "Subscription is empty" }
        val proxies = links.mapNotNull(::parseLink)
        check(proxies.isNotEmpty()) { "No Mihomo-compatible proxy links found" }
        return buildString {
            appendLine("mode: rule")
            appendLine("proxies:")
            proxies.forEach { append(it) }
            appendLine("rules:")
            appendLine("  - MATCH,${proxies.first().name.yaml()}")
        }
    }

    private fun decodeBase64Subscription(text: String): String = runCatching {
        val normalized = text.replace("\\s".toRegex(), "")
        Base64.decode(normalized, Base64.DEFAULT).toString(StandardCharsets.UTF_8)
    }.getOrDefault(text)

    private fun parseLink(link: String): ProxyYaml? = when {
        link.startsWith("ss://", true) -> shadowsocks(link)
        link.startsWith("ssr://", true) -> shadowsocksR(link)
        link.startsWith("vmess://", true) -> vmess(link)
        link.startsWith("vless://", true) -> vless(link)
        link.startsWith("trojan://", true) -> trojan(link)
        link.startsWith("hy2://", true) || link.startsWith("hysteria2://", true) -> hysteria2(link)
        link.startsWith("hysteria://", true) || link.startsWith("hy://", true) -> hysteria(link)
        link.startsWith("tuic://", true) -> tuic(link)
        link.startsWith("socks://", true) || link.startsWith("socks5://", true) -> socks(link)
        link.startsWith("http://", true) || link.startsWith("https://", true) -> http(link)
        link.startsWith("ssh://", true) -> ssh(link)
        else -> null
    }

    private fun shadowsocks(link: String): ProxyYaml? {
        val encodedOrUri = link.removePrefix("ss://")
        val fragment = encodedOrUri.substringAfter('#', "")
        val main = encodedOrUri.substringBefore('#').substringBefore('?')
        val decoded = if (main.contains('@')) main else base64(main) ?: return null
        val credentials = decoded.substringBeforeLast('@', "")
        val address = decoded.substringAfterLast('@', "")
        val method = credentials.substringBefore(':')
        val password = credentials.substringAfter(':', "")
        val host = address.substringBeforeLast(':', "")
        val port = address.substringAfterLast(':', "").toIntOrNull() ?: return null
        if (method.isBlank() || password.isBlank() || host.isBlank()) return null
        return ProxyYaml(name(fragment, host), "ss", listOf(
            "server" to host, "port" to port.toString(), "cipher" to method, "password" to password,
        ))
    }

    private fun shadowsocksR(link: String): ProxyYaml? {
        val decoded = base64(link.removePrefix("ssr://")) ?: return null
        val parts = decoded.substringBefore("/?").split(':')
        if (parts.size < 6) return null
        val params = Uri.parse("https://x/?${decoded.substringAfter("/?", "")}")
        return ProxyYaml(name(base64(params.getQueryParameter("remarks") ?: "") ?: "", parts[0]), "ssr", listOf(
            "server" to parts[0], "port" to parts[1], "cipher" to parts[3], "password" to (base64(parts[5]) ?: ""),
            "protocol" to parts[2], "obfs" to parts[4],
        ))
    }

    private fun vmess(link: String): ProxyYaml? {
        val objectJson = JSONObject(base64(link.removePrefix("vmess://")) ?: return null)
        val host = objectJson.optString("add")
        val port = objectJson.optString("port")
        val uuid = objectJson.optString("id")
        if (host.isBlank() || port.isBlank() || uuid.isBlank()) return null
        val fields = mutableListOf(
            "server" to host, "port" to port, "uuid" to uuid,
            "alterId" to objectJson.optString("aid", "0"),
            "cipher" to objectJson.optString("scy", "auto"),
            "udp" to "true",
        )
        val network = objectJson.optString("net")
        if (network.isNotBlank()) fields += "network" to network
        objectJson.optString("host").takeIf { it.isNotBlank() }?.let { fields += "servername" to it }
        objectJson.optString("path").takeIf { it.isNotBlank() }?.let { fields += "ws-opts.path" to it }
        if (objectJson.optString("tls").equals("tls", true)) fields += "tls" to "true"
        return ProxyYaml(name(objectJson.optString("ps"), host), "vmess", fields)
    }

    private fun vless(link: String): ProxyYaml? = standardV2ray(link, "vless")

    private fun trojan(link: String): ProxyYaml? {
        val uri = Uri.parse(link)
        val password = Uri.decode(uri.userInfo ?: return null)
        val host = uri.host ?: return null
        return ProxyYaml(name(uri.fragment, host), "trojan", listOf(
            "server" to host, "port" to (uri.port.takeIf { it > 0 } ?: 443).toString(), "password" to password,
            "sni" to (uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: ""),
            "skip-cert-verify" to (uri.getQueryParameter("allowInsecure") == "1").toString(),
        ).filter { it.second.isNotBlank() })
    }

    private fun standardV2ray(link: String, type: String): ProxyYaml? {
        val uri = Uri.parse(link)
        val uuid = Uri.decode(uri.userInfo ?: return null)
        val host = uri.host ?: return null
        val fields = mutableListOf(
            "server" to host, "port" to (uri.port.takeIf { it > 0 } ?: 443).toString(), "uuid" to uuid,
            "udp" to "true",
        )
        uri.getQueryParameter("encryption")?.let { fields += "cipher" to it }
        uri.getQueryParameter("security")?.takeIf { it != "none" }?.let { fields += "tls" to "true" }
        uri.getQueryParameter("sni")?.let { fields += "servername" to it }
        uri.getQueryParameter("type")?.let { fields += "network" to it }
        uri.getQueryParameter("path")?.let { fields += "ws-opts.path" to it }
        return ProxyYaml(name(uri.fragment, host), type, fields)
    }

    private fun hysteria(link: String): ProxyYaml? = hysteriaCommon(link, "hysteria")

    private fun hysteria2(link: String): ProxyYaml? = hysteriaCommon(link, "hysteria2")

    private fun hysteriaCommon(link: String, type: String): ProxyYaml? {
        val uri = Uri.parse(link)
        val host = uri.host ?: return null
        val auth = Uri.decode(uri.userInfo ?: uri.getQueryParameter("auth") ?: "")
        val authKey = if (type == "hysteria") "auth-str" else "password"
        return ProxyYaml(name(uri.fragment, host), type, listOf(
            "server" to host, "port" to (uri.port.takeIf { it > 0 } ?: 443).toString(),
            authKey to auth,
            "sni" to (uri.getQueryParameter("sni") ?: ""),
            "skip-cert-verify" to (uri.getQueryParameter("insecure") == "1").toString(),
        ).filter { it.second.isNotBlank() })
    }

    private fun tuic(link: String): ProxyYaml? {
        val uri = Uri.parse(link)
        val host = uri.host ?: return null
        val userInfo = Uri.decode(uri.userInfo ?: return null)
        val uuid = userInfo.substringBefore(':')
        val password = userInfo.substringAfter(':', "")
        return ProxyYaml(name(uri.fragment, host), "tuic", listOf(
            "server" to host, "port" to (uri.port.takeIf { it > 0 } ?: 443).toString(),
            "uuid" to uuid, "password" to password,
            "sni" to (uri.getQueryParameter("sni") ?: ""),
            "skip-cert-verify" to (uri.getQueryParameter("allow_insecure") == "1").toString(),
        ).filter { it.second.isNotBlank() })
    }

    private fun socks(link: String): ProxyYaml? = basicProxy(link, "socks5")
    private fun http(link: String): ProxyYaml? = basicProxy(link, "http")
    private fun ssh(link: String): ProxyYaml? = basicProxy(link, "ssh")

    private fun basicProxy(link: String, type: String): ProxyYaml? {
        val uri = Uri.parse(link)
        val host = uri.host ?: return null
        val userInfo = Uri.decode(uri.userInfo ?: "")
        return ProxyYaml(name(uri.fragment, host), type, listOf(
            "server" to host, "port" to (uri.port.takeIf { it > 0 } ?: if (type == "http") 80 else 443).toString(),
            "username" to userInfo.substringBefore(':', ""),
            "password" to userInfo.substringAfter(':', ""),
        ).filter { it.second.isNotBlank() })
    }

    private fun base64(value: String): String? = runCatching {
        val padded = value.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        Base64.decode(padded, Base64.DEFAULT).toString(StandardCharsets.UTF_8)
    }.getOrNull()

    private fun name(fragment: String?, fallback: String): String =
        fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }?.ifBlank { fallback } ?: fallback

    private data class ProxyYaml(val name: String, val type: String, val fields: List<Pair<String, String>>) {
        override fun toString(): String = buildString {
            appendLine("  - name: ${name.yaml()}")
            appendLine("    type: $type")
            fields.forEach { (key, value) ->
                if (key.contains('.')) {
                    // Nested transport properties are intentionally skipped here;
                    // full YAML imports retain every advanced transport option.
                    return@forEach
                }
                appendLine("    $key: ${value.yaml()}")
            }
        }
    }

    private fun String.yaml(): String = "'${replace("'", "''")}'"
}
