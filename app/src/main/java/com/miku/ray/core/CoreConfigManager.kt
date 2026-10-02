package com.miku.ray.core

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miku.ray.AppConfig
import com.miku.ray.dto.ConfigResult
import com.miku.ray.dto.CoreConfigContext
import com.miku.ray.dto.V2rayConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.CoreResolvedType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.FakeDnsIpPool
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.PackageUidResolver
import com.miku.ray.util.Utils

object CoreConfigManager {
    private var initConfigCache: String? = null
    private var initConfigCacheWithTun: String? = null

    fun getV2rayConfig(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid)
            ?: return ConfigResult(status = false, guid = guid, errorMessage = "Failed to build config context")
            if (configContext.isCustom) {
                return buildV2rayCustomConfig(configContext)
            }
            return toConfigResult(configContext, buildUnifiedConfig(configContext))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config", e)
            return ConfigResult(
                status = false,
                guid = guid,
                errorMessage = "Failed to get V2ray config: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun getV2rayConfig4Speedtest(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid)
            ?: return ConfigResult(status = false, guid = guid, errorMessage = "Failed to build config context")
            if (configContext.isCustom) {
                return buildV2rayCustomConfig(configContext)
            }
            val v2rayConfig = buildUnifiedConfig(configContext)
            val primaryResolvedOutbound = configContext.resolvedOutbounds.first()
            val isBalancer = primaryResolvedOutbound.resolvedType == CoreResolvedType.POLICYGROUP
            val targetTag = if (isBalancer) AppConfig.TAG_BALANCER else AppConfig.TAG_PROXY
            postProcessForSpeedtest(v2rayConfig, targetTag, isBalancer)

            return toConfigResult(configContext, v2rayConfig)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config for speedtest", e)
            return ConfigResult(
                status = false,
                guid = guid,
                errorMessage = "Failed to get V2ray config for speedtest: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun buildV2rayCustomConfig(configContext: CoreConfigContext): ConfigResult {
        val context = configContext.context
        val raw = MmkvManager.decodeServerRaw(configContext.guid)
        ?: return ConfigResult(status = false, guid = configContext.guid, errorMessage = "Custom config is empty")
        val result = ConfigResult(true, configContext.guid, raw)

        val json = JsonUtil.parseString(raw)?.takeIf { it.isJsonObject }?.asJsonObject ?: return result

        val speedEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true
        val trafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
        val statisticsEnabled = speedEnabled || trafficEnabled
        if (statisticsEnabled) {
            if (!json.has("stats")) {
                json.add("stats", JsonObject())
            }
            val policyObj = json.get("policy")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject().also { json.add("policy", it) }
            val systemObj = policyObj.get("system")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject().also { policyObj.add("system", it) }
            if (!systemObj.has("statsOutboundUplink")) {
                systemObj.addProperty("statsOutboundUplink", true)
            }
            if (!systemObj.has("statsOutboundDownlink")) {
                systemObj.addProperty("statsOutboundDownlink", true)
            }
        } else {
            json.remove("stats")

            json.get("policy")?.takeIf { it.isJsonObject }?.asJsonObject?.let { policy ->
                policy.remove("system")
                if (policy.entrySet().isEmpty()) {
                    json.remove("policy")
                }
            }
        }

        if (!needTun()) {
            return JsonUtil.toJsonPretty(json)?.let { ConfigResult(true, configContext.guid, it) } ?: result
        }

        if (SettingsManager.canUseProcessRouting()) {
            val rulesJson = json.get("routing")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray()

            for (elem in rulesJson) {
                val rule = elem.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val process = rule.get("process")?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                val packages = process.mapNotNull {
                    it.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }.takeIf { it.isNotEmpty() } ?: continue
                val uids = PackageUidResolver.packageNamesToUids(context, packages).takeIf { it.isNotEmpty() } ?: continue

                rule.add("process", JsonArray().apply { uids.forEach { add(it) } })
            }
        }

        val inboundsJson = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
        ?: JsonArray().also { json.add("inbounds", it) }
        val tunNotExists = inboundsJson.none { elem ->
            elem.isJsonObject && elem.asJsonObject.get("protocol")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString == "tun"
        }

        if (tunNotExists) {
            val templateConfig = initV2rayConfig(configContext)
            templateConfig.inbounds.firstOrNull { it.tag == "tun" }?.let { inboundTun ->
                inboundTun.settings?.mtu = SettingsManager.getVpnMtu()
                inboundsJson.add(JsonUtil.parseString(JsonUtil.toJson(inboundTun)))
            }
        }

        return JsonUtil.toJsonPretty(json)?.let { ConfigResult(true, configContext.guid, it) } ?: result
    }

    private fun buildUnifiedConfig(configContext: CoreConfigContext): V2rayConfig {
        require(configContext.resolvedOutbounds.isNotEmpty()) { "resolvedOutbounds must not be empty for a non-CUSTOM context" }
        val primaryResolvedOutbound = configContext.resolvedOutbounds.first()

        val v2rayConfig = initV2rayConfig(configContext)
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = primaryResolvedOutbound.profile.remarks

        configureInbounds(v2rayConfig)

        if (v2rayConfig.outbounds.isNotEmpty()) {
            v2rayConfig.outbounds.removeAt(0)
        }
        val existingTags = v2rayConfig.outbounds.mapTo(mutableSetOf()) { it.tag }
        val policyGroupBalancerTags = mutableMapOf<String, String>()
        val balancerStrategies = mutableListOf<BalancerStrategy>()

        PolicyGroupTrafficRegistry.clear()
        configContext.resolvedOutbounds.forEachIndexed { index, spec ->
            buildOutbounds(
                resolvedOutbound = spec,
                prepend = index == 0,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
                policyGroupBalancerTags = policyGroupBalancerTags,
                balancerStrategies = balancerStrategies,
            )
        }

        configureRouting(configContext, v2rayConfig, policyGroupBalancerTags)
        configureFakeDns(v2rayConfig)
        configureDns(configContext, v2rayConfig, policyGroupBalancerTags)
        configureLocalDns(configContext, v2rayConfig)
        configureRootModeDns(v2rayConfig)

        if (primaryResolvedOutbound.resolvedType == CoreResolvedType.POLICYGROUP) {
            if (v2rayConfig.routing.domainStrategy == "IPIfNonMatch") {
                v2rayConfig.routing.rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        ip = arrayListOf("0.0.0.0/0", "::/0"),
                        balancerTag = AppConfig.TAG_BALANCER,
                    )
                )
            } else {
                v2rayConfig.routing.rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        network = "tcp,udp",
                        balancerTag = AppConfig.TAG_BALANCER,
                    )
                )
            }
        }

        applyObservability(v2rayConfig, balancerStrategies)
        applySpeedDisabled(v2rayConfig)
        resolveOutboundDomainsToHosts(v2rayConfig)
        applyTcpKeepAlive(v2rayConfig)

        return v2rayConfig
    }

    private fun buildOutbounds(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: MutableMap<String, String>,
        balancerStrategies: MutableList<BalancerStrategy>,
    ) {
        if (resolvedOutbound.tag in existingTags) {
            LogUtil.w(AppConfig.TAG, "Resolved outbound tag '${resolvedOutbound.tag}' already exists, skipping duplicated entry")
            return
        }

        when (resolvedOutbound.resolvedType) {
            CoreResolvedType.NORMAL -> handleNormalResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
            )

            CoreResolvedType.PROXYCHAIN -> handleProxyChainResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
            )

            CoreResolvedType.POLICYGROUP -> handlePolicyGroupResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
                policyGroupBalancerTags = policyGroupBalancerTags,
                balancerStrategies = balancerStrategies,
            )
        }
    }

    private fun handleNormalResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
    ) {
        val profile = resolvedOutbound.resolvedProfiles.firstOrNull() ?: run {
            LogUtil.w(AppConfig.TAG, "NORMAL resolved outbound '${resolvedOutbound.tag}' has empty resolvedProfiles, skipping")
            return
        }
        val outbound = convertProfile2Outbound(profile) ?: run {
            LogUtil.w(AppConfig.TAG, "Could not convert NORMAL resolved outbound '${resolvedOutbound.tag}' profile to outbound, skipping")
            return
        }
        outbound.tag = resolvedOutbound.tag
        if (prepend) {
            v2rayConfig.outbounds.add(0, outbound)
        } else {
            v2rayConfig.outbounds.add(outbound)
        }
        existingTags.add(resolvedOutbound.tag)
    }

    private fun handleProxyChainResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
    ) {
        val chainOutbounds = resolvedOutbound.resolvedProfiles
        .mapNotNull { convertProfile2Outbound(it) }
        .toMutableList()
        if (chainOutbounds.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "PROXYCHAIN resolved outbound '${resolvedOutbound.tag}' has no valid profiles, skipping")
            return
        }
        if (chainOutbounds.size == 1) {
            val outbound = chainOutbounds.first()
            outbound.tag = resolvedOutbound.tag
            if (prepend) {
                v2rayConfig.outbounds.add(0, outbound)
            } else {
                v2rayConfig.outbounds.add(outbound)
            }
            existingTags.add(resolvedOutbound.tag)
            return
        }

        val chainTags = chainOutbounds.mapIndexed { index, _ ->
            if (index == 0) {
                resolvedOutbound.tag
            } else {
                "${AppConfig.TAG_PROXY}-${resolvedOutbound.tag}-$index"
            }
        }
        if (chainTags.any { it in existingTags }) {
            LogUtil.w(
                AppConfig.TAG,
                "PROXYCHAIN resolved outbound '${resolvedOutbound.tag}' has colliding hop tags, skipping"
            )
            return
        }

        chainOutbounds.forEachIndexed { index, outbound ->
            outbound.tag = chainTags[index]
        }
        for (i in 0 until chainOutbounds.size - 1) {
            chainOutbounds[i].ensureSockopt().dialerProxy = chainOutbounds[i + 1].tag
        }

        if (prepend) {
            v2rayConfig.outbounds.addAll(0, chainOutbounds)
        } else {
            v2rayConfig.outbounds.addAll(chainOutbounds)
        }
        chainOutbounds.forEach { existingTags.add(it.tag) }
    }

    private fun handlePolicyGroupResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: MutableMap<String, String>,
        balancerStrategies: MutableList<BalancerStrategy>,
    ) {
        val memberTriples = resolvedOutbound.resolvedProfiles
        .zip(
            resolvedOutbound.resolvedGuids.ifEmpty {
                List(resolvedOutbound.resolvedProfiles.size) { "" }
            }
        )
        .mapNotNull { (profile, guid) ->
            convertProfile2Outbound(profile)?.let { ob -> Triple(ob, profile, guid) }
        }
        if (memberTriples.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "POLICYGROUP resolved outbound '${resolvedOutbound.tag}' has no valid member outbounds, skipping")
            return
        }

        val memberTagPrefix = "${AppConfig.TAG_PROXY}-${resolvedOutbound.tag}-"
        val membersToAdd = mutableListOf<V2rayConfig.OutboundBean>()
        memberTriples.forEachIndexed { index, (outbound, profile, guid) ->
            val memberTag = "$memberTagPrefix${index + 1}-${profile.remarks.trim()}"
            if (memberTag in existingTags) {
                return@forEachIndexed
            }
            outbound.tag = memberTag
            membersToAdd.add(outbound)
            existingTags.add(memberTag)
            PolicyGroupTrafficRegistry.register(memberTag, guid)
        }

        if (membersToAdd.isEmpty()) {
            LogUtil.w(
                AppConfig.TAG,
                "POLICYGROUP resolved outbound '${resolvedOutbound.tag}' produced no unique member tags, skipping"
            )
            return
        }

        if (prepend) {
            v2rayConfig.outbounds.addAll(0, membersToAdd)
        } else {
            v2rayConfig.outbounds.addAll(membersToAdd)
        }

        val balancerTag = if (resolvedOutbound.tag == AppConfig.TAG_PROXY) {
            AppConfig.TAG_BALANCER
        } else {
            "${AppConfig.TAG_BALANCER_PRE}-${resolvedOutbound.tag}"
        }
        val strategyType = BalancerStrategyType.from(resolvedOutbound.profile.policyGroupType)
        val fallbackTag = if (strategyType.supportsObservatory && resolvedOutbound.profile.policyGroupTestOutbounds != false) {
            resolvedOutbound.profile.policyGroupFallbackTag
            ?.takeIf { it.isNotEmpty() && it != AppConfig.TAG_PROXY }
            ?: membersToAdd.first().tag
        } else null
        val strategy = buildBalancerStrategy(
            strategyType = strategyType,
            selector = listOf(memberTagPrefix),
            balancerTag = balancerTag,
            fallbackTag = fallbackTag,
            profile = resolvedOutbound.profile,
        )
        val existingBalancers = v2rayConfig.routing.balancers?.toMutableList() ?: mutableListOf()
        if (existingBalancers.none { it.tag == balancerTag }) {
            existingBalancers.add(strategy.balancer)
            v2rayConfig.routing.balancers = existingBalancers
        }
        balancerStrategies.add(strategy)
        policyGroupBalancerTags[resolvedOutbound.tag] = balancerTag
    }

    private fun postProcessForSpeedtest(v2rayConfig: V2rayConfig, targetTag: String, isBalancer: Boolean) {
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.inbounds.clear()

        v2rayConfig.routing.rules.clear()
        v2rayConfig.routing.rules.add(
            V2rayConfig.RoutingBean.RulesBean(
                network = "tcp,udp",
                outboundTag = if (isBalancer) null else targetTag,
                balancerTag = if (isBalancer) targetTag else null,
            )
        )
        v2rayConfig.dns = null
        v2rayConfig.fakedns = null
        v2rayConfig.stats = null
        v2rayConfig.policy = null
        v2rayConfig.outbounds.forEach { key -> key.mux = null }
    }

    private fun toConfigResult(configContext: CoreConfigContext, v2rayConfig: V2rayConfig): ConfigResult {
        return ConfigResult(
            status = true,
            guid = configContext.guid,
            content = JsonUtil.toJsonPretty(v2rayConfig) ?: ""
        )
    }

    private fun initV2rayConfig(configContext: CoreConfigContext): V2rayConfig {
        val context = configContext.context
        val assets: String
        if (needTun()) {
            assets = initConfigCacheWithTun ?: Utils.readTextFromAssets(context, "v2ray_config_with_tun.json")
            if (TextUtils.isEmpty(assets)) {
                error("Missing asset: v2ray_config_with_tun.json")
            }
            initConfigCacheWithTun = assets
        } else {
            assets = initConfigCache ?: Utils.readTextFromAssets(context, "v2ray_config.json")
            if (TextUtils.isEmpty(assets)) {
                error("Missing asset: v2ray_config.json")
            }
            initConfigCache = assets
        }
        return JsonUtil.fromJson(assets, V2rayConfig::class.java)
        ?: error("Failed to parse config template")
    }

    private fun needTun(): Boolean {
        return SettingsManager.isVpnMode() && !SettingsManager.isUsingHevTun()
    }

    private fun configureInbounds(v2rayConfig: V2rayConfig) {
        val vpn = SettingsManager.isVpnMode()
        val useHev = SettingsManager.isUsingHevTun()
        val forcedByHev = vpn && useHev
        val forcedBySocksRoot = SettingsManager.isRootMode()
        || MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)

        val enableLocalProxy = forcedByHev || forcedBySocksRoot || MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)

        val socksPort = SettingsManager.getSocksPort()
        val socksUsername = SettingsManager.getSocksUsername()
        val socksPassword = SettingsManager.getSocksPassword()
        val inbound1 = v2rayConfig.inbounds[0]
        if (inbound1.settings == null) {
            inbound1.settings = V2rayConfig.InboundBean.InSettingsBean()
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) != true) {
            inbound1.listen = AppConfig.LOOPBACK
        }
        inbound1.port = socksPort
        inbound1.settings?.udp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SOCKS_ENABLE_UDP, AppConfig.DEFAULT_SOCKS_ENABLE_UDP)
        if (socksUsername != null && socksPassword != null) {
            inbound1.settings?.auth = "password"
            inbound1.settings?.accounts = listOf(
                V2rayConfig.InboundBean.InSettingsBean.SocksAccountBean(
                    user = socksUsername,
                    pass = socksPassword
                )
            )
        } else {
            inbound1.settings?.auth = "noauth"
            inbound1.settings?.accounts = null
        }
        val fakedns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
        val sniffAllTlsAndHttp =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true) != false
        inbound1.sniffing?.enabled = fakedns || sniffAllTlsAndHttp
        inbound1.sniffing?.routeOnly =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
        if (!sniffAllTlsAndHttp) {
            inbound1.sniffing?.destOverride?.clear()
        }
        if (fakedns) {
            inbound1.sniffing?.destOverride?.add("fakedns")
        }

        if (!Utils.isXray()) {
            val inbound2 = JsonUtil.fromJson(JsonUtil.toJson(inbound1), V2rayConfig.InboundBean::class.java)
            ?: error("Failed to clone inbound template")
            inbound2.tag = EConfigType.HTTP.name.lowercase()
            inbound2.port = SettingsManager.getHttpPort()
            inbound2.protocol = EConfigType.HTTP.name.lowercase()
            inbound2.settings?.auth = null
            inbound2.settings?.udp = null
            v2rayConfig.inbounds.add(inbound2)
        }

        if (!enableLocalProxy) {
            v2rayConfig.inbounds.removeIf { it.protocol == "socks" || it.protocol == "http" }
        }

        if (needTun()) {
            val inboundTun = v2rayConfig.inbounds.firstOrNull { e -> e.tag == "tun" }
            inboundTun?.settings?.mtu = SettingsManager.getVpnMtu()
            inboundTun?.sniffing = inbound1.sniffing
        }
    }

    private fun configureFakeDns(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true
            && MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
        ) {
            val fakeDnsConfig = FakeDnsIpPool.parseOrDefault(
                MmkvManager.decodeSettingsString(AppConfig.PREF_FAKE_DNS_IP_POOL)
            )
            v2rayConfig.fakedns = listOf(
                V2rayConfig.FakednsBean(
                    ipPool = fakeDnsConfig.cidr,
                    poolSize = fakeDnsConfig.poolSize,
                )
            )
        }
    }

    private fun collectUserRuleDomainsByTag(tag: String): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && key.outboundTag == tag && !key.domain.isNullOrEmpty()) {
                key.domain?.forEach {
                    domain.add(it)
                }
            }
        }

        return domain
    }

    private fun collectCustomOutboundDomains(): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && !AppConfig.BUILTIN_OUTBOUND_TAGS.contains(key.outboundTag)
                && !key.domain.isNullOrEmpty()
            ) {
                key.domain?.forEach {
                    domain.add(it)
                }
            }
        }

        return domain
    }

    private fun configureLocalDns(configContext: CoreConfigContext, v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) != true) {
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
            val geositeCn = arrayListOf(AppConfig.GEOSITE_CN)
            val routingDomains = configContext.routingDomainRules
            .asSequence()
            .filter { it.outboundTag != AppConfig.TAG_BLOCKED }
            .flatMap { it.domain.asSequence() }
            .toList()
            .distinct()
            val finalDomain = geositeCn + routingDomains
            v2rayConfig.dns?.servers?.add(
                0,
                V2rayConfig.DnsBean.ServersBean(
                    address = "fakedns",
                    domains = finalDomain
                )
            )
        }

        if (SettingsManager.isVpnMode()) {
            if (SettingsManager.isUsingHevTun()) {
                v2rayConfig.routing.rules.add(
                    0, V2rayConfig.RoutingBean.RulesBean(
                        inboundTag = arrayListOf("socks"),
                        outboundTag = "dns-out",
                        port = "53",
                    )
                )
            } else {
                v2rayConfig.routing.rules.add(
                    0, V2rayConfig.RoutingBean.RulesBean(
                        inboundTag = arrayListOf("tun"),
                        outboundTag = "dns-out",
                        port = "53",
                    )
                )
            }
        }

        if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
            v2rayConfig.outbounds.add(
                V2rayConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null
                )
            )
        }
    }

    private fun configureRootModeDns(v2rayConfig: V2rayConfig) {
        if (!SettingsManager.isRootMode()) return

        if (v2rayConfig.routing.rules.none { it.outboundTag == "dns-out" && it.port == "53" }) {
            v2rayConfig.routing.rules.add(
                0,
                V2rayConfig.RoutingBean.RulesBean(
                    inboundTag = arrayListOf("socks"),
                    outboundTag = "dns-out",
                    port = "53",
                )
            )
        }
        if (v2rayConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            v2rayConfig.outbounds.add(
                V2rayConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = null,
                    streamSettings = null,
                    mux = null
                )
            )
        }
    }

    private fun applySpeedDisabled(v2rayConfig: V2rayConfig) {
        val speedEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true
        val trafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true

        if (!speedEnabled && !trafficEnabled) {
            v2rayConfig.stats = null

            v2rayConfig.policy?.system = null
        }
    }

    private fun configureDns(
        configContext: CoreConfigContext,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>,
    ) {
        val servers = ArrayList<Any>()
        val remoteDns = SettingsManager.getRemoteDnsServers()
        val domesticDns = SettingsManager.getDomesticDnsServers()

        remoteDns.forEach { servers.add(it) }

        val hosts = buildDnsHostsFromRoutingRules(configContext)
        val cnDomesticDnsTags = buildDnsCnModeFromRoutingRules(configContext, servers, domesticDns)
        val domesticDnsTags = buildDnsFromRoutingRules(
            configContext = configContext,
            servers = servers,
            remoteDns = remoteDns,
            domesticDns = domesticDns
        )
        domesticDnsTags.addAll(cnDomesticDnsTags)

        v2rayConfig.dns = V2rayConfig.DnsBean(
            servers = servers,
            hosts = hosts,
            tag = AppConfig.TAG_DNS,
            enableParallelQuery = if ((domesticDns.size + remoteDns.size) > 2) true else null
        )

        if (domesticDnsTags.isNotEmpty()) {
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_DIRECT,
                    inboundTag = ArrayList(domesticDnsTags),
                    domain = null
                )
            )
        }

        val dnsProxyBalancerTag = policyGroupBalancerTags[AppConfig.TAG_PROXY]
        if (dnsProxyBalancerTag != null) {
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    balancerTag = dnsProxyBalancerTag,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        } else {
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_PROXY,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        }
    }

    private fun buildDnsHostsFromRoutingRules(configContext: CoreConfigContext): MutableMap<String, Any> {
        val hosts = mutableMapOf<String, Any>()

        val blockDomains = configContext.routingDomainRules
        .asSequence()
        .filter { it.outboundTag == AppConfig.TAG_BLOCKED }
        .flatMap { it.domain.asSequence() }
        .toList()
        if (blockDomains.isNotEmpty()) {
            hosts.putAll(blockDomains.map { it to AppConfig.LOOPBACK })
        }

        hosts[AppConfig.GOOGLEAPIS_CN_DOMAIN] = AppConfig.GOOGLEAPIS_COM_DOMAIN
        hosts[AppConfig.DNS_ALIDNS_DOMAIN] = AppConfig.DNS_ALIDNS_ADDRESSES
        hosts[AppConfig.DNS_CISCO_SSE_DOMAIN] = AppConfig.DNS_CISCO_SSE_ADDRESSES
        hosts[AppConfig.DNS_CISCO_UMBRELLA_DOMAIN] = AppConfig.DNS_CISCO_UMBRELLA_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONE_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONE_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_COM_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_COM_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_WARP_DOMAIN] = AppConfig.DNS_CLOUDFLARE_WARP_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOH_DOMAIN] = AppConfig.DNS_DNSPOD_DOH_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOT_DOMAIN] = AppConfig.DNS_DNSPOD_DOT_ADDRESSES
        hosts[AppConfig.DNS_GOOGLE_DOMAIN] = AppConfig.DNS_GOOGLE_ADDRESSES
        hosts[AppConfig.DNS_QUAD9_DOMAIN] = AppConfig.DNS_QUAD9_ADDRESSES
        hosts[AppConfig.DNS_SB_DOMAIN] = AppConfig.DNS_SB_ADDRESSES
        hosts[AppConfig.DNS_YANDEX_DOMAIN] = AppConfig.DNS_YANDEX_ADDRESSES

        val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
        if (userHosts.isNotNullEmpty()) {
            val userHostsMap = userHosts?.split(",").orEmpty()
            .filter { it.isNotBlank() && it.contains(":") }
            .associate {
                val parts = it.split(":", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
            hosts.putAll(userHostsMap)
        }

        return hosts
    }

    private fun buildDnsCnModeFromRoutingRules(configContext: CoreConfigContext, servers: ArrayList<Any>, domesticDns: List<String>): List<String> {
        val cnRegionFilter = { domain: String ->
            domain.startsWith("geosite:") && (domain.endsWith("-cn") || domain.endsWith("@cn"))
            || domain == AppConfig.GEOSITE_CN
        }
        val isCnRoutingMode = configContext.routingDomainRules
        .asSequence()
        .filter { it.outboundTag == AppConfig.TAG_DIRECT }
        .flatMap { it.domain.asSequence() }
        .any { it == AppConfig.GEOSITE_CN }

        if (!isCnRoutingMode) {
            return emptyList()
        }

        val geoipCn = arrayListOf(AppConfig.GEOIP_CN)
        val cnDomains = configContext.routingDomainRules
        .asSequence()
        .filter { it.outboundTag == AppConfig.TAG_DIRECT }
        .flatMap { it.domain.asSequence() }
        .filter { cnRegionFilter(it) }
        .toList()
        if (cnDomains.isEmpty()) {
            return emptyList()
        }

        val cnDomesticDnsTags = mutableListOf<String>()
        domesticDns.forEachIndexed { index, address ->
            val cnDomesticDnsTag = "${AppConfig.TAG_DOMESTIC_DNS}_cn_expect_${index}"
            servers.add(
                V2rayConfig.DnsBean.ServersBean(
                    address = address,
                    domains = cnDomains,
                    expectIPs = geoipCn,
                    skipFallback = true,
                    tag = cnDomesticDnsTag
                )
            )
            cnDomesticDnsTags.add(cnDomesticDnsTag)
        }
        return cnDomesticDnsTags
    }

    private fun buildDnsFromRoutingRules(
        configContext: CoreConfigContext,
        servers: ArrayList<Any>,
        remoteDns: List<String>,
        domesticDns: List<String>,
    ): MutableList<String> {
        val domesticDnsTags = mutableListOf<String>()
        configContext.routingDomainRules.forEachIndexed { ruleIndex, rule ->
            when (rule.outboundTag) {
                AppConfig.TAG_DIRECT -> {
                    domesticDns.forEachIndexed { dnsIndex, address ->
                        val tag = "${AppConfig.TAG_DOMESTIC_DNS}_${ruleIndex}_$dnsIndex"
                        servers.add(
                            V2rayConfig.DnsBean.ServersBean(
                                address = address,
                                domains = rule.domain,
                                skipFallback = true,
                                tag = tag
                            )
                        )
                        domesticDnsTags.add(tag)
                    }
                }
                AppConfig.TAG_BLOCKED -> Unit
                else -> {
                    servers.add(
                        V2rayConfig.DnsBean.ServersBean(
                            address = remoteDns.first(),
                            domains = rule.domain,
                        )
                    )
                }
            }
        }
        return domesticDnsTags
    }

    private fun resolveOutboundDomainsToHosts(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, AppConfig.DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD) != "1") {
            return
        }

        val proxyOutboundList = v2rayConfig.getAllProxyOutbound()
        val dns = v2rayConfig.dns ?: return
        val newHosts = dns.hosts?.toMutableMap() ?: mutableMapOf()
        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true

        for (item in proxyOutboundList) {
            val domain = item.getServerAddress()
            if (domain.isNullOrEmpty()) {
                continue
            }

            if (newHosts.containsKey(domain)) {
                item.ensureSockopt().domainStrategy = "UseIP"
                item.ensureSockopt().happyEyeballs = V2rayConfig.OutboundBean.StreamSettingsBean.HappyEyeballsBean(
                    prioritizeIPv6 = preferIpv6,
                    interleave = 2
                )
                continue
            }

            val resolvedIps = HttpUtil.resolveHostToIP(domain, preferIpv6)
            if (resolvedIps.isNullOrEmpty()) {
                continue
            }

            item.ensureSockopt().domainStrategy = "UseIP"
            item.ensureSockopt().happyEyeballs = V2rayConfig.OutboundBean.StreamSettingsBean.HappyEyeballsBean(
                prioritizeIPv6 = preferIpv6,
                interleave = 2
            )
            newHosts[domain] = if (resolvedIps.size == 1) {
                resolvedIps[0]
            } else {
                resolvedIps
            }
        }

        dns.hosts = newHosts
    }

    private fun applyTcpKeepAlive(v2rayConfig: V2rayConfig) {
        val idleSeconds = MmkvManager.decodeSettingsString(AppConfig.PREF_TCP_KEEPALIVE_IDLE, "30")
        ?.toIntOrNull() ?: 60
        if (idleSeconds <= 0) return

        v2rayConfig.getAllProxyOutbound().forEach { outbound ->
            outbound.ensureSockopt().tcpKeepAliveIdle = idleSeconds
        }
    }

    private fun convertProfile2Outbound(profileItem: ProfileItem): V2rayConfig.OutboundBean? {
        return CoreOutboundBuilder.convert(profileItem)
    }

    private fun applyObservability(v2rayConfig: V2rayConfig, strategies: List<BalancerStrategy>) {
        val allObsSelectors = strategies
        .mapNotNull { it.observatory?.subjectSelector }
        .flatten()
        .distinct()
        val obsTemplate = strategies.firstNotNullOfOrNull { it.observatory }
        if (obsTemplate != null && allObsSelectors.isNotEmpty()) {
            v2rayConfig.observatory = V2rayConfig.ObservatoryObject(
                subjectSelector = allObsSelectors,
                probeUrl = obsTemplate.probeUrl,
                probeInterval = obsTemplate.probeInterval,
                enableConcurrency = obsTemplate.enableConcurrency
            )
        }

        val allBurstSelectors = strategies
        .mapNotNull { it.burstObservatory?.subjectSelector }
        .flatten()
        .distinct()
        val burstTemplate = strategies.firstNotNullOfOrNull { it.burstObservatory }
        if (burstTemplate != null && allBurstSelectors.isNotEmpty()) {
            v2rayConfig.burstObservatory = V2rayConfig.BurstObservatoryObject(
                subjectSelector = allBurstSelectors,
                pingConfig = burstTemplate.pingConfig
            )
        }
    }

    private fun configureRouting(
        configContext: CoreConfigContext,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>
    ) {

        v2rayConfig.routing.domainStrategy =
        MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
        ?: "AsIs"

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            appendRoutingUserRule(configContext, key, v2rayConfig, policyGroupBalancerTags)
        }
    }

    private fun appendRoutingUserRule(
        configContext: CoreConfigContext,
        item: RulesetItem?,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>
    ) {
        val context = configContext.context
        if (item == null || !item.enabled) {
            return
        }

        val rule = JsonUtil.fromJson(JsonUtil.toJson(item), V2rayConfig.RoutingBean.RulesBean::class.java) ?: return

        rule.ip?.let { ipList ->
            val updatedIpList = ArrayList<String>()
            ipList.forEach { ip ->
                when (ip) {
                    AppConfig.GEOIP_CN -> updatedIpList.add("ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn")
                    AppConfig.GEOIP_PRIVATE -> updatedIpList.add("ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private")
                    else -> updatedIpList.add(ip)
                }
            }
            rule.ip = updatedIpList
        }

        if (SettingsManager.canUseProcessRouting()) {
            rule.process?.let { processList ->
                if (processList.isNotEmpty()) {
                    val uids = PackageUidResolver.packageNamesToUids(context, processList)
                    rule.process = uids.ifEmpty { null }
                }
            }
        } else {
            rule.process = null
        }

        val outboundTag = rule.outboundTag

        policyGroupBalancerTags[outboundTag]?.let { balancerTag ->
            rule.outboundTag = null
            rule.balancerTag = balancerTag
        }

        if (!outboundTag.isNullOrBlank()
            && outboundTag !in policyGroupBalancerTags
            && outboundTag !in AppConfig.BUILTIN_OUTBOUND_TAGS
            && v2rayConfig.outbounds.none { it.tag == outboundTag }
        ) {
            LogUtil.w(AppConfig.TAG, "Outbound tag '$outboundTag' not found, falling back to '${AppConfig.TAG_PROXY}'")
            rule.outboundTag = AppConfig.TAG_PROXY
        }

        v2rayConfig.routing.rules.add(rule)
    }

    private fun buildBalancerStrategy(
        strategyType: BalancerStrategyType,
        selector: List<String>,
        balancerTag: String = AppConfig.TAG_BALANCER,
        fallbackTag: String? = null,
        profile: ProfileItem? = null,
    ): BalancerStrategy {
        val probeUrl = SettingsManager.getDelayTestUrl()
        val leastPingInterval = decodeObservatoryDuration(profile?.policyGroupObservatoryLeastPingInterval, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
        val leastLoadInterval = decodeObservatoryDuration(profile?.policyGroupObservatoryLeastLoadInterval, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
        val leastLoadMethod = profile?.policyGroupObservatoryLeastLoadMethod?.trim()?.takeIf { it.isNotEmpty() } ?: AppConfig.OBSERVATORY_LEAST_LOAD_METHOD
        val leastLoadSampling = decodeObservatorySampling(profile?.policyGroupObservatoryLeastLoadSampling)
        val leastLoadTimeout = decodeObservatoryDuration(profile?.policyGroupObservatoryLeastLoadTimeout, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)
        val balancer = V2rayConfig.RoutingBean.BalancerBean(
            tag = balancerTag,
            selector = selector,
            fallbackTag = fallbackTag,
            strategy = V2rayConfig.RoutingBean.StrategyObject(type = strategyType.policyGroupType)
        )
        val observatory = if (strategyType.requiresObservatory || fallbackTag != null) {
            V2rayConfig.ObservatoryObject(
                subjectSelector = selector,
                probeUrl = probeUrl,
                probeInterval = leastPingInterval,
                enableConcurrency = true
            )
        } else null
        val burstObservatory = if (strategyType.requiresBurstObservatory) {
            V2rayConfig.BurstObservatoryObject(
                subjectSelector = selector,
                pingConfig = V2rayConfig.BurstObservatoryObject.PingConfigObject(
                    destination = probeUrl,
                    httpMethod = leastLoadMethod,
                    interval = leastLoadInterval,
                    sampling = leastLoadSampling,
                    timeout = leastLoadTimeout
                )
            )
        } else null
        return BalancerStrategy(balancer, observatory, burstObservatory)
    }

    private fun decodeObservatoryDuration(value: String?, default: String): String {
        val trimmed = value?.trim()
        return if (!trimmed.isNullOrEmpty() && AppConfig.OBSERVATORY_DURATION_PATTERN.matches(trimmed)) {
            trimmed
        } else {
            default
        }
    }

    private fun decodeObservatorySampling(value: String?): Int {
        return value
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING.toInt()
    }

    private data class BalancerStrategy(
        val balancer: V2rayConfig.RoutingBean.BalancerBean,
        val observatory: V2rayConfig.ObservatoryObject? = null,
        val burstObservatory: V2rayConfig.BurstObservatoryObject? = null,
    )

}
