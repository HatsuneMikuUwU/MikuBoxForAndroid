package com.miku.ray.ui.server

import android.content.Context
import com.miku.ray.handler.MmkvManager
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.fmt.Hysteria2Fmt
import com.miku.ray.fmt.ShadowsocksFmt
import com.miku.ray.fmt.SocksFmt
import com.miku.ray.fmt.TrojanFmt
import com.miku.ray.fmt.VlessFmt
import com.miku.ray.fmt.VmessFmt
import com.miku.ray.fmt.WireguardFmt

/**
 * Persists a per-protocol form (Server{Vmess,Vless,Trojan,Shadowsocks,Socks,
 * Wireguard,Hysteria2}Activity) into the profile store the app actually reads.
 *
 * The forms historically wrote only the legacy server store, so their cards had
 * to be routed to the generic editor to avoid losing the draft. This converter
 * closes that gap: the form's draft becomes a share link (the same encoders the
 * share sheet uses) and [com.miku.ray.MikuProfiles.Impl.saveShareLink] turns it
 * into a mihomo configuration in the app module — the decoder lives where the
 * core lives, this layer only speaks links.
 *
 * A null [com.miku.ray.MikuProfiles.impl] — the UI preview mode — keeps the
 * legacy store, which is what the preview host reads.
 */
object MikuProfileFormSaver {

    /**
     * Returns null on success, or a human-readable reason when the draft could
     * not be converted and stored.
     */
    fun save(context: Context, editGuid: String, config: ProfileItem): String? {
        val impl = com.miku.ray.MikuProfiles.impl
        if (impl == null) {
            MmkvManager.encodeServerConfig(editGuid, config)
            return null
        }

        val uri = when (config.configType) {
            EConfigType.VMESS -> VmessFmt.toUri(config)
            EConfigType.VLESS -> VlessFmt.toUri(config)
            EConfigType.TROJAN -> TrojanFmt.toUri(config)
            EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
            EConfigType.SOCKS -> SocksFmt.toUri(config)
            EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
            EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
            else -> return "Unsupported protocol: ${config.configType}"
        }
        val link = config.configType.protocolScheme + uri

        return runCatching {
            val name = config.remarks.ifBlank { config.description.orEmpty() }
            impl.saveShareLink(
                editGuid.takeIf { it.isNotBlank() },
                name,
                link,
                config.subscriptionId.takeIf { it.isNotBlank() },
            )
        }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }
}
