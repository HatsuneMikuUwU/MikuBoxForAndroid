package com.miku.ray.handler

import android.content.Context
import android.util.Log
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVHandler
import com.tencent.mmkv.MMKVLogLevel
import com.tencent.mmkv.MMKVRecoverStrategic
import com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.miku.ray.AppConfig.PREF_IS_BOOTED
import com.miku.ray.AppConfig.PREF_ROUTING_RULESET
import com.miku.ray.AppConfig.TAG
import com.miku.ray.BuildConfig
import com.miku.ray.dto.entities.AssetUrlCache
import com.miku.ray.dto.entities.AssetUrlItem
import com.miku.ray.dto.entities.DailyTrafficInfo
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.dto.entities.ServerAffiliationInfo
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.dto.entities.WebDavConfig
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.Utils

internal class ProfileStorageException(message: String) : IllegalStateException(message)

object MmkvManager {

    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_ASSET = "ASSET"
    private const val ID_SETTING = "SETTING"
    private const val ID_DAILY_TRAFFIC = "DAILY_TRAFFIC"
    private const val KEY_DAILY_TRAFFIC_DATES = "DAILY_TRAFFIC_DATES"
    private const val KEY_TOTAL_TRAFFIC_UPLINK = "TOTAL_TRAFFIC_UPLINK_ALL_TIME"
    private const val KEY_TOTAL_TRAFFIC_DOWNLINK = "TOTAL_TRAFFIC_DOWNLINK_ALL_TIME"
    private const val DAILY_TRAFFIC_RETENTION_DAYS = 90
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_WEBDAV_CONFIG = "WEBDAV_CONFIG"
    private const val KEY_PINNED_SERVERS = "PINNED_SERVERS"

    private val recoveryHandler = object : MMKVHandler {
        override fun onMMKVCRCCheckFail(mmapID: String) =
        recoverFromStorageError(mmapID, "CRC check")

        override fun onMMKVFileLengthError(mmapID: String) =
        recoverFromStorageError(mmapID, "file length check")

        override fun wantLogRedirecting(): Boolean = false

        override fun mmkvLog(
            level: MMKVLogLevel,
            file: String,
            line: Int,
            function: String,
            message: String
        ) = Unit
    }

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val dailyTrafficStorage by lazy { MMKV.mmkvWithID(ID_DAILY_TRAFFIC, MMKV.MULTI_PROCESS_MODE) }

    fun initialize(context: Context) {
        val logLevel = if (BuildConfig.DEBUG) {
            MMKVLogLevel.LevelDebug
        } else {
            MMKVLogLevel.LevelInfo
        }
        MMKV.initialize(
            context,
            context.filesDir.resolve("mmkv").absolutePath,
            null,
            logLevel,
            recoveryHandler
        )
    }

    private fun recoverFromStorageError(mmapID: String, error: String): MMKVRecoverStrategic {
        Log.e(TAG, "MMKV $error failed for $mmapID; attempting data recovery")
        return MMKVRecoverStrategic.OnErrorRecover
    }

    private inline fun <T> withProfileIndexLock(block: () -> T): T {
        return synchronized(mainStorage) {
            mainStorage.lock()
            try {
                block()
            } finally {
                mainStorage.unlock()
            }
        }
    }

    private fun requireStorageWrite(success: Boolean, message: String) {
        if (!success) throw ProfileStorageException(message)
    }

