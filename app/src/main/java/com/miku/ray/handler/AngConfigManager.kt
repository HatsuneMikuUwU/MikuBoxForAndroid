package com.miku.ray.handler

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.text.TextUtils
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.dto.ProfileDiffEntry
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.fmt.ClashMetaFmt
import com.miku.ray.fmt.CustomFmt
import com.miku.ray.fmt.Hysteria2Fmt
import com.miku.ray.fmt.ShadowsocksFmt
import com.miku.ray.fmt.SIP008Fmt
import com.miku.ray.fmt.SocksFmt
import com.miku.ray.fmt.TrojanFmt
import com.miku.ray.fmt.V2rayNFmt
import com.miku.ray.fmt.VlessFmt
import com.miku.ray.fmt.VmessFmt
import com.miku.ray.fmt.WireguardFmt
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.QRCodeDecoder
import com.miku.ray.util.SubscriptionImportChoice
import com.miku.ray.util.SubscriptionUserinfoParser
import com.miku.ray.util.Utils
import kotlinx.coroutines.CancellationException
import java.net.URI

object AngConfigManager {

    private data class ParsedProfile(
        val profile: ProfileItem,
        val rawConfig: String? = null,
    )

    private val configFmtParsers: Map<String, (String) -> ProfileItem?> by lazy {
        mapOf(
            EConfigType.VMESS.protocolScheme to VmessFmt::parse,
            EConfigType.SHADOWSOCKS.protocolScheme to ShadowsocksFmt::parse,
            EConfigType.SOCKS.protocolScheme to SocksFmt::parse,
            AppConfig.SOCKS4 to SocksFmt::parse,
            AppConfig.SOCKS5 to SocksFmt::parse,
            EConfigType.TROJAN.protocolScheme to TrojanFmt::parse,
            EConfigType.VLESS.protocolScheme to VlessFmt::parse,
            EConfigType.WIREGUARD.protocolScheme to WireguardFmt::parse,
            EConfigType.HYSTERIA2.protocolScheme to Hysteria2Fmt::parse,
            AppConfig.HY2 to Hysteria2Fmt::parse
        )
    }

    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            com.miku.ray.MikuProfiles.impl?.get(guid)?.let {
                Utils.setClipboard(context, it.config)
                return 0
            }
            val result = CoreConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    private fun shareConfig(guid: String): String {
        com.miku.ray.MikuProfiles.impl?.get(guid)?.let { return it.config }
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
                else -> {}
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    suspend fun importBatchConfig(
        server: String?,
        subid: String,
        append: Boolean,
        requestSubscriptionName: (suspend (String?, Set<String>) -> SubscriptionImportChoice?)? = null
    ): Pair<Int, Int> {
        com.miku.ray.MikuProfiles.impl?.let { return it.importContent(server.orEmpty(), subid) }
        return try {
            val decodedServer = Utils.decode(server)

            var count = parseClashMetaConfig(decodedServer, subid, append)
            if (count <= 0) {
                count = parseClashMetaConfig(server, subid, append)
            }
            if (count <= 0) {
                count = parseSIP008Config(decodedServer, subid, append)
            }
            if (count <= 0) {
                count = parseSIP008Config(server, subid, append)
            }
            if (count <= 0) {
                count = parseBatchConfig(decodedServer, subid, append)
            }
            if (count <= 0) {
                count = parseBatchConfig(server, subid, append)
            }
            if (count <= 0) {
                count = parseCustomConfigServer(server, subid, append)
            }

            var countSub = parseBatchSubscription(server, requestSubscriptionName)
            if (countSub <= 0) {
                countSub = parseBatchSubscription(decodedServer, requestSubscriptionName)
            }
            if (countSub > 0) {
                updateConfigViaSubAll()
            }

            count to countSub
        } catch (e: ProfileStorageException) {
            LogUtil.e(AppConfig.TAG, "Failed to store imported profiles", e)
            0 to 0
        }
    }

    private suspend fun parseBatchSubscription(
        servers: String?,
        requestSubscriptionName: (suspend (String?, Set<String>) -> SubscriptionImportChoice?)?
    ): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
            .distinct()
            .forEach { str ->
                if (Utils.isValidSubUrl(str)) {
                    count += importUrlAsSubscription(str, requestSubscriptionName)
                }
            }
            return count
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    private fun parseSIP008Config(content: String?, subid: String, append: Boolean): Int {
        try {
            val subItem = MmkvManager.decodeSubscription(subid)
            val configs = SIP008Fmt.parse(content)
            .filter { config -> matchesSubscriptionFilters(config, subItem) }
            .onEach { config -> config.subscriptionId = subid }
            if (configs.isNotEmpty()) {
                commitProfiles(
                    configs = configs.map { ParsedProfile(it) },
                    subid = subid,
                    append = append,
                )
            }
            return configs.size
        } catch (e: ProfileStorageException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse SIP008 subscription", e)
        }
        return 0
    }
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }

