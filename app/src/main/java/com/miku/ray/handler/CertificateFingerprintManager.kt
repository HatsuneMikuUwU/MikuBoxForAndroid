package com.miku.ray.handler

import com.miku.ray.AppConfig
import com.miku.ray.dto.CertSha256Request
import com.miku.ray.dto.CertSha256Result
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

object CertificateFingerprintManager {
    private const val TIMEOUT_MS = 5000L

    /**
     * Certificate pinning is an Xray feature: MikuRay asked its core to fetch the
     * server's certificate and hand back a SHA-256 for the field. The embedded
     * mihomo core verifies TLS itself and exposes no such call, so the field is
     * left for the user to fill rather than filled with a guess.
     */
    fun fetchForManualFill(profile: ProfileItem): String? {
        LogUtil.d(AppConfig.TAG, "certificate fetch is not available from the embedded core")
        return null
    }

    private fun buildRequest(profile: ProfileItem): CertSha256Request? {
        if (!isFetchable(profile)) return null

        val server = profile.server?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = profile.serverPort?.toIntOrNull()?.takeIf { it > 0 } ?: AppConfig.DEFAULT_PORT

        return CertSha256Request(
            address = resolveDialAddress(server),
            port = port,
            serverName = inferServerName(profile),
            timeoutMs = TIMEOUT_MS,
        )
    }

    private fun isFetchable(profile: ProfileItem): Boolean {
        return profile.configType == EConfigType.HYSTERIA2 || profile.security == AppConfig.TLS
    }

    private fun fetch(
        type: String,
        request: CertSha256Request,
        fetcher: (String) -> String,
    ): CertSha256Result? {
        return try {
            JsonUtil.fromJsonSafe(fetcher(JsonUtil.toJson(request)), CertSha256Result::class.java)
        } catch (e: UnsatisfiedLinkError) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 API missing in libv2ray", e)
            null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 failed", e)
            null
        }
    }

    private fun resolveDialAddress(server: String): String {
        if (Utils.isPureIpAddress(server) || !Utils.isDomainName(server)) return server

        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6, false)
        return HttpUtil.resolveHostToIP(server, preferIpv6)
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: server
    }

    private fun inferServerName(profile: ProfileItem): String? {
        val sni = profile.sni?.takeIf { it.isNotBlank() }
        return sni?.takeUnless { Utils.isPureIpAddress(it) }
    }
}
