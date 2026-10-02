package com.miku.ray.dto

data class ProfileDiffEntry(
    val subscriptionName: String,
    val profileName: String
)

data class SubscriptionUpdateResult(
    val configCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skipCount: Int = 0,
    val addedProfiles: List<ProfileDiffEntry> = emptyList(),
    val deletedProfiles: List<ProfileDiffEntry> = emptyList()
) {
    operator fun plus(other: SubscriptionUpdateResult): SubscriptionUpdateResult {
        return SubscriptionUpdateResult(
            configCount = this.configCount + other.configCount,
            successCount = this.successCount + other.successCount,
            failureCount = this.failureCount + other.failureCount,
            skipCount = this.skipCount + other.skipCount,
            addedProfiles = this.addedProfiles + other.addedProfiles,
            deletedProfiles = this.deletedProfiles + other.deletedProfiles
        )
    }
}
