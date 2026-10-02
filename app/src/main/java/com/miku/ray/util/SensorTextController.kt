package com.miku.ray.util

import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.handler.MmkvManager

object SensorTextController {

    fun isEnabled(): Boolean =
    MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_SENSOR_TEXT, false)

    fun getAddress(profile: ProfileItem): String {
        return if (isEnabled()) {
            val server = profile.server ?: ""
            val port = profile.serverPort ?: ""
            if (server.isBlank() && port.isBlank()) "" else "$server : $port"
        } else {
            generateMaskedDescription(profile)
        }
    }

    private fun generateMaskedDescription(profile: ProfileItem): String {
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        val addrPart = server?.let {
            if (it.contains(":"))
            it.split(":").take(2).joinToString(":", postfix = ":***")
            else
            it.split('.').dropLast(1).joinToString(".", postfix = ".***")
        } ?: ""

        return "$addrPart : ${port ?: ""}"
    }
}
