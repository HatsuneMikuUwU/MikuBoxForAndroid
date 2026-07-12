package io.nekohasekai.sagernet.fmt.wireguard

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

// Parse the user-supplied reserved value into the 3-byte array the WireGuard
// endpoint expects. Accepts either a comma/line separated list of three ints
// ("0, 0, 0" / "[0,0,0]") or a base64-encoded 3-byte string.
fun parseReserved(anyStr: String): List<Int>? {
    val trimmed = anyStr.trim()
    if (trimmed.isEmpty()) return null
    val list = trimmed.listByLineOrComma()
    if (list.size == 3) {
        val ints = list.map {
            it.replace("[", "").replace("]", "").replace(" ", "").toIntOrNull()
        }
        if (ints.all { it != null }) {
            return ints.map { it!! and 0xFF }
        }
    }
    return parseReservedBase64(trimmed)
}

private fun parseReservedBase64(anyStr: String): List<Int>? {
    return try {
        val ba = android.util.Base64.decode(anyStr, android.util.Base64.DEFAULT)
        if (ba.size == 3) ba.map { it.toInt() and 0xFF } else null
    } catch (_: Exception) {
        null
    }
}

fun buildSingBoxEndpointWireGuardBean(bean: WireGuardBean): SingBoxOptions.Endpoint_WireGuardOptions {
    return SingBoxOptions.Endpoint_WireGuardOptions().apply {
        type = "wireguard"
        address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        mtu = bean.mtu
        peers = listOf(SingBoxOptions.WireGuardPeer().apply {
            address = bean.serverAddress
            port = bean.serverPort
            public_key = bean.peerPublicKey
            pre_shared_key = bean.peerPreSharedKey
            allowed_ips = listOf("0.0.0.0/0", "::/0")
            if (bean.reserved.isNotBlank()) parseReserved(bean.reserved)?.let { reserved = it }
        })
    }
}
