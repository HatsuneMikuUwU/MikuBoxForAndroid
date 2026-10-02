package com.miku.ray.fmt

import android.text.TextUtils
import com.miku.ray.AppConfig
import com.miku.ray.dto.VmessQRCode
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.NetworkType
import com.miku.ray.extension.idnHost
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.net.URI

object VmessFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        if (str.indexOf('?') > 0 && str.indexOf('&') > 0) {
            return parseVmessStd(str)
        }

        val config = ProfileItem.create(EConfigType.VMESS)

        var result = str.replace(EConfigType.VMESS.protocolScheme, "")
        result = Utils.decode(result)
        if (TextUtils.isEmpty(result)) {
            LogUtil.w(AppConfig.TAG, "Toast decoding failed")
            return null
        }
        val vmessQRCode = JsonUtil.fromJson(result, VmessQRCode::class.java) ?: return null
        if (TextUtils.isEmpty(vmessQRCode.add)
            || TextUtils.isEmpty(vmessQRCode.port)
            || TextUtils.isEmpty(vmessQRCode.id)
            || TextUtils.isEmpty(vmessQRCode.net)
        ) {
            LogUtil.w(AppConfig.TAG, "Toast incorrect protocol")
            return null
        }

        config.remarks = vmessQRCode.ps
        config.server = vmessQRCode.add
        config.serverPort = vmessQRCode.port
        config.password = vmessQRCode.id
        config.method =
        if (TextUtils.isEmpty(vmessQRCode.scy)) AppConfig.DEFAULT_SECURITY else vmessQRCode.scy

        config.network = vmessQRCode.net
        if (config.network.isNullOrEmpty()) {
            config.network = NetworkType.TCP.type
        }
        config.headerType = vmessQRCode.type
        config.host = vmessQRCode.host
        config.path = vmessQRCode.path

        when (NetworkType.fromString(config.network)) {
            NetworkType.KCP -> {
                config.seed = vmessQRCode.path
            }

            NetworkType.GRPC -> {
                config.mode = vmessQRCode.type
                config.serviceName = vmessQRCode.path
                config.authority = vmessQRCode.host
            }

            else -> {}
        }

        config.security = vmessQRCode.tls
        config.sni = vmessQRCode.sni
        config.fingerPrint = vmessQRCode.fp
        config.alpn = vmessQRCode.alpn
        config.insecure = when (vmessQRCode.insecure) {
            "1" -> true
            "0" -> false
            else -> false
        }
        config.verifyPeerCertByName = vmessQRCode.vcn
        config.pinnedCA256 = vmessQRCode.pcs

        return config
    }

    fun toUri(config: ProfileItem): String {
        val vmessQRCode = VmessQRCode()

        vmessQRCode.v = "2"
        vmessQRCode.ps = config.remarks
        vmessQRCode.add = config.server.orEmpty()
        vmessQRCode.port = config.serverPort.orEmpty()
        vmessQRCode.id = config.password.orEmpty()
        vmessQRCode.scy = config.method.orEmpty()
        vmessQRCode.aid = "0"

        vmessQRCode.net = config.network.orEmpty()
        vmessQRCode.type = config.headerType.orEmpty()
        when (NetworkType.fromString(config.network)) {
            NetworkType.KCP -> {
                vmessQRCode.path = config.seed.orEmpty()
            }

            NetworkType.GRPC -> {
                vmessQRCode.type = config.mode.orEmpty()
                vmessQRCode.path = config.serviceName.orEmpty()
                vmessQRCode.host = config.authority.orEmpty()
            }

            else -> {}
        }

        config.host?.nullIfBlank()?.let { vmessQRCode.host = it }
        config.path?.nullIfBlank()?.let { vmessQRCode.path = it }

        vmessQRCode.tls = config.security.orEmpty()
        vmessQRCode.sni = config.sni.orEmpty()
        vmessQRCode.fp = config.fingerPrint.orEmpty()
        vmessQRCode.alpn = config.alpn.orEmpty()
        vmessQRCode.insecure = when (config.insecure) {
            true -> "1"
            false -> "0"
            else -> ""
        }
        vmessQRCode.vcn = config.verifyPeerCertByName.orEmpty()
        vmessQRCode.pcs = config.pinnedCA256.orEmpty()

        val json = JsonUtil.toJson(vmessQRCode)
        return Utils.encode(json)
    }

    fun parseVmessStd(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.VMESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.method = AppConfig.DEFAULT_SECURITY

        getItemFormQuery(config, queryParam)

        return config
    }

}
