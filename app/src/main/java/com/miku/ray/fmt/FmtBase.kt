package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.Utils
import java.net.URI

open class FmtBase {
    fun toUri(config: ProfileItem, userInfo: String?, dicQuery: HashMap<String, String>?): String {
        val query = if (dicQuery != null)
        "?" + dicQuery.toList().joinToString(
            separator = "&",
            transform = { it.first + "=" + Utils.encodeURIComponent(it.second) })
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.encodeURIComponent(userInfo ?: ""),
            Utils.getIpv6Address(HttpUtil.toIdnDomain(config.server.orEmpty())),
            config.serverPort
        )

        return "${url}${query}#${Utils.encodeURIComponent(config.remarks)}"
    }

    fun getQueryParam(uri: URI): Map<String, String> {
        return uri.rawQuery.split("&")
        .associate { it.split("=").let { (k, v) -> k to Utils.decodeURIComponent(v) } }
    }

    fun getItemFormQuery(config: ProfileItem, queryParam: Map<String, String>) {
        config.network = queryParam["type"] ?: NetworkType.TCP.type
        config.headerType = queryParam["headerType"]
        config.host = queryParam["host"]
        config.path = queryParam["path"]

        config.seed = queryParam["seed"]
        config.kcpMtu = queryParam["mtu"]?.toIntOrNull()
        config.kcpTti = queryParam["tti"]?.toIntOrNull()
        config.quicSecurity = queryParam["quicSecurity"]
        config.quicKey = queryParam["key"]
        config.mode = queryParam["mode"]
        config.serviceName = queryParam["serviceName"]
        config.authority = queryParam["authority"]
        config.xhttpMode = queryParam["mode"]
        config.xhttpExtra = queryParam["extra"]
        config.finalMask = queryParam["fm"]

        config.security = queryParam["security"]
        if (config.security != AppConfig.TLS && config.security != AppConfig.REALITY) {
            config.security = null
        }
        val allowInsecureKeys = arrayOf("insecure", "allowInsecure", "allow_insecure")
        config.insecure = when {
            allowInsecureKeys.any { queryParam[it] == "1" } -> true
            allowInsecureKeys.any { queryParam[it] == "0" } -> false
            else -> false
        }
        config.sni = queryParam["sni"]
        config.fingerPrint = queryParam["fp"]
        config.alpn = queryParam["alpn"]
        config.echConfigList = queryParam["ech"]
        config.verifyPeerCertByName = queryParam["vcn"]
        config.pinnedCA256 = queryParam["pcs"]
        config.publicKey = queryParam["pbk"]
        config.shortId = queryParam["sid"]
        config.spiderX = queryParam["spx"]
        config.mldsa65Verify = queryParam["pqv"]
        config.flow = queryParam["flow"]
    }

    fun getQueryDic(config: ProfileItem): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()
        dicQuery["security"] = config.security?.ifEmpty { "none" }.orEmpty()
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        config.echConfigList?.nullIfBlank()?.let { dicQuery["ech"] = it }
        config.verifyPeerCertByName?.nullIfBlank()?.let { dicQuery["vcn"] = it }
        config.pinnedCA256?.nullIfBlank()?.let { dicQuery["pcs"] = it }
        config.fingerPrint?.nullIfBlank()?.let { dicQuery["fp"] = it }
        config.publicKey?.nullIfBlank()?.let { dicQuery["pbk"] = it }
        config.shortId?.nullIfBlank()?.let { dicQuery["sid"] = it }
        config.spiderX?.nullIfBlank()?.let { dicQuery["spx"] = it }
        config.mldsa65Verify?.nullIfBlank()?.let { dicQuery["pqv"] = it }
        config.flow?.nullIfBlank()?.let { dicQuery["flow"] = it }
        config.finalMask?.nullIfBlank()?.let { dicQuery["fm"] = it }
        config.kcpMtu?.let { dicQuery["mtu"] = it.toString() }
        config.kcpTti?.let { dicQuery["tti"] = it.toString() }
        if (config.security == AppConfig.TLS) {
            val insecureFlag = if (config.insecure == true) "1" else "0"
            dicQuery["insecure"] = insecureFlag
            dicQuery["allowInsecure"] = insecureFlag
        }

        val networkType = NetworkType.fromString(config.network)
        dicQuery["type"] = networkType.type

        when (networkType) {
            NetworkType.TCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
            }

            NetworkType.KCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.seed?.nullIfBlank()?.let { dicQuery["seed"] = it }
            }

            NetworkType.WS, NetworkType.HTTP_UPGRADE -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

            NetworkType.XHTTP -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
                config.xhttpMode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.xhttpExtra?.nullIfBlank()?.let { dicQuery["extra"] = it }
            }

            NetworkType.HTTP, NetworkType.H2 -> {
                dicQuery["type"] = "http"
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

            NetworkType.GRPC -> {
                config.mode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.authority?.nullIfBlank()?.let { dicQuery["authority"] = it }
                config.serviceName?.nullIfBlank()?.let { dicQuery["serviceName"] = it }
            }

            else -> {}
        }

        return dicQuery
    }
}
