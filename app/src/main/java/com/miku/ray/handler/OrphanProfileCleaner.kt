package com.miku.ray.handler

import com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID

internal data class StoredProfileReference(
    val guid: String,
    val subscriptionId: String?,
)

internal object OrphanProfileCleaner {

    fun findOrphans(
        profiles: Collection<StoredProfileReference>,
        indexedServersBySubscription: Map<String, Set<String>?>,
        selectedServer: String?,
    ): Set<String>? {
        if (profiles.isEmpty()) return emptySet()

        if (indexedServersBySubscription.isEmpty() ||
            indexedServersBySubscription.values.any { it == null }
        ) {
            return null
        }

        val indexedServers = indexedServersBySubscription.values
        .filterNotNull()
        .flatten()
        .toSet()

        return profiles.mapNotNullTo(linkedSetOf()) { profile ->
            if (profile.guid == selectedServer || profile.guid in indexedServers) {
                return@mapNotNullTo null
            }

            val subscriptionId = profile.subscriptionId ?: return@mapNotNullTo null
            val groupId = subscriptionId.ifEmpty { DEFAULT_SUBSCRIPTION_ID }
            if (!indexedServersBySubscription.containsKey(groupId)) {
                return@mapNotNullTo null
            }

            profile.guid
        }
    }
}
