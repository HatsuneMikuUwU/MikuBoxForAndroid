package com.miku.ray.dto

import android.content.Context
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.CoreResolvedType

data class CoreConfigContext(
    val context: Context,
    val guid: String,
    val isCustom: Boolean = false,
    val resolvedOutbounds: List<ResolvedOutbound> = emptyList(),
    val routingDomainRules: List<RoutingDomainRule> = emptyList(),
) {
    data class ResolvedOutbound(
        val tag: String,
        val profile: ProfileItem,
        val resolvedProfiles: List<ProfileItem>,
        val resolvedType: CoreResolvedType,
        val resolvedGuids: List<String> = emptyList(),
    )

    data class RoutingDomainRule(
        val domain: List<String>,
        val outboundTag: String,
    )
}
