package com.miku.ray.handler

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.miku.ray.AppConfig.GEOIP_PRIVATE
import com.miku.ray.AppConfig.GEOSITE_PRIVATE
import com.miku.ray.AppConfig.TAG_DIRECT
import com.miku.ray.AppConfig.VPN
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.dto.V2rayConfig
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.RoutingType
import com.miku.ray.enums.VpnInterfaceAddressConfig
import com.miku.ray.handler.MmkvManager.decodeAllServerList
import com.miku.ray.handler.MmkvManager.decodeServerConfig
import com.miku.ray.handler.MmkvManager.decodeSubsList
import com.miku.ray.handler.MmkvManager.decodeSubscription
import com.miku.ray.handler.MmkvManager.encodeSubscription
import com.miku.ray.handler.MmkvManager.removeSubscription
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Locale
import kotlin.random.Random

object SettingsManager {

    @Volatile
    private var runtimeSocksPort: Int? = null

    fun initApp(context: Context) {
        ensureDefaultSettings()
        initRoutingRulesets(context)
        migrateServerListToSubscriptions()
        migrateHysteria2PinSHA256()
    }

    fun preloadAllBanners(context: Context) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
            AppConfig.PREF_CUSTOM_THEME_BANNER_URI,
        )
        bannerKeys.forEach { key ->
            preloadBanner(context, MmkvManager.decodeSettingsString(key))
        }
    }

    private const val MAX_BANNER_PRELOAD_SIZE = 1600

    fun preloadBanner(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val appContext = context.applicationContext
            Glide.with(appContext)
            .load(Uri.parse(uriString))
            .downsample(DownsampleStrategy.CENTER_INSIDE)
            .override(MAX_BANNER_PRELOAD_SIZE, MAX_BANNER_PRELOAD_SIZE)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .skipMemoryCache(true)
            .preload()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to preload banner $uriString", e)
        }
    }

    private fun initRoutingRulesets(context: Context) {
        val exist = MmkvManager.decodeRoutingRulesets()
        if (exist.isNullOrEmpty()) {
            val rulesetList = getPresetRoutingRulesets(context)
            MmkvManager.encodeRoutingRulesets(rulesetList)
        }
    }

    private fun getPresetRoutingRulesets(context: Context, index: Int = 0): MutableList<RulesetItem>? {
        val fileName = RoutingType.fromIndex(index).fileName
        val assets = Utils.readTextFromAssets(context, fileName)
        if (TextUtils.isEmpty(assets)) {
            return null
        }

        return JsonUtil.fromJsonSafe(assets, Array<RulesetItem>::class.java)
        ?.toMutableList()
        ?.also { ensureRoutingRulesetIds(it) }
    }

    fun resetRoutingRulesetsFromPresets(context: Context, index: Int) {
        val rulesetList = getPresetRoutingRulesets(context, index) ?: return
        resetRoutingRulesetsCommon(rulesetList)
    }

    fun resetRoutingRulesets(content: String?): Boolean {
        if (content.isNullOrEmpty()) {
            return false
        }

        try {
            val rulesetList = JsonUtil.fromJsonSafe(content, Array<RulesetItem>::class.java)?.toMutableList()
            if (rulesetList.isNullOrEmpty()) {
                return false
            }

            resetRoutingRulesetsCommon(rulesetList)
            return true
        } catch (e: Exception) {
            LogUtil.e(ANG_PACKAGE, "Failed to reset routing rulesets", e)
            return false
        }
    }

    private fun resetRoutingRulesetsCommon(rulesetList: MutableList<RulesetItem>) {
        val lockedRulesets = MmkvManager.decodeRoutingRulesets()
        ?.filter { it.locked == true }
        .orEmpty()
        val importedRulesets = rulesetList.filterNot { imported ->

            lockedRulesets.any { locked -> locked.id == imported.id && locked == imported }
        }
        val mergedRulesets = (lockedRulesets + importedRulesets).toMutableList()
        ensureRoutingRulesetIds(mergedRulesets)
        MmkvManager.encodeRoutingRulesets(mergedRulesets)
    }

    fun ensureRoutingRulesetIds(rulesetList: MutableList<RulesetItem>): Boolean {
        val usedIds = HashSet<String>(rulesetList.size)
        var changed = false
        rulesetList.forEach { ruleset ->
            // Gson fills fields reflectively and a hand-edited import can carry
            // an explicit null, both bypassing the declared non-null default.
            // The nullable cast keeps the empty branch real for R8, which would
            // otherwise fold it away using the field's declared type.
            val currentId = (ruleset.id as String?)?.trim().orEmpty()
            if (currentId.isEmpty() || !usedIds.add(currentId)) {
                var replacement: String
                do {
                    replacement = Utils.getUuid()
                } while (!usedIds.add(replacement))
                ruleset.id = replacement
                changed = true
            } else if (ruleset.id != currentId) {
                ruleset.id = currentId
                changed = true
            }
        }
        return changed
    }

    fun getRoutingRuleset(index: Int): RulesetItem? {
        if (index < 0) return null
        return MmkvManager.decodeRoutingRulesets()?.getOrNull(index)
    }

    fun saveRoutingRuleset(index: Int, ruleset: RulesetItem?) {
        if (ruleset == null) return
        val rulesetList = MmkvManager.decodeRoutingRulesets()?.toMutableList() ?: mutableListOf()
        if (index < 0) {
            val usedIds = rulesetList.mapTo(HashSet()) { it.id }
            if (ruleset.id.isBlank() || !usedIds.add(ruleset.id)) {
                do {
                    ruleset.id = Utils.getUuid()
                } while (!usedIds.add(ruleset.id))
            }
            rulesetList.add(0, ruleset)
        } else if (index < rulesetList.size) {
            ruleset.id = rulesetList[index].id
            rulesetList[index] = ruleset
        } else {
            return
        }
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    fun removeRoutingRuleset(index: Int) {
        val rulesetList = MmkvManager.decodeRoutingRulesets()?.toMutableList() ?: return
        if (index !in rulesetList.indices) return
        rulesetList.removeAt(index)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }
    fun routingRulesetsBypassLan(): Boolean {
        val vpnBypassLan = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
        if (vpnBypassLan == "1") {
            return true
        } else if (vpnBypassLan == "2") {
            return false
        }

        val guid = MmkvManager.getSelectServer() ?: return false
        val config = decodeServerConfig(guid) ?: return false
        if (config.configType == EConfigType.CUSTOM) {
            val raw = MmkvManager.decodeServerRaw(guid) ?: return false
            val v2rayConfig = JsonUtil.fromJsonSafe(raw, V2rayConfig::class.java)
            val exist = v2rayConfig?.routing?.rules?.filter { it.outboundTag == TAG_DIRECT }?.any {
                it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
            }
            return exist == true
        }

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        val exist = rulesetItems?.filter { it.enabled && it.outboundTag == TAG_DIRECT }?.any {
            it.domain?.contains(GEOSITE_PRIVATE) == true || it.ip?.contains(GEOIP_PRIVATE) == true
        }
        return exist == true
    }

    fun swapRoutingRuleset(fromPosition: Int, toPosition: Int) {
        val rulesetList = MmkvManager.decodeRoutingRulesets()
        if (rulesetList.isNullOrEmpty()) return

        Collections.swap(rulesetList, fromPosition, toPosition)
        MmkvManager.encodeRoutingRulesets(rulesetList)
    }

    fun saveSubscriptionsOrder(orderedGuids: List<String>) {
        val subsList = decodeSubsList()
        if (subsList.isEmpty()) return
        val reordered = orderedGuids.filter { it in subsList }.toMutableList()
        subsList.filterNot { it in reordered }.forEach { reordered.add(it) }
        MmkvManager.encodeSubsList(reordered)
    }

    @Deprecated("Use saveSubscriptionsOrder for drag reorder; this only swaps two positions")
    fun swapSubscriptions(fromPosition: Int, toPosition: Int) {
        val subsList = decodeSubsList()
        if (subsList.isEmpty()) return

        val item = subsList.removeAt(fromPosition)
        subsList.add(toPosition, item)
        MmkvManager.encodeSubsList(subsList)
    }

    fun getServerViaRemarks(remarks: String?): ProfileItem? {
        if (remarks.isNullOrEmpty()) {
            return null
        }
        val serverList = decodeAllServerList()
        return serverList
        .mapNotNull { guid -> decodeServerConfig(guid) }
        .firstOrNull { it.remarks == remarks }
    }

    fun getProfileRemarks(excludeConfigTypes: Set<EConfigType> = setOf(EConfigType.CUSTOM)): List<String> {
        return decodeAllServerList()
        .asSequence()
        .mapNotNull { guid -> decodeServerConfig(guid) }
        .filter { profile -> profile.configType !in excludeConfigTypes }
        .map { it.remarks.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
    }

    fun removeSubscriptionWithDefault(subid: String) {
        SubscriptionUpdater.cancelOne(subId = subid)
        removeSubscription(subid)

        val subsList2 = decodeSubsList()
        if (subsList2.isNotEmpty()) {
            return
        }

        val defaultSub = SubscriptionItem(
            remarks = "Miku",
        )
        encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)
    }

    fun getSocksPort(): Int {
        com.miku.ray.MikuSettings.impl?.let { return it.mixedPort() }
        val port =
        if (IsDynamicSocksPort()) {
            runtimeSocksPort ?: refreshRuntimeSocksPort()
        } else {
            Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
        }
        return port ?: AppConfig.PORT_SOCKS.toInt()
    }

    @Synchronized
    fun refreshRuntimeSocksPort(): Int? {
        if (com.miku.ray.MikuSettings.impl != null) return null
        if (IsDynamicSocksPort()) {
            runtimeSocksPort = generateRandomSocksPort()
            return runtimeSocksPort
        }
        return null
    }

    fun getSocksUsername(): String? {
        com.miku.ray.MikuSettings.impl?.let { return it.proxyCredentials()?.first }
        return MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_USERNAME)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getSocksPassword(): String? {
        com.miku.ray.MikuSettings.impl?.let { return it.proxyCredentials()?.second }
        return MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PASSWORD)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getHttpPort(): Int {
        // MikuRay's Xray core opened separate SOCKS and HTTP inbounds, which is
        // why this used to step one port up. The core here opens a single mixed
        // inbound that answers both protocols, so the HTTP port *is* the SOCKS
        // port — and the features that dial through it (the address lookup,
        // subscription updates over the proxy, asset downloads) land on the
        // listener the core actually opened.
        return getSocksPort()
    }

    private fun IsDynamicSocksPort(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)
    }

    private fun generateRandomSocksPort(): Int {
        return Random.nextInt(10000, 65535)
    }

    fun initAssets(context: Context, assets: AssetManager) {
        val geo = arrayOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)
        val targetFolders = listOfNotNull(
            Utils.userAssetPath(context).takeIf { it.isNotEmpty() },
            Utils.legacyExternalAssetPath(context).takeIf { it.isNotEmpty() },
        )

        if (targetFolders.isEmpty()) {
            LogUtil.e(ANG_PACKAGE, "asset copy failed: no writable asset folder available")
            return
        }

        targetFolders.forEach { folderPath ->
            try {
                val folder = File(folderPath).apply { mkdirs() }
                assets.list("")
                ?.filter { geo.contains(it) }
                ?.filter { !File(folder, it).exists() }
                ?.forEach {
                    val target = File(folder, it)
                    assets.open(it).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    LogUtil.i(AppConfig.TAG, "Copied from apk assets folder to ${target.absolutePath}")
                }
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "asset copy failed for $folderPath", e)
            }
        }
    }

    fun getDomesticDnsServers(): List<String> {
        val domesticDns =
        MmkvManager.decodeSettingsString(AppConfig.PREF_DOMESTIC_DNS) ?: AppConfig.DNS_DIRECT
        val ret = domesticDns.split(",").map { it.trim() }.filter { it.isNotEmpty() && (Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it)) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_DIRECT)
        }
        return ret
    }

    fun getRemoteDnsServers(): List<String> {
        val remoteDns =
        MmkvManager.decodeSettingsString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_PROXY
        val ret = remoteDns.split(",").map { it.trim() }.filter { it.isNotEmpty() && (Utils.isPureIpAddress(it) || Utils.isCoreDNSAddress(it)) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_PROXY)
        }
        return ret
    }

    fun getVpnDnsServers(): List<String> {
        val vpnDns = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_DNS) ?: AppConfig.DNS_VPN
        return vpnDns.split(",").map { it.trim() }.filter { it.isNotEmpty() && Utils.isPureIpAddress(it) }
    }

    fun getDelayTestUrl(second: Boolean = false): String {
        if (second) return AppConfig.DELAY_TEST_URL2

        return MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: AppConfig.DELAY_TEST_URL
    }

    fun getRealPingConcurrency(): Int {
        val value = MmkvManager.decodeSettingsString(AppConfig.PREF_REAL_PING_CONCURRENCY)?.toIntOrNull() ?: 16
        return value.coerceIn(1, 128)
    }

    fun getCountryCodeTestTimeout(): Int {
        val seconds = MmkvManager.decodeSettingsString(AppConfig.PREF_COUNTRY_CODE_TIMEOUT)?.toIntOrNull() ?: 5
        return seconds.coerceIn(1, 15) * 1000
    }

    fun getLocale(): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) appLocales[0] ?: Utils.getSysLocale() else Utils.getSysLocale()
    }

    fun setNightMode() {
        when (MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0")) {
            "0" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "3" -> AppCompatDelegate.setDefaultNightMode(
                if (com.miku.ray.util.ThemeManager.isAutoDayTime()) {
                    AppCompatDelegate.MODE_NIGHT_NO
                } else {
                    AppCompatDelegate.MODE_NIGHT_YES
                }
            )
        }
    }

    fun refreshAutoNightModeIfNeeded() {
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") == "3") {
            setNightMode()
        }
    }

    fun getCurrentVpnInterfaceAddressConfig(): VpnInterfaceAddressConfig {
        val selectedIndex = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, "0")?.toInt()
        return VpnInterfaceAddressConfig.getConfigByIndex(selectedIndex ?: 0)
    }

    fun getVpnMtu(): Int {
        return Utils.parseInt(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_MTU), AppConfig.VPN_MTU)
    }

    fun isUsingHevTun(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true)
    }

    fun isSendHwidEnabled(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_SEND_HWID, false)
    }

    fun isVpnMode(): Boolean {
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE)
        return mode == null || mode == VPN
    }

    fun isRootMode(): Boolean {
        return MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_MODE_ENABLE, false)
    }

    fun canUseProcessRouting(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        if (isUsingHevTun()) {
            return false
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false) == false) {
            return false
        }

        return true
    }

    internal fun ensureDefaultSettings() {
        ensureDefaultValue(AppConfig.PREF_MODE, VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        ensureDefaultValue(AppConfig.PREF_VPN_MTU, AppConfig.VPN_MTU.toString())
        ensureDefaultValue(AppConfig.PREF_SOCKS_PORT, AppConfig.PORT_SOCKS)
        ensureDefaultValue(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY)
        ensureDefaultValue(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        ensureDefaultValue(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DELAY_TEST_URL)
        ensureDefaultValue(AppConfig.PREF_IP_API_URL, AppConfig.IP_API_URL)
        ensureDefaultValue(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, AppConfig.HEVTUN_RW_TIMEOUT)
        ensureDefaultValue(AppConfig.PREF_HEV_TUNNEL_ICMP, "off")
        ensureDefaultValue(AppConfig.PREF_HEV_TUNNEL_UDP_MODE, "udp")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_LENGTH, "50-100")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
        ensureDefaultValue(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")
    }

    private fun ensureDefaultValue(key: String, default: String) {
        if (MmkvManager.decodeSettingsString(key).isNullOrEmpty()) {
            MmkvManager.encodeSettings(key, default)
        }
    }

    fun resetAllSettings(context: Context) {
        MmkvManager.clearAllSettings()
        ensureDefaultSettings()
        initRoutingRulesets(context)
    }

    @Suppress("DEPRECATION")
    private fun migrateHysteria2PinSHA256() {
        val migrationKey = "hysteria2_pin_sha256_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        val serverList = decodeAllServerList()

        for (guid in serverList) {
            val profile = decodeServerConfig(guid) ?: continue
            if (profile.configType != EConfigType.HYSTERIA2) {
                continue
            }
            if (profile.pinSHA256.isNullOrEmpty() || !profile.pinnedCA256.isNullOrEmpty()) {
                continue
            }
            profile.pinnedCA256 = profile.pinSHA256
            profile.pinSHA256 = null
            MmkvManager.encodeServerConfig(guid, profile)
        }

        MmkvManager.encodeSettings(migrationKey, true)
    }

    private fun migrateServerListToSubscriptions() {
        val migrationKey = "server_list_to_subscriptions_migrated"
        if (MmkvManager.decodeSettingsBool(migrationKey, false)) {
            return
        }

        ensureDefaultSubscription()

        val oldJson = MmkvManager.readLegacyServerList()
        if (oldJson.isNullOrBlank()) {
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val guids = JsonUtil.fromJsonSafe(oldJson, Array<String>::class.java) ?: run {
            MmkvManager.encodeSettings(migrationKey, true)
            return
        }

        val subscriptionServerMap = mutableMapOf<String, MutableList<String>>()

        guids.forEach { guid ->
            val config = decodeServerConfig(guid) ?: return@forEach
            val subId = config.subscriptionId.ifEmpty { DEFAULT_SUBSCRIPTION_ID }

            subscriptionServerMap.getOrPut(subId) { mutableListOf() }.add(guid)
        }

        subscriptionServerMap.forEach { (subId, serverGuids) ->
            MmkvManager.encodeServerList(serverGuids, subId)
        }

        MmkvManager.encodeSettings(migrationKey, true)
    }

    private fun ensureDefaultSubscription() {
        if (decodeSubscription(DEFAULT_SUBSCRIPTION_ID) == null) {
            val defaultSub = SubscriptionItem(
                remarks = "Miku",
            )
            encodeSubscription(DEFAULT_SUBSCRIPTION_ID, defaultSub)

            val subsList = decodeSubsList()
            if (subsList.count() > 1) {
                val reordered = subsList.toMutableList()
                reordered.add(reordered.removeAt(0))
                saveSubscriptionsOrder(reordered)
            }
        }
    }

}
