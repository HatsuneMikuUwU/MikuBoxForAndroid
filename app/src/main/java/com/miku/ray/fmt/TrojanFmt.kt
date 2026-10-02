package com.miku.ray.fmt

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.idnHost
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.Utils
import java.net.URI

object TrojanFmt : FmtBase() {
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.TROJAN)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo

        if (uri.rawQuery.isNullOrEmpty()) {
            config.network = NetworkType.TCP.type
            config.security = AppConfig.TLS
            config.insecure = false
        } else {
            val queryParam = getQueryParam(uri)

            getItemFormQuery(config, queryParam)
            config.security = queryParam["security"] ?: AppConfig.TLS
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val dicQuery = getQueryDic(config)

        return toUri(config, config.password, dicQuery)
    }
}
