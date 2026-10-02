package com.miku.ray.core

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.dto.CoreConfigContext
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.CoreResolvedType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

object CoreConfigContextBuilder {

    fun build(context: Context, guid: String): CoreConfigContext? {
        val config = MmkvManager.decodeServerConfig(guid) ?: return null

        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, config) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        val routingResolvedOutbounds = resolveRoutingOutbounds()
        val resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds
        val fallbackResolvedOutbounds = resolveFallbackOutbounds(resolvedOutbounds)
        val routingDomainRules = collectRoutingDomainRulesForDns()

        return CoreConfigContext(
            context = context,
            guid = guid,
            resolvedOutbounds = resolvedOutbounds + fallbackResolvedOutbounds,
            routingDomainRules = routingDomainRules,
        )
    }

    private fun resolveOutbound(tag: String, profile: ProfileItem): CoreConfigContext.ResolvedOutbound? {
        if (profile.configType == EConfigType.CUSTOM) {
            return null
        }

        var resolvedGuids: List<String> = emptyList()
        val (resolvedProfiles, resolvedType) = when (profile.configType) {
            EConfigType.POLICYGROUP -> {
                val members = resolvePolicyGroupMembers(profile)
                resolvedGuids = members?.map { it.first }.orEmpty()
                Pair(
                    members?.map { it.second } ?: listOf(profile),
                    CoreResolvedType.POLICYGROUP,
                )
            }

            EConfigType.PROXYCHAIN -> {
                val chainProfiles = resolveProxyChainProfiles(profile)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }

            else -> {
                val chainProfiles = resolveProxyChainProfilesFromGroup(profile)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }
        }

        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            profile = profile,
            resolvedProfiles = resolvedProfiles,
            resolvedType = resolvedType,
            resolvedGuids = resolvedGuids,
        )
    }

    private fun resolveRoutingOutbounds(): List<CoreConfigContext.ResolvedOutbound> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val resolvedOutbounds = mutableListOf<CoreConfigContext.ResolvedOutbound>()
        val processedTags = mutableSetOf<String>()

        try {
            rulesetItems
            .filter { it.enabled }
            .mapNotNull { it.outboundTag.takeIf { tag -> tag.isNotBlank() } }
            .filter { tag -> tag !in AppConfig.BUILTIN_OUTBOUND_TAGS }
            .distinct()
            .forEach { tag ->
                if (tag in processedTags) {
                    return@forEach
                }
                processedTags.add(tag)

                try {
                    val profile = SettingsManager.getServerViaRemarks(tag) ?: run {
                        LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                        return@forEach
                    }
                    val resolvedOutbound = resolveOutbound(tag, profile) ?: run {
                        LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                        return@forEach
                    }
                    if (resolvedOutbound.resolvedProfiles.isEmpty()) {
                        LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                        return@forEach
                    }
                    resolvedOutbounds.add(resolvedOutbound)
                    LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedProfiles.size}")
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    internal fun resolvePolicyGroupGuids(config: ProfileItem): List<String> =
    resolvePolicyGroupMembers(config)?.map { it.first }?.distinct().orEmpty()

    private fun resolvePolicyGroupMembers(config: ProfileItem): List<Pair<String, ProfileItem>>? {
        try {
            val serverList = MmkvManager.decodeAllServerList()
            return serverList
            .asSequence()
            .mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)?.let { profile -> guid to profile }
            }
            .filter { (_, profile) ->
                val subscriptionId = config.policyGroupSubscriptionId
                if (subscriptionId.isNullOrBlank()) {
                    true
                } else {
                    profile.subscriptionId == subscriptionId
                }
            }
            .filter { (_, profile) ->
                val filter = config.policyGroupFilter
                if (filter.isNullOrBlank()) {
                    true
                } else {
                    try {
                        Regex(filter).containsMatchIn(profile.remarks)
                    } catch (_: Exception) {
                        profile.remarks.contains(filter)
                    }
                }
            }
            .filter { (_, profile) -> profile.server.isNotNullEmpty() }
            .filter { (_, profile) ->
                Utils.isPureIpAddress(profile.server!!) || Utils.isValidUrl(profile.server!!)
            }
            .filter { (_, profile) -> !profile.configType.isComplexType() }
            .toList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve policy group profiles for '${config.remarks}'", e)
            return null
        }
    }

    private fun resolveProxyChainProfiles(config: ProfileItem): List<ProfileItem> {
        if (config.proxyChainProfiles.isNullOrBlank()) {
            return listOf(config)
        }

        try {
            return config.proxyChainProfiles.orEmpty().split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { remark -> SettingsManager.getServerViaRemarks(remark) }
            .filter { it.server.isNotNullEmpty() }
            .filter { Utils.isPureIpAddress(it.server!!) || Utils.isValidUrl(it.server!!) }
            .filter { !it.configType.isComplexType() }
            .toList()
            .reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    private fun resolveProxyChainProfilesFromGroup(config: ProfileItem): List<ProfileItem> {
        if (config.subscriptionId.isEmpty()) {
            return listOf(config)
        }

        try {
            val subItem = MmkvManager.decodeSubscription(config.subscriptionId) ?: return listOf(config)
            val resolved = mutableListOf<ProfileItem>()
            SettingsManager.getServerViaRemarks(subItem.nextProfile)?.let { resolved.add(it) }
            resolved.add(config)
            SettingsManager.getServerViaRemarks(subItem.prevProfile)?.let { resolved.add(it) }
            return resolved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    private fun collectRoutingDomainRulesForDns(): List<CoreConfigContext.RoutingDomainRule> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val result = mutableListOf<CoreConfigContext.RoutingDomainRule>()

        rulesetItems
        .asSequence()
        .filter { it.enabled }
        .filter { !it.domain.isNullOrEmpty() }
        .forEach { rule ->
            val normalizedOutboundTag = when (rule.outboundTag) {
                AppConfig.TAG_DIRECT -> AppConfig.TAG_DIRECT
                AppConfig.TAG_BLOCKED -> AppConfig.TAG_BLOCKED
                else -> AppConfig.TAG_PROXY
            }
            result.add(
                CoreConfigContext.RoutingDomainRule(
                    domain = rule.domain.orEmpty(),
                    outboundTag = normalizedOutboundTag
                )
            )
        }

        return result
    }

    private fun resolveFallbackOutbounds(resolvedOutbounds: List<CoreConfigContext.ResolvedOutbound>): List<CoreConfigContext.ResolvedOutbound> {
        return resolvedOutbounds
        .asSequence()
        .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
        .filter { BalancerStrategyType.from(it.profile.policyGroupType).supportsObservatory && it.profile.policyGroupTestOutbounds != false }
        .mapNotNull { it.profile.policyGroupFallbackTag }
        .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
        .distinct()
        .mapNotNull { tag ->
            SettingsManager.getServerViaRemarks(tag)
            ?.takeUnless { it.configType == EConfigType.CUSTOM || it.configType == EConfigType.POLICYGROUP }
            ?.let { resolveOutbound(tag, it) }
        }
        .toList()
    }
}
