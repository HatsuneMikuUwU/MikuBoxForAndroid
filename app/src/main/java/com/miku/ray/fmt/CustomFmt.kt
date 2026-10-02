package com.miku.ray.fmt

import com.miku.ray.dto.V2rayConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.util.JsonUtil

object CustomFmt : FmtBase() {
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        val outbound = fullConfig?.getProxyOutbound()

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()
        config.network = outbound?.streamSettings?.network
        config.security = outbound?.streamSettings?.security
        config.customProtocol = outbound?.protocol

        return config
    }
}
