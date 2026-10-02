package com.mikubox.mihomo.profile

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import com.mikubox.mihomo.R
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Converts the common non-UI subscription formats used by UwU into Mihomo YAML. */
object MihomoSubscriptionDecoder {

    private const val PROXY_GROUP = "PROXY"

    /**
     * Converts [source] into a Mihomo configuration.
     *
     * [validateYamlSyntax] is for the profile editor, whose input is expected to
     * be a YAML document: when the text fails even snakeyaml, the link fallback
     * below would only mask the real problem behind "No Mihomo-compatible proxy
     * links", so the parser's own message (with its line information) is raised
     * instead. Subscription import/refresh callers keep the default: a link
     * list is a legal payload there and must keep parsing as one.
     */
    fun toMihomoConfig(context: Context, source: String, validateYamlSyntax: Boolean = false): String {
        val text = source.trim().removePrefix("\uFEFF")
        if (isMihomoConfig(text)) return text

        // Some providers Base64-encode the whole Mihomo/Clash YAML, not a link list.
        val decoded = if (text.contains("://")) text else decodeBase64Subscription(text)
        if (isMihomoConfig(decoded)) return decoded

        if (validateYamlSyntax) {
            yamlParseFailure(text)?.let { message ->
                error(context.getString(com.miku.ray.R.string.mihomo_yaml_parse_failed, message))
            }
        }

        val links = decoded
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        check(links.isNotEmpty()) { context.getString(R.string.error_subscription_empty) }
        val proxies = links.mapNotNull(::parseLink).dedupeNames()
        check(proxies.isNotEmpty()) {
            context.getString(R.string.error_no_compatible_proxy_links)
        }
        return buildString {
            appendLine("mode: rule")
            appendLine("proxies:")
            proxies.forEach { append(it) }
            appendLine("proxy-groups:")
            appendLine("  - name: $PROXY_GROUP")
            appendLine("    type: select")
            appendLine("    proxies:")
            proxies.forEach { appendLine("      - ${it.name.yaml()}") }
            appendLine("rules:")
            appendLine("  - MATCH,$PROXY_GROUP")
        }
    }

