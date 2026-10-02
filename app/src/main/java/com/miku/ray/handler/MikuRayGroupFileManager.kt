package com.miku.ray.handler

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.MikuRayExportPayload
import com.miku.ray.dto.entities.MikuRayExportedProfile
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.MikuRayFileCrypto
import com.miku.ray.util.Utils
import java.io.File

object MikuRayGroupFileManager {

    const val FILE_EXTENSION = ".mikuray"

    fun buildGroupExportPayload(subscriptionId: String, groupName: String): MikuRayExportPayload? {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) return null

        val profiles = guids.mapNotNull { buildExportedProfile(it) }
        if (profiles.isEmpty()) return null

        val groupSettings = MmkvManager.decodeSubscription(subId)?.copy(remarks = groupName)

        return MikuRayExportPayload(
            type = MikuRayExportPayload.TYPE_GROUP,
            name = groupName,
            groupSettings = groupSettings,
            profiles = profiles
        )
    }

    fun buildProfileExportPayload(guid: String): MikuRayExportPayload? {
        val exported = buildExportedProfile(guid) ?: return null
        return MikuRayExportPayload(
            type = MikuRayExportPayload.TYPE_PROFILE,
            name = exported.profile.remarks,
            profiles = listOf(exported)
        )
    }

    private fun buildExportedProfile(guid: String): MikuRayExportedProfile? {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val raw = if (profile.configType == EConfigType.CUSTOM) MmkvManager.decodeServerRaw(guid) else null
        return MikuRayExportedProfile(profile = profile, raw = raw)
    }

    fun encryptPayloadToFile(context: Context, payload: MikuRayExportPayload, password: String, fileNamePrefix: String): File {
        val json = JsonUtil.toJson(payload)
        val encrypted = MikuRayFileCrypto.encrypt(json, password)
        val safeName = sanitizeFileName(fileNamePrefix)
        val file = File(context.cacheDir, "$safeName$FILE_EXTENSION")
        file.writeBytes(encrypted)
        return file
    }

    fun decryptPayloadFromFile(bytes: ByteArray, password: String): MikuRayExportPayload {
        val json = MikuRayFileCrypto.decrypt(bytes, password)
        return JsonUtil.fromJsonSafe(json, MikuRayExportPayload::class.java)
        ?: throw MikuRayFileCrypto.MikuRayCryptoException("Malformed .mikuray payload")
    }

    fun importPayload(payload: MikuRayExportPayload, targetSubscriptionId: String): Int {
        val subId = if (payload.type == MikuRayExportPayload.TYPE_GROUP) {
            val newSubId = Utils.getUuid()

            val restoredSettings = (payload.groupSettings ?: SubscriptionItem())
            .copy(
                remarks = payload.name,
                addedTime = System.currentTimeMillis(),
                lastUpdated = -1
            )
            MmkvManager.encodeSubscription(newSubId, restoredSettings)
            newSubId
        } else {
            targetSubscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        }

        var count = 0
        payload.profiles.forEach { exported ->
            val profile = exported.profile.copy(subscriptionId = subId)
            val newGuid = MmkvManager.encodeServerConfig("", profile)
            if (profile.configType == EConfigType.CUSTOM && !exported.raw.isNullOrBlank()) {
                MmkvManager.encodeServerRaw(newGuid, exported.raw)
            }
            count++
        }
        return count
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().ifEmpty { "MikuRay" }
        return cleaned.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
    }
}