            val subItem = MmkvManager.decodeSubscription(subid)

            val configs = mutableListOf<ProfileItem>()
            val v2raynLines = mutableListOf<String>()

            servers.lines()
            .distinct()
            .reversed()
            .forEach { line ->
                if (line.startsWith(AppConfig.V2RAYNFMTS, ignoreCase = true)) {
                    v2raynLines.add(line)
                } else {
                    parseConfig(line, subid, subItem)?.let { configs.add(it) }
                }
            }

            val v2raynConfigs = V2rayNFmt.parse(v2raynLines, subid)
            .filter { matchesSubscriptionFilters(it, subItem) }
            .onEach { it.subscriptionId = subid }
            val allConfigs = v2raynConfigs + configs

            if (allConfigs.isNotEmpty()) {
                commitProfiles(
                    configs = allConfigs.map(::ParsedProfile),
                    subid = subid,
                    append = append,
                )
            }

            return allConfigs.size
        } catch (e: ProfileStorageException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    private fun commitProfiles(configs: List<ParsedProfile>, subid: String, append: Boolean) {
        val keyToProfile = linkedMapOf<String, ProfileItem>()
        val rawConfigs = mutableMapOf<String, String>()

        configs.forEach { parsed ->
            val key = Utils.getUuid()
            keyToProfile[key] = parsed.profile
            parsed.rawConfig?.let { raw -> rawConfigs[key] = raw }
        }

        MmkvManager.saveServerProfiles(
            profiles = keyToProfile,
            rawConfigs = rawConfigs,
            subscriptionId = subid,
            append = append,
        )
    }

    private fun parseCustomConfigServer(server: String?, subid: String, append: Boolean): Int {
        if (server == null) {
            return 0
        }
        val subItem = MmkvManager.decodeSubscription(subid)
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                JsonUtil.fromJson(server, Array<Any>::class.java) ?: arrayOf()

                if (serverList.isNotEmpty()) {
                    val configs = mutableListOf<ParsedProfile>()
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv))
                        if (!matchesSubscriptionFilters(config, subItem)) {
                            continue
                        }
                        config.subscriptionId = subid
                        configs.add(ParsedProfile(config, JsonUtil.toJsonPretty(srv) ?: ""))
                    }
                    if (configs.isNotEmpty()) {
                        commitProfiles(configs, subid, append)
                    }
                    return configs.size
                }
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                val config = CustomFmt.parse(server)
                if (!matchesSubscriptionFilters(config, subItem)) {
                    return 0
                }
                config.subscriptionId = subid
                commitProfiles(listOf(ParsedProfile(config, server)), subid, append)
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server)
                if (!matchesSubscriptionFilters(config, subItem)) {
                    return 0
                }

                config.subscriptionId = subid
                commitProfiles(listOf(ParsedProfile(config, server)), subid, append)
                return 1
            } catch (e: ProfileStorageException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    private fun matchesSubscriptionFilters(config: ProfileItem, subItem: SubscriptionItem?): Boolean {
        if (subItem?.filter.isNotNullEmpty() && config.remarks.isNotNullEmpty()) {
            val matched = Regex(pattern = subItem?.filter.orEmpty())
            .containsMatchIn(input = config.remarks)
            if (!matched) return false
        }

        if (subItem?.networkFilter.isNotNullEmpty()) {
            val allowedNetworks = subItem?.networkFilter.orEmpty()
            .split(',', '，', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
            if (allowedNetworks.isNotEmpty()) {
                val configNetwork = config.network.orEmpty().lowercase().ifEmpty { "tcp" }
                if (configNetwork !in allowedNetworks) return false
            }
        }

        if (subItem?.protocolFilter.isNotNullEmpty()) {
            val allowedProtocols = subItem?.protocolFilter.orEmpty()
            .split(',', '，', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
            if (allowedProtocols.isNotEmpty()) {

                val configProtocol = if (config.configType == EConfigType.CUSTOM) {
                    config.customProtocol.orEmpty().lowercase()
                } else {
                    config.configType.protocolScheme.removeSuffix("://").lowercase()
                }
                if (configProtocol.isEmpty() || configProtocol !in allowedProtocols) return false
            }
        }

        return true
    }

    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?
    ): ProfileItem? {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return null
            }

            val config = configFmtParsers.firstNotNullOfOrNull { (scheme, parser) ->
                if (str.startsWith(scheme)) parser(str) else null
            }

            if (config == null) {
                return null
            }

            if (!matchesSubscriptionFilters(config, subItem)) {
                return null
            }

            config.subscriptionId = subid

            return config
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse config", e)
            return null
        }
    }

    fun updateConfigViaSubAll(): SubscriptionUpdateResult {
        if (com.miku.ray.MikuProfiles.impl != null) {
            val subscriptions = com.miku.ray.MikuSubscriptions.list()
            val successes = subscriptions.count { com.miku.ray.MikuSubscriptions.refresh(it.id) }
            com.miku.ray.MikuProfiles.impl?.sync()
            return SubscriptionUpdateResult(configCount = successes, successCount = successes,
                failureCount = subscriptions.size - successes)
        }
        return try {
            val subscriptions = MmkvManager.decodeSubscriptions()
            subscriptions.fold(SubscriptionUpdateResult()) { acc, subscription ->
                acc + updateConfigViaSub(subscription)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            SubscriptionUpdateResult()
        }
    }

    fun removeInvalidServer(subId: String) {
        val pinnedServers = MmkvManager.decodePinnedServers()
        val serverList = MmkvManager.decodeServerList(subId)
        val invalidServers = serverList.filter {
            if (pinnedServers.contains(it)) return@filter false
            val aff = MmkvManager.decodeServerAffiliationInfo(it)
            aff != null && aff.testDelayMillis < 0L
        }
        MmkvManager.removeServers(invalidServers, subId)
    }

    fun updateConfigViaSub(it: SubscriptionCache): SubscriptionUpdateResult {
        try {
            if (!it.subscription.enabled) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            if (TextUtils.isEmpty(it.guid)
                || TextUtils.isEmpty(it.subscription.remarks)
                || TextUtils.isEmpty(it.subscription.url)
            ) {
                return SubscriptionUpdateResult(skipCount = 1)
            }

            val url = HttpUtil.toIdnUrl(it.subscription.url)
            if (!Utils.isValidUrl(url)) {
                return SubscriptionUpdateResult(failureCount = 1)
            }
            if (!it.subscription.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return SubscriptionUpdateResult(failureCount = 1)
                }
            }
            val oldProfileNames = snapshotProfileNames(it.guid)

            LogUtil.i(AppConfig.TAG, url)
            val userAgent = it.subscription.userAgent
            val requestHeaders = it.subscription.requestHeaders
            val proxyUsername = SettingsManager.getSocksUsername()
            val proxyPassword = SettingsManager.getSocksPassword()
            val hwid = if (SettingsManager.isSendHwidEnabled()) {
                Settings.Secure.getString(
                    AngApplication.application.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            } else null

            var subscriptionResponse = try {
                val httpPort = SettingsManager.getHttpPort()
                HttpUtil.getUrlContentResponseWithUserAgent(
                    UrlContentRequest(
                        url = url,
                        userAgent = userAgent,
                        requestHeaders = requestHeaders,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword,
                        hwid = hwid
                    )
                )
            } catch (e: Exception) {
                LogUtil.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error", e)
                null
            }
            if (subscriptionResponse?.content.isNullOrEmpty()) {
                subscriptionResponse = try {
                    HttpUtil.getUrlContentResponseWithUserAgent(
                        UrlContentRequest(
                            url = url,
                            userAgent = userAgent,
                            requestHeaders = requestHeaders,
                            hwid = hwid
                        )
                    )
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Update subscription: Failed to get URL content with user agent", e)
                    null
                }
            }
            val configText = subscriptionResponse?.content.orEmpty()
            if (configText.isEmpty()) {
                return SubscriptionUpdateResult(failureCount = 1)
            }

            val count = parseConfigViaSub(configText, it.guid, false)
            if (count > 0) {
                val usage = SubscriptionUserinfoParser.parse(subscriptionResponse?.headers.orEmpty())
                it.subscription.bytesUsed = usage.bytesUsed
                it.subscription.bytesRemaining = usage.bytesRemaining
                it.subscription.expiresAt = usage.expiresAt
                it.subscription.lastUpdated = System.currentTimeMillis()
                MmkvManager.encodeSubscription(it.guid, it.subscription)
                LogUtil.i(AppConfig.TAG, "Subscription updated: ${it.subscription.remarks}, $count configs")

                val newProfileNames = snapshotProfileNames(it.guid)
                val subName = it.subscription.remarks

                var added = (newProfileNames - oldProfileNames)
                .map { name -> ProfileDiffEntry(subName, name) }
                val deleted = (oldProfileNames - newProfileNames)
                .map { name -> ProfileDiffEntry(subName, name) }

                if (added.isEmpty() && deleted.isEmpty()) {
                    added = newProfileNames.map { name -> ProfileDiffEntry(subName, name) }
                }

                return SubscriptionUpdateResult(
                    configCount = count,
                    successCount = 1,
                    addedProfiles = added,
                    deletedProfiles = deleted
                )
            } else {
                return SubscriptionUpdateResult(failureCount = 1)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return SubscriptionUpdateResult(failureCount = 1)
        }
    }

    private fun snapshotProfileNames(subId: String): Set<String> {
        return MmkvManager.decodeServerList(subId)
        .mapNotNull { guid -> MmkvManager.decodeServerConfig(guid)?.remarks }
        .filter { it.isNotBlank() }
        .toSet()
    }

    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseClashMetaConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseClashMetaConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseSIP008Config(Utils.decode(server), subid, append)
        }
        if (count <= 0) {
            count = parseSIP008Config(server, subid, append)
        }
        if (count <= 0) {
            count = parseBatchConfig(Utils.decode(server), subid, append)
        }
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid, append)
        }
        return count
    }

    private fun parseClashMetaConfig(server: String?, subid: String, append: Boolean): Int {
        if (server.isNullOrBlank() || !server.contains("proxies:")) return 0
        return try {
            val subItem = MmkvManager.decodeSubscription(subid)
            val configs = ClashMetaFmt.parse(server, subid)
                .filter { matchesSubscriptionFilters(it, subItem) }
                .map(::ParsedProfile)
            if (configs.isNotEmpty()) commitProfiles(configs, subid, append)
            configs.size
        } catch (e: ProfileStorageException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse ClashMeta YAML subscription", e)
            0
        }
    }

    private suspend fun importUrlAsSubscription(
        url: String,
        requestSubscriptionName: (suspend (String?, Set<String>) -> SubscriptionImportChoice?)?
    ): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.subscription.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val (remarks, tabIcon) = if (requestSubscriptionName == null) {
            (uri.fragment ?: "import sub") to null
        } else {
            val choice = requestSubscriptionName(uri.fragment, subscriptions.map { it.subscription.remarks }.toSet())
            ?: return 0
            val trimmedName = choice.name.trim().takeIf { it.isNotEmpty() } ?: return 0
            trimmedName to choice.tabIcon
        }
        if (MmkvManager.decodeSubscriptions().any { it.subscription.url == url }) return 0
        val subItem = SubscriptionItem()
        subItem.remarks = remarks
        subItem.url = url
        subItem.tabIcon = tabIcon
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }

    fun generateDescription(profile: ProfileItem): String {
        val server = profile.server
        val port = profile.serverPort
        if (server.isNullOrBlank() && port.isNullOrBlank()) return ""

        return "$server : ${port ?: ""}"
    }
}