    private fun isMihomoConfig(text: String): Boolean {
        val document = runCatching {
            org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(
                org.yaml.snakeyaml.LoaderOptions())).load<Any>(text)
        }.getOrNull() as? Map<*, *> ?: return false
        return document.keys.any { it in setOf("proxies", "proxy-providers", "proxy-groups",
            "rules", "rule-providers", "dns", "mixed-port", "mode") }
    }

    /**
     * Strict snakeyaml pass for the editor path: null when the text parses at
     * all (valid YAML that simply is not a Mihomo config keeps falling through
     * to link parsing), otherwise the parser's message. Line information comes
     * from snakeyaml itself and is authoritative for this check only.
     */
    private fun yamlParseFailure(text: String): String? = runCatching {
        org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(
            org.yaml.snakeyaml.LoaderOptions())).load<Any>(text)
        null
    }.getOrElse { e -> e.message?.takeIf(String::isNotBlank) ?: e.javaClass.simpleName }

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
        // Legacy form: the whole "method:password@host:port" is Base64. SIP002
        // form: only the "method:password" userinfo is Base64 (or plain but
        // percent-encoded), while the host stays readable.
        val decoded = if (main.contains('@')) {
            val plainUserInfo = runCatching { Uri.decode(main.substringBeforeLast('@')) }
                .getOrDefault(main.substringBeforeLast('@'))
            val userInfo = if (plainUserInfo.contains(':')) {
                plainUserInfo
            } else {
                base64(main.substringBeforeLast('@')) ?: plainUserInfo
            }
            "$userInfo@${main.substringAfterLast('@')}"
        } else {
            base64(main) ?: return null
        }
        val credentials = decoded.substringBeforeLast('@', "")
        val address = decoded.substringAfterLast('@', "")
        val method = credentials.substringBefore(':')
        val password = credentials.substringAfter(':', "")
        val host = address.substringBeforeLast(':', "")
        val port = address.substringAfterLast(':', "").toIntOrNull() ?: return null
        if (method.isBlank() || password.isBlank() || host.isBlank()) return null
        return ProxyYaml(name(decodeFragment(fragment), host), "ss", listOf(
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
        // One malformed line must not abort the whole import, so a payload that
        // is not JSON just skips the node.
        val objectJson = runCatching {
            JSONObject(base64(link.removePrefix("vmess://")) ?: return null)
        }.getOrNull() ?: return null
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
        val wsHost = objectJson.optString("host").takeIf { it.isNotBlank() }
        val path = objectJson.optString("path").takeIf { it.isNotBlank() }
        if (network.equals("ws", true)) {
            path?.let { fields += "ws-opts.path" to it }
            wsHost?.let { fields += "ws-opts.headers.Host" to it }
        }
        if (objectJson.optString("tls").equals("tls", true)) {
            fields += "tls" to "true"
            // SNI defaults to the disguise host when present.
            objectJson.optString("sni").takeIf { it.isNotBlank() }?.let { fields += "servername" to it }
                ?: wsHost?.let { fields += "servername" to it }
        }
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
        // xtls-rprx-vision flow (vless), TLS/Reality security.
        uri.getQueryParameter("flow")?.takeIf { it.isNotBlank() }?.let { fields += "flow" to it }
        val security = uri.getQueryParameter("security")
        if (security != null && security != "none") fields += "tls" to "true"
        uri.getQueryParameter("sni")?.takeIf { it.isNotBlank() }?.let { fields += "servername" to it }
        uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() }?.let { fields += "client-fingerprint" to it }
        if (security == "reality") {
            uri.getQueryParameter("pbk")?.takeIf { it.isNotBlank() }?.let { fields += "reality-opts.public-key" to it }
            uri.getQueryParameter("sid")?.takeIf { it.isNotBlank() }?.let { fields += "reality-opts.short-id" to it }
        }
        // Transport: ws needs its path + Host header; grpc needs the service name.
        val network = uri.getQueryParameter("type")?.takeIf { it.isNotBlank() } ?: "tcp"
        fields += "network" to network
        when (network.lowercase()) {
            "ws" -> {
                uri.getQueryParameter("path")?.takeIf { it.isNotBlank() }?.let { fields += "ws-opts.path" to it }
                uri.getQueryParameter("host")?.takeIf { it.isNotBlank() }?.let { fields += "ws-opts.headers.Host" to it }
            }
            "grpc" -> uri.getQueryParameter("serviceName")?.takeIf { it.isNotBlank() }
                ?.let { fields += "grpc-opts.grpc-service-name" to it }
        }
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

    /**
     * Fragment values from [Uri.getFragment] arrive already decoded — decoding
     * them a second time corrupts (or crashes on) literal '%' names. Raw link
     * substrings go through [decodeFragment] first at the call site.
     */
    private fun name(fragment: String?, fallback: String): String =
        fragment?.trim()?.ifBlank { null } ?: fallback

    private fun decodeFragment(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() }
            ?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrDefault(it) }

    /** Mihomo rejects duplicate proxy names, so repeated nodes get a suffix. */
    private fun List<ProxyYaml>.dedupeNames(): List<ProxyYaml> {
        val counts = HashMap<String, Int>()
        return map { proxy ->
            val seen = counts.merge(proxy.name, 1) { _, added -> added }
            if (seen == 1) proxy else proxy.copy(name = "${proxy.name} ${seen}")
        }
    }

    private data class ProxyYaml(val name: String, val type: String, val fields: List<Pair<String, String>>) {
        override fun toString(): String {
            // Dotted keys (e.g. "ws-opts.path", "ws-opts.headers.Host") become nested YAML.
            val root = LinkedHashMap<String, Any>()
            insert(root, listOf("type"), type)
            fields.forEach { (key, value) -> insert(root, key.split('.'), value) }
            return buildString {
                appendLine("  - name: ${name.yaml()}")
                emit(root, 2)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun insert(map: LinkedHashMap<String, Any>, path: List<String>, value: String) {
        if (path.size == 1) {
            map[path[0]] = value
            return
        }
        val child = map.getOrPut(path[0]) { LinkedHashMap<String, Any>() } as LinkedHashMap<String, Any>
        insert(child, path.drop(1), value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun StringBuilder.emit(map: Map<String, Any>, depth: Int) {
        val pad = "  ".repeat(depth)
        map.forEach { (key, value) ->
            if (value is String) {
                appendLine("$pad$key: ${scalar(value)}")
            } else {
                appendLine("$pad$key:")
                emit(value as Map<String, Any>, depth + 1)
            }
        }
    }

    /** Numbers and booleans must stay unquoted or Mihomo rejects the whole config. */
    private fun scalar(value: String): String =
        if (value == "true" || value == "false" || value.matches(Regex("-?\\d+"))) value else value.yaml()

    private fun String.yaml(): String = "'${replace("'", "''")}'"
}