    private fun removeProfilePayloads(guids: Collection<String>) {
        if (guids.isEmpty()) return
        guids.forEach { guid ->
            val subId = getSubscriptionId(decodeServerConfig(guid)?.subscriptionId)
            foldTrafficIntoGroupResidual(guid, subId)
        }
        val keys = guids.toTypedArray()
        profileFullStorage.removeValuesForKeys(keys)
        serverAffStorage.removeValuesForKeys(keys)
        serverRawStorage.removeValuesForKeys(keys)
    }

    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }

    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    fun setSelectServer(guid: String) {
        com.miku.ray.MikuProfiles.impl?.select(guid)
        mainStorage.encode(KEY_SELECTED_SERVER, guid)
    }

    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String) {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        mainStorage.encode(key, JsonUtil.toJson(serverList))
    }

    fun saveOriginServerList(subscriptionId: String) {
        val subId = getSubscriptionId(subscriptionId)
        val current = decodeServerList(subId)
        if (current.isNotEmpty()) {
            mainStorage.encode("${KEY_SUB_SERVER_PREFIX}ORIGIN_$subId", JsonUtil.toJson(current))
        }
    }

    fun restoreOriginServerList(subscriptionId: String): Boolean {
        val subId = getSubscriptionId(subscriptionId)
        val key = "${KEY_SUB_SERVER_PREFIX}ORIGIN_$subId"
        val json = mainStorage.decodeString(key) ?: return false
        val origin = JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: return false
        encodeServerList(origin, subId)
        mainStorage.remove(key)
        return true
    }

    fun removeFromOriginServerList(guids: Collection<String>, subscriptionId: String) {
        if (guids.isEmpty()) return
        val subId = getSubscriptionId(subscriptionId)
        val key = "${KEY_SUB_SERVER_PREFIX}ORIGIN_$subId"
        val json = mainStorage.decodeString(key) ?: return
        val origin = JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: return
        origin.removeAll(guids.toSet())
        if (origin.isEmpty()) {
            mainStorage.remove(key)
        } else {
            mainStorage.encode(key, JsonUtil.toJson(origin))
        }
    }

    fun hasOriginServerList(subscriptionId: String): Boolean {
        val subId = getSubscriptionId(subscriptionId)
        return mainStorage.containsKey("${KEY_SUB_SERVER_PREFIX}ORIGIN_$subId")
    }

    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val subId = getSubscriptionId(subscriptionId)
        val key = "$KEY_SUB_SERVER_PREFIX$subId"
        val json = mainStorage.decodeString(key)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()

        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }

        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }

        return allServers
    }

    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }

    fun encodeServerConfig(guid: String, config: ProfileItem): String {
        val key = guid.ifBlank { Utils.getUuid() }
        profileFullStorage.encode(key, JsonUtil.toJson(config))

        val subId = getSubscriptionId(config.subscriptionId)
        val serverList = decodeServerList(subId)

        if (!serverList.contains(key)) {
            serverList.add(0, key)
            encodeServerList(serverList, subId)
            if (getSelectServer().isNullOrBlank()) {
                mainStorage.encode(KEY_SELECTED_SERVER, key)
            }
        }

        return key
    }

    fun encodeProfileDirect(key: String, configJson: String) {
        profileFullStorage.encode(key, configJson)
    }

    internal fun saveServerProfiles(
        profiles: Map<String, ProfileItem>,
        rawConfigs: Map<String, String>,
        subscriptionId: String,
        append: Boolean,
    ) {
        if (profiles.isEmpty()) return

        withProfileIndexLock {
            val replacedServers = if (append) {
                emptyList()
            } else {
                decodeServerList(subscriptionId).toList()
            }

            val previousSelection = getSelectServer()
            val selectedProfile = if (!append &&
                previousSelection != null &&
                previousSelection in replacedServers
            ) {
                decodeServerConfig(previousSelection)
            } else {
                null
            }
            val replacementSelection = ProfileReplacement.findSelectedReplacement(
                profiles = profiles,
                currentSelection = previousSelection,
                selectedProfile = selectedProfile,
            )

            profiles.forEach { (guid, profile) ->
                requireStorageWrite(
                    profileFullStorage.encode(guid, JsonUtil.toJson(profile)),
                    "Failed to save profile payload",
                )
                rawConfigs[guid]?.let { raw ->
                    requireStorageWrite(
                        serverRawStorage.encode(guid, raw),
                        "Failed to save raw profile payload",
                    )
                }
            }

            val pinnedServers = decodePinnedServers()
            val pinnedReplacedServers = if (append) {
                emptyList()
            } else {
                replacedServers.filter { pinnedServers.contains(it) }
            }

            val serverList = if (append) {
                decodeServerList(subscriptionId)
            } else {
                mutableListOf()
            }
            val indexedServers = serverList.toHashSet()
            profiles.keys.forEach { guid ->
                if (indexedServers.add(guid)) {
                    serverList.add(0, guid)
                }
            }

            pinnedReplacedServers.forEach { guid ->
                if (indexedServers.add(guid)) {
                    serverList.add(guid)
                }
            }
            encodeServerList(serverList, subscriptionId)

            replacementSelection?.let { guid ->
                requireStorageWrite(
                    mainStorage.encode(KEY_SELECTED_SERVER, guid),
                    "Failed to update selected profile",
                )
            }

            if (replacedServers.isEmpty()) return@withProfileIndexLock

            val protectedServers = setOfNotNull(replacementSelection ?: previousSelection) + pinnedReplacedServers
            val removablePayloads = ProfileReplacement.findRemovablePayloads(
                replacedServers = replacedServers,
                replacementServers = profiles.keys,
                protectedServers = protectedServers,
            )
            removeProfilePayloads(removablePayloads)
        }
    }

    fun removeServer(guid: String) {
        com.miku.ray.MikuProfiles.impl?.remove(guid)
        if (guid.isBlank()) {
            return
        }

        val config = decodeServerConfig(guid)
        val subId = getSubscriptionId(config?.subscriptionId)
        foldTrafficIntoGroupResidual(guid, subId)

        val serverList = decodeServerList(subId)
        serverList.remove(guid)
        encodeServerList(serverList, subId)

        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        removeFromOriginServerList(listOf(guid), subId)
        profileFullStorage.remove(guid)
        serverAffStorage.remove(guid)
        serverRawStorage.remove(guid)
        unpinServer(guid)
    }

    fun removeServers(guids: List<String>, subscriptionId: String) {
        guids.forEach { com.miku.ray.MikuProfiles.impl?.remove(it) }
        if (guids.isEmpty()) return
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)
        if (serverList.removeAll(guids)) {
            encodeServerList(serverList, subId)
        }
        val pinnedServers = decodePinnedServers()
        if (pinnedServers.removeAll(guids.toSet())) {
            settingsStorage.encode(KEY_PINNED_SERVERS, pinnedServers)
        }

        val selectedServer = getSelectServer()
        guids.forEach { guid ->
            foldTrafficIntoGroupResidual(guid, subId)
            if (selectedServer == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
            serverRawStorage.remove(guid)
        }
        removeFromOriginServerList(guids, subId)
    }

    fun removeServerViaSubid(subscriptionId: String?) {
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)

        serverList.forEach { guid ->
            if (getSelectServer() == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
            serverRawStorage.remove(guid)
        }
        removeFromOriginServerList(serverList, subId)

        serverList.clear()
        encodeServerList(serverList, subId)
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    /**
     * Writes a server's lifetime totals.
     *
     * Added for this project: the totals live inside the same affiliation record
     * as the delay, but only the delay had an encoder — MikuRay's own traffic
     * controller wrote the totals from its core, and the core here is MikuBox's,
     * so the app needs a way in. The rows read them through
     * [getProfileTrafficString].
     */
    fun encodeServerTraffic(guid: String, uplinkTotal: Long, downlinkTotal: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        if (aff.uplinkTotal == uplinkTotal && aff.downlinkTotal == downlinkTotal) {
            return
        }
        aff.uplinkTotal = uplinkTotal
        aff.downlinkTotal = downlinkTotal
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    fun encodeServerCountryCode(guid: String, countryCode: String?) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.countryCode = countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }
  
    private fun groupTrafficUpKey(subId: String) = "grp_traffic_up_$subId"
    private fun groupTrafficDownKey(subId: String) = "grp_traffic_down_$subId"

    private fun foldTrafficIntoGroupResidual(guid: String, subId: String) {
        val aff = decodeServerAffiliationInfo(guid) ?: return
        if (aff.uplinkTotal == 0L && aff.downlinkTotal == 0L) return
        val upKey = groupTrafficUpKey(subId)
        val downKey = groupTrafficDownKey(subId)
        serverAffStorage.encode(upKey, serverAffStorage.decodeLong(upKey, 0L) + aff.uplinkTotal)
        serverAffStorage.encode(downKey, serverAffStorage.decodeLong(downKey, 0L) + aff.downlinkTotal)
    }

    fun addProfileTraffic(guid: String, uplink: Long, downlink: Long) {
        if (guid.isBlank() || (uplink == 0L && downlink == 0L)) return
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.uplinkTotal += uplink
        aff.downlinkTotal += downlink
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    fun getProfileTrafficString(guid: String): String? {
        if (guid.isBlank()) return null
        val aff = decodeServerAffiliationInfo(guid) ?: return null
        if (aff.uplinkTotal == 0L && aff.downlinkTotal == 0L) return null
        return "↑ ${formatTrafficBytes(aff.uplinkTotal)}  ↓ ${formatTrafficBytes(aff.downlinkTotal)}"
    }

    fun resetProfileTraffic(guid: String) {
        if (guid.isBlank()) return
        val aff = decodeServerAffiliationInfo(guid) ?: return
        aff.uplinkTotal = 0L
        aff.downlinkTotal = 0L
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    fun resetGroupTraffic(subscriptionId: String) {
        val subId = getSubscriptionId(subscriptionId)
        decodeServerList(subId).forEach { guid -> resetProfileTraffic(guid) }
        serverAffStorage.remove(groupTrafficUpKey(subId))
        serverAffStorage.remove(groupTrafficDownKey(subId))
    }

    fun getGroupTrafficString(subscriptionId: String): String? {
        val subId = getSubscriptionId(subscriptionId)
        var uplinkTotal = serverAffStorage.decodeLong(groupTrafficUpKey(subId), 0L)
        var downlinkTotal = serverAffStorage.decodeLong(groupTrafficDownKey(subId), 0L)
        decodeServerList(subId).forEach { guid ->
            decodeServerAffiliationInfo(guid)?.let { aff ->
                uplinkTotal += aff.uplinkTotal
                downlinkTotal += aff.downlinkTotal
            }
        }
        if (uplinkTotal == 0L && downlinkTotal == 0L) return null
        return formatTrafficBytes(uplinkTotal + downlinkTotal)
    }

    fun resetAllTraffic() {
        decodeAllServerList().forEach { guid -> resetProfileTraffic(guid) }
        clearDailyTrafficHistory()
    }

    fun getTotalTrafficString(): String {
        val (uplinkTotal, downlinkTotal) = getTotalTrafficDetail() ?: (0L to 0L)
        return formatTrafficBytes(uplinkTotal + downlinkTotal)
    }

    fun getTotalTrafficDetail(): Pair<Long, Long>? {
        val uplinkTotal = dailyTrafficStorage.decodeLong(KEY_TOTAL_TRAFFIC_UPLINK, 0L)
        val downlinkTotal = dailyTrafficStorage.decodeLong(KEY_TOTAL_TRAFFIC_DOWNLINK, 0L)
        if (uplinkTotal + downlinkTotal == 0L) return null
        return uplinkTotal to downlinkTotal
    }

    fun addTotalTrafficAllTime(uplink: Long, downlink: Long) {
        if (uplink == 0L && downlink == 0L) return
        val newUplinkTotal = dailyTrafficStorage.decodeLong(KEY_TOTAL_TRAFFIC_UPLINK, 0L) + uplink
        val newDownlinkTotal = dailyTrafficStorage.decodeLong(KEY_TOTAL_TRAFFIC_DOWNLINK, 0L) + downlink
        dailyTrafficStorage.encode(KEY_TOTAL_TRAFFIC_UPLINK, newUplinkTotal)
        dailyTrafficStorage.encode(KEY_TOTAL_TRAFFIC_DOWNLINK, newDownlinkTotal)
    }

    private fun clearTotalTrafficAllTime() {
        dailyTrafficStorage.remove(KEY_TOTAL_TRAFFIC_UPLINK)
        dailyTrafficStorage.remove(KEY_TOTAL_TRAFFIC_DOWNLINK)
    }

    private fun clearDailyTrafficHistory() {
        decodeDailyTrafficDates().forEach { dailyTrafficStorage.removeValueForKey(it) }
        dailyTrafficStorage.remove(KEY_DAILY_TRAFFIC_DATES)
    }

    fun clearTotalTrafficDataAndHistory() {
        clearTotalTrafficAllTime()
        clearDailyTrafficHistory()
    }

    private fun dailyTrafficDateKey(calendar: java.util.Calendar): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = calendar.timeZone
        return fmt.format(calendar.time)
    }

    private fun decodeDailyTrafficInfo(dateKey: String): DailyTrafficInfo? {
        val json = dailyTrafficStorage.decodeString(dateKey) ?: return null
        return JsonUtil.fromJsonSafe(json, DailyTrafficInfo::class.java)
    }

    private fun decodeDailyTrafficDates(): MutableList<String> {
        val json = dailyTrafficStorage.decodeString(KEY_DAILY_TRAFFIC_DATES) ?: return mutableListOf()
        return JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
    }

    private fun encodeDailyTrafficDates(dates: List<String>) {
        dailyTrafficStorage.encode(KEY_DAILY_TRAFFIC_DATES, JsonUtil.toJson(dates))
    }

    fun addDailyTraffic(uplink: Long, downlink: Long) {
        if (uplink == 0L && downlink == 0L) return
        val todayKey = dailyTrafficDateKey(java.util.Calendar.getInstance())
        val info = decodeDailyTrafficInfo(todayKey) ?: DailyTrafficInfo()
        info.uplinkTotal += uplink
        info.downlinkTotal += downlink
        dailyTrafficStorage.encode(todayKey, JsonUtil.toJson(info))

        val dates = decodeDailyTrafficDates()
        if (!dates.contains(todayKey)) {
            dates.add(todayKey)
            pruneOldDailyTraffic(dates)
        }
    }

    private fun pruneOldDailyTraffic(dates: MutableList<String>) {
        dates.sort()
        while (dates.size > DAILY_TRAFFIC_RETENTION_DAYS) {
            val oldest = dates.removeAt(0)
            dailyTrafficStorage.removeValueForKey(oldest)
        }
        encodeDailyTrafficDates(dates)
    }

    fun getDailyTrafficDetail(daysAgo: Int): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val info = decodeDailyTrafficInfo(dailyTrafficDateKey(cal)) ?: return 0L to 0L
        return info.uplinkTotal to info.downlinkTotal
    }

    fun getDailyTrafficHistory(days: Int): List<Triple<String, Long, Long>> {
        val cal = java.util.Calendar.getInstance()
        val result = mutableListOf<Triple<String, Long, Long>>()
        for (i in (days - 1) downTo 0) {
            val dayCal = cal.clone() as java.util.Calendar
            dayCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val key = dailyTrafficDateKey(dayCal)
            val info = decodeDailyTrafficInfo(key)
            result.add(Triple(key, info?.uplinkTotal ?: 0L, info?.downlinkTotal ?: 0L))
        }
        return result
    }

    fun getTodayTrafficDetail(): Pair<Long, Long> = getDailyTrafficDetail(0)

    fun getCurrentMonthTrafficDetail(): Pair<Long, Long> {
        val monthPrefix = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        var uplinkTotal = 0L
        var downlinkTotal = 0L
        decodeDailyTrafficDates().forEach { dateKey ->
            if (dateKey.startsWith(monthPrefix)) {
                decodeDailyTrafficInfo(dateKey)?.let { info ->
                    uplinkTotal += info.uplinkTotal
                    downlinkTotal += info.downlinkTotal
                }
            }
        }
        return uplinkTotal to downlinkTotal
    }

    fun formatTrafficBytesPublic(bytes: Long): String = formatTrafficBytes(bytes)

    private fun formatTrafficBytes(bytes: Long): String {
        if (bytes == 0L) return "0.00 KB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var i = 0
        while (size >= 1024 && i < units.size - 1) { size /= 1024; i++ }
        return String.format(java.util.Locale.getDefault(), "%.2f %s", size, units[i])
    }

    fun hasAnyTestDelayResults(): Boolean {
        return decodeAllServerList().any { guid ->
            decodeServerAffiliationInfo(guid)?.testDelayMillis?.let { it != 0L } ?: false
        }
    }

    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    fun hasAnyCountryCodeResults(): Boolean {
        return decodeAllServerList().any { guid ->
            !decodeServerAffiliationInfo(guid)?.countryCode.isNullOrBlank()
        }
    }

    fun clearAllCountryCodes(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.countryCode = null
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    fun removeAllServer(): Int {
        val pinnedServers = decodePinnedServers()
        val subsList = decodeSubsList().toMutableList()
        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            subsList.add(DEFAULT_SUBSCRIPTION_ID)
        }

        var removedCount = 0
        subsList.forEach { subId ->
            val serverList = decodeServerList(subId)
            val (kept, removed) = serverList.partition { pinnedServers.contains(it) }
            if (removed.isNotEmpty()) {
                removed.forEach { guid ->
                    if (getSelectServer() == guid) {
                        mainStorage.remove(KEY_SELECTED_SERVER)
                    }
                }
                removeProfilePayloads(removed)
                removeFromOriginServerList(removed, subId)
                removedCount += removed.size
                encodeServerList(kept.toMutableList(), subId)
            }
        }
        return removedCount
    }

    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            if (isServerPinned(guid)) {
                return 0
            }
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                    count++
                }
            }
        } else {
            val pinnedServers = decodePinnedServers()
            serverAffStorage.allKeys()?.forEach { key ->
                if (pinnedServers.contains(key)) {
                    return@forEach
                }
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                        count++
                    }
                }
            }
        }
        return count
    }

    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }

    internal fun removeOrphanedServerProfiles(): Int? = synchronized(mainStorage) {
        mainStorage.lock()
        try {
            val indexedServersBySubscription = mainStorage.allKeys().orEmpty()
            .asSequence()
            .filter { key -> key.startsWith(KEY_SUB_SERVER_PREFIX) }
            .associate { key ->
                val subscriptionId = key.removePrefix(KEY_SUB_SERVER_PREFIX)
                val json = mainStorage.decodeString(key)
                val serverIds = if (json.isNullOrBlank()) {
                    null
                } else {
                    JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toSet()
                }
                subscriptionId to serverIds
            }

            val profiles = profileFullStorage.allKeys().orEmpty().map { guid ->
                StoredProfileReference(
                    guid = guid,
                    subscriptionId = decodeServerConfig(guid)?.subscriptionId,
                )
            }
            val orphans = OrphanProfileCleaner.findOrphans(
                profiles = profiles,
                indexedServersBySubscription = indexedServersBySubscription,
                selectedServer = getSelectServer(),
            ) ?: return@synchronized null

            if (orphans.isNotEmpty()) {
                val keys = orphans.toTypedArray()
                keys.forEach { guid ->
                    val subId = getSubscriptionId(decodeServerConfig(guid)?.subscriptionId)
                    foldTrafficIntoGroupResidual(guid, subId)
                }
                profileFullStorage.removeValuesForKeys(keys)
                serverAffStorage.removeValuesForKeys(keys)
                serverRawStorage.removeValuesForKeys(keys)
            }
            orphans.size
        } finally {
            mainStorage.unlock()
        }
    }

    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()

        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)

        removeServerViaSubid(subid)

        val subId = getSubscriptionId(subid)
        serverAffStorage.remove(groupTrafficUpKey(subId))
        serverAffStorage.remove(groupTrafficDownKey(subId))
    }

    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, JsonUtil.toJson(subItem))

        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, JsonUtil.toJson(subsList))
    }

    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.distinct()?.toMutableList() ?: mutableListOf()
        }
    }

    fun decodeAssetUrls(): List<AssetUrlCache> {
        val assetUrlItems = mutableListOf<AssetUrlCache>()
        assetStorage.allKeys()?.forEach { key ->
            val json = assetStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java) ?: AssetUrlItem()
                assetUrlItems.add(AssetUrlCache(key, item))
            }
        }
        return assetUrlItems.sortedBy { it.assetUrl.addedTime }
    }

    fun removeAssetUrl(assetid: String) {
        assetStorage.remove(assetid)
    }

    fun encodeAsset(assetid: String, assetItem: AssetUrlItem) {
        val key = assetid.ifBlank { Utils.getUuid() }
        assetStorage.encode(key, JsonUtil.toJson(assetItem))
    }

    fun decodeAsset(assetid: String): AssetUrlItem? {
        val json = assetStorage.decodeString(assetid) ?: return null
        return JsonUtil.fromJsonSafe(json, AssetUrlItem::class.java)
    }

    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null

        val rulesetList = JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)
        ?.toMutableList()
        ?: return mutableListOf()
        val repaired = repairRoutingRulesetIds(rulesetList)
        if (repaired.second) {

            if (!settingsStorage.encode(PREF_ROUTING_RULESET, JsonUtil.toJson(repaired.first))) {
                return null
            }
        }
        return repaired.first
    }

    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty()) {
            encodeSettings(PREF_ROUTING_RULESET, "")
            return
        }
        val repaired = repairRoutingRulesetIds(rulesetList)
        encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(repaired.first))
    }

    private fun repairRoutingRulesetIds(
        rulesetList: MutableList<RulesetItem>
    ): Pair<MutableList<RulesetItem>, Boolean> {
        val usedIds = HashSet<String>(rulesetList.size)
        var changed = false
        rulesetList.forEach { item ->
            val currentId = item.id.trim()
            if (currentId.isEmpty() || !usedIds.add(currentId)) {
                var replacement: String
                do {
                    replacement = Utils.getUuid()
                } while (!usedIds.add(replacement))
                item.id = replacement
                changed = true
            } else if (item.id != currentId) {
                item.id = currentId
                changed = true
            }
        }
        return rulesetList to changed
    }

    fun encodeSettings(key: String, value: String?): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Int): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Long): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Float): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: Boolean): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun encodeSettings(key: String, value: MutableSet<String>): Boolean {
        return settingsStorage.encode(key, value)
    }

    fun decodeSettingsString(key: String): String? {
        return settingsStorage.decodeString(key)
    }

    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        return settingsStorage.decodeString(key, defaultValue)
    }

    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        return settingsStorage.decodeInt(key, defaultValue)
    }

    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    fun decodeSettingsBool(key: String): Boolean {
        return settingsStorage.decodeBool(key, false)
    }

    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        return settingsStorage.decodeBool(key, defaultValue)
    }

    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }

    fun decodePinnedServers(): MutableSet<String> {
        return settingsStorage.decodeStringSet(KEY_PINNED_SERVERS) ?: mutableSetOf()
    }

    fun isServerPinned(guid: String): Boolean {
        if (guid.isBlank()) return false
        return decodePinnedServers().contains(guid)
    }

    fun togglePinnedServer(guid: String): Boolean {
        if (guid.isBlank()) return false
        val pinnedServers = decodePinnedServers()
        val nowPinned = if (pinnedServers.contains(guid)) {
            pinnedServers.remove(guid)
            false
        } else {
            pinnedServers.add(guid)
            true
        }
        settingsStorage.encode(KEY_PINNED_SERVERS, pinnedServers)
        return nowPinned
    }

    private fun unpinServer(guid: String) {
        val pinnedServers = decodePinnedServers()
        if (pinnedServers.remove(guid)) {
            settingsStorage.encode(KEY_PINNED_SERVERS, pinnedServers)
        }
    }

    fun clearAllSettings() {
        settingsStorage.clearAll()
    }

    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }

    fun encodeWebDavConfig(config: WebDavConfig): Boolean {
        return mainStorage.encode(KEY_WEBDAV_CONFIG, JsonUtil.toJson(config))
    }

    fun decodeWebDavConfig(): WebDavConfig? {
        val json = mainStorage.decodeString(KEY_WEBDAV_CONFIG) ?: return null
        return JsonUtil.fromJsonSafe(json, WebDavConfig::class.java)
    }

}
