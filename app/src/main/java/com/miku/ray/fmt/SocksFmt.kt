package com.miku.ray.fmt

import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.idnHost
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.util.Utils
import java.net.URI

object SocksFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SOCKS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()

        if (uri.userInfo?.isEmpty() == false) {
            val result = if (uri.userInfo.contains(":")) {
                uri.userInfo.split(":", limit = 2)
            } else {
                Utils.decode(uri.userInfo).split(":", limit = 2)
            }
            if (result.count() == 2) {
                config.username = result.first()
                config.password = result.last()
            }
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val pw =
        if (config.username.isNotNullEmpty())
        "${config.username}:${config.password}"
        else
        ":"

        return toUri(config, Utils.encode(pw, true), null)
    }
}
