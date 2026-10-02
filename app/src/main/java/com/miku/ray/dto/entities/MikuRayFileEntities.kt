package com.miku.ray.dto.entities

data class MikuRayExportedProfile(
    val profile: ProfileItem,
    val raw: String? = null
)

data class MikuRayExportPayload(
    val formatVersion: Int = 1,
    val type: String,
    val name: String,
    val exportedTime: Long = System.currentTimeMillis(),
    val groupSettings: SubscriptionItem? = null,
    val profiles: List<MikuRayExportedProfile>
) {
    companion object {
        const val TYPE_GROUP = "group"
        const val TYPE_PROFILE = "profile"
    }
}
