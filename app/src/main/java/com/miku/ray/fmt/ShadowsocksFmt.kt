package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.idnHost
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.net.URI

object ShadowsocksFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        return parseSip002(str) ?: parseLegacy(str)
    }

    fun parseSip002(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null
        if (uri.userInfo.isNullOrEmpty()) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()

        val result = if (uri.userInfo.contains(":")) {
            uri.userInfo.split(":", limit = 2)
        } else {
            Utils.decode(uri.userInfo).split(":", limit = 2)
        }
        if (result.count() == 2) {
            config.method = result.first()
            config.password = result.last()
        }

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)
            getItemFormQuery(config, queryParam)

            if (queryParam["plugin"]?.contains("obfs=http") == true) {
                val queryPairs = HashMap<String, String>()
                for (pair in queryParam["plugin"]?.split(";") ?: listOf()) {
                    val idx = pair.split("=", limit = 2)
                    if (idx.size == 2) {
                        queryPairs[idx.first()] = idx.last()
                    }
                }
                config.network = NetworkType.TCP.type
                config.headerType = "http"
                config.host = queryPairs["obfs-host"]
                config.path = queryPairs["path"]
            }
        }

        return config
    }

    fun parseLegacy(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)
        var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
        val indexSplit = result.indexOf("#")
        if (indexSplit > 0) {
            try {
                config.remarks =
                Utils.decodeURIComponent(result.substring(indexSplit + 1, result.length))
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode remarks in SS legacy URL", e)
            }

            result = result.substring(0, indexSplit)
        }

        val indexS = result.indexOf("@")
        result = if (indexS > 0) {
            Utils.decode(result.substring(0, indexS)) + result.substring(
                indexS,
                result.length
            )
        } else {
            Utils.decode(result)
        }

        val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
        val match = legacyPattern.matchEntire(result) ?: return null

        config.server = match.groupValues[3].removeSurrounding("[", "]")
        config.serverPort = match.groupValues[4]
        config.password = match.groupValues[2]
        config.method = match.groupValues[1].lowercase()

        return config
    }

    fun toUri(config: ProfileItem): String {
        val pw = "${config.method}:${config.password}"

        return toUri(config, Utils.encode(pw, true), null)
    }
}
