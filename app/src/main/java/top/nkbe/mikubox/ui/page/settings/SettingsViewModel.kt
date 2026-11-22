package top.nkbe.mikubox.ui.page.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceDataStore
import com.neko.widget.BannerSettingsCharacterImageView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.utils.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread

@Immutable
data class UpdateCheckDialogState(
    val remoteVersion: String,
    val localVersion: String,
    val downloadUrl: String,
)

@Immutable
data class SettingsUiState(
    // Hero & Tab
    val bannerCharacterKey: String = DataStore.bannerSettingsCharacter,
    @DrawableRes val bannerCharacterRes: Int =
        BannerSettingsCharacterImageView.resolveDrawable(DataStore.bannerSettingsCharacter),
    val customProfileName: String = DataStore.customProfileName,

    // General
    val persistAcrossReboot: Boolean = DataStore.configurationStore.getBoolean(Key.PERSIST_ACROSS_REBOOT, false),
    val serviceMode: String = DataStore.serviceMode,
    val tunImplementation: Int = DataStore.tunImplementation,
    val mtu: Int = DataStore.mtu,
    val speedInterval: Int = DataStore.speedInterval,
    val profileTrafficStatistics: Boolean = DataStore.profileTrafficStatistics,
    val showDirectSpeed: Boolean = DataStore.showDirectSpeed,
    val showGroupInNotification: Boolean = DataStore.showGroupInNotification,
    val confirmProfileDelete: Boolean = DataStore.confirmProfileDelete,
    val alwaysShowAddress: Boolean = DataStore.alwaysShowAddress,
    val meteredNetwork: Boolean = DataStore.meteredNetwork,
    val acquireWakeLock: Boolean = DataStore.acquireWakeLock,
    val hideFromRecentApps: Boolean = DataStore.hideFromRecentApps,
    val logLevel: Int = DataStore.logLevel,
    val logBufSize: Int = DataStore.logBufSize,
    val hasGlobalCustomConfig: Boolean = DataStore.globalCustomConfig.isNotBlank(),

    // Route
    val proxyApps: Boolean = DataStore.proxyApps,
    val bypassLan: Boolean = DataStore.bypassLan,
    val bypassLanInCore: Boolean = DataStore.bypassLanInCore,
    val trafficSniffing: Int = DataStore.trafficSniffing,
    val resolveDestination: Boolean = DataStore.resolveDestination,
    val ipv6Mode: Int = DataStore.ipv6Mode,
    val rulesProvider: Int = DataStore.rulesProvider,
    val rulesGeositeUrl: String = DataStore.rulesGeositeUrl,
    val rulesGeoipUrl: String = DataStore.rulesGeoipUrl,

    // DNS
    val remoteDns: String = DataStore.remoteDns,
    val domainStrategyForRemote: String =
        DataStore.configurationStore.getString("domain_strategy_for_remote") ?: "auto",
    val directDns: String = DataStore.directDns,
    val domainStrategyForServer: String =
        DataStore.configurationStore.getString("domain_strategy_for_server") ?: "auto",
    val enableDnsRouting: Boolean = DataStore.enableDnsRouting,
    val enableFakeDns: Boolean = DataStore.enableFakeDns,

    // Inbound
    val mixedPort: Int = DataStore.mixedPort,
    val appendHttpProxy: Boolean = DataStore.appendHttpProxy,
    val allowAccess: Boolean = DataStore.allowAccess,

    // Misc
    val connectionTestURL: String = DataStore.connectionTestURL,
    val connectionTestConcurrent: Int = DataStore.connectionTestConcurrent,
    val enableClashAPI: Boolean = DataStore.enableClashAPI,
    val networkChangeResetConnections: Boolean = DataStore.networkChangeResetConnections,
    val wakeResetConnections: Boolean = DataStore.wakeResetConnections,
    val globalAllowInsecure: Boolean = DataStore.globalAllowInsecure,
    val allowInsecureOnRequest: Boolean = DataStore.allowInsecureOnRequest,
    val appTLSVersion: String = DataStore.appTLSVersion.ifBlank { "1.2" },

    // Wardrobe: Theme & Display
    val appTheme: Int = DataStore.appTheme,
    val isDynamicTheme: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DataStore.appTheme == Theme.DYNAMIC,
    val supportsDynamicTheme: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val nightTheme: Int = DataStore.nightTheme,
    val usingNightMode: Boolean = Theme.usingNightMode(),
    val trueBlackEnabled: Boolean = DataStore.trueBlackEnabled,
    val boldFontEnabled: Boolean = DataStore.boldFontEnabled,
    val fontSize: Int = DataStore.fontSize,
    val dpiValue: Int = DataStore.dpiValue,
    val appLanguageTag: String = currentNormalizedAppLocale(),

    // Wardrobe: Home, BottomBar & Effects
    val soundOnConnect: Boolean = DataStore.soundOnConnect,
    val showSplashScreen: Boolean = DataStore.showSplashScreen,
    val showWelcomeAnim: Boolean = DataStore.showWelcomeAnim,
    val showBottomBar: Boolean = DataStore.showBottomBar,
    val blurBottomStatus: Boolean = DataStore.blurBottomStatus,
    val blurBottomBlobAnim: Boolean = DataStore.blurBottomBlobAnim,
    val blurBottomRadius: Int = DataStore.blurBottomRadius,
    val blurBottomAlpha: Int = DataStore.blurBottomAlpha,
    val connectionTestWithIp: Boolean = DataStore.connectionTestWithIp,
    val showIpInTwoLine: Boolean = DataStore.showIpInTwoLine,
    val showFabTimer: Boolean = DataStore.showFabTimer,
    val fabStyle: Int = DataStore.fabStyle,
    val doubleColumnProfileList: Boolean = DataStore.doubleColumnProfileList,
    val showQuickActions: Boolean = DataStore.showQuickActions,
    val showSnowflakes: Boolean = DataStore.showSnowflakes,
    val disableBottomSheet: Boolean = DataStore.disableBottomSheet,
    val disableBottomSheetHome: Boolean = DataStore.disableBottomSheetHome,
    val disableParticlesSheet: Boolean = DataStore.disableParticlesSheet,
    val customSheetBannerUri: String? =
        DataStore.configurationStore.getString("custom_sheet_banner_uri", null),
    val customProfileBannerUri: String? =
        DataStore.configurationStore.getString("custom_profile_banner_uri", null),
    val showWeatherInfo: Boolean = DataStore.showWeatherInfo,
    val manualWeatherEnabled: Boolean = DataStore.manualWeatherEnabled,
    val manualWeatherCity: String = DataStore.manualWeatherCity,
    val showWeatherCondition: Boolean = DataStore.showWeatherCondition,
    val hideWeatherCity: Boolean = DataStore.hideWeatherCity,

    // Transient UI states
    val isCheckingUpdate: Boolean = false,
) {
    companion object {
        fun fromDataStore(): SettingsUiState = SettingsUiState(
            bannerCharacterKey = DataStore.bannerSettingsCharacter,
            bannerCharacterRes = BannerSettingsCharacterImageView.resolveDrawable(
                DataStore.bannerSettingsCharacter
            ),
            customProfileName = DataStore.customProfileName,
            persistAcrossReboot = DataStore.configurationStore.getBoolean(
                Key.PERSIST_ACROSS_REBOOT,
                false
            ),
            serviceMode = DataStore.serviceMode,
            tunImplementation = DataStore.tunImplementation,
            mtu = DataStore.mtu,
            speedInterval = DataStore.speedInterval,
            profileTrafficStatistics = DataStore.profileTrafficStatistics,
            showDirectSpeed = DataStore.showDirectSpeed,
            showGroupInNotification = DataStore.showGroupInNotification,
            confirmProfileDelete = DataStore.confirmProfileDelete,
            alwaysShowAddress = DataStore.alwaysShowAddress,
            meteredNetwork = DataStore.meteredNetwork,
            acquireWakeLock = DataStore.acquireWakeLock,
            hideFromRecentApps = DataStore.hideFromRecentApps,
            logLevel = DataStore.logLevel,
            logBufSize = DataStore.logBufSize,
            hasGlobalCustomConfig = DataStore.globalCustomConfig.isNotBlank(),
            proxyApps = DataStore.proxyApps,
            bypassLan = DataStore.bypassLan,
            bypassLanInCore = DataStore.bypassLanInCore,
            trafficSniffing = DataStore.trafficSniffing,
            resolveDestination = DataStore.resolveDestination,
            ipv6Mode = DataStore.ipv6Mode,
            rulesProvider = DataStore.rulesProvider,
            rulesGeositeUrl = DataStore.rulesGeositeUrl,
            rulesGeoipUrl = DataStore.rulesGeoipUrl,
            remoteDns = DataStore.remoteDns,
            domainStrategyForRemote = DataStore.configurationStore
                .getString("domain_strategy_for_remote") ?: "auto",
            directDns = DataStore.directDns,
            domainStrategyForServer = DataStore.configurationStore
                .getString("domain_strategy_for_server") ?: "auto",
            enableDnsRouting = DataStore.enableDnsRouting,
            enableFakeDns = DataStore.enableFakeDns,
            mixedPort = DataStore.mixedPort,
            appendHttpProxy = DataStore.appendHttpProxy,
            allowAccess = DataStore.allowAccess,
            connectionTestURL = DataStore.connectionTestURL,
            connectionTestConcurrent = DataStore.connectionTestConcurrent,
            enableClashAPI = DataStore.enableClashAPI,
            networkChangeResetConnections = DataStore.networkChangeResetConnections,
            wakeResetConnections = DataStore.wakeResetConnections,
            globalAllowInsecure = DataStore.globalAllowInsecure,
            allowInsecureOnRequest = DataStore.allowInsecureOnRequest,
            appTLSVersion = DataStore.appTLSVersion.ifBlank { "1.2" },
            appTheme = DataStore.appTheme,
            isDynamicTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                DataStore.appTheme == Theme.DYNAMIC,
            supportsDynamicTheme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            nightTheme = DataStore.nightTheme,
            usingNightMode = Theme.usingNightMode(),
            trueBlackEnabled = DataStore.trueBlackEnabled,
            boldFontEnabled = DataStore.boldFontEnabled,
            fontSize = DataStore.fontSize,
            dpiValue = DataStore.dpiValue,
            appLanguageTag = currentNormalizedAppLocale(),
            soundOnConnect = DataStore.soundOnConnect,
            showSplashScreen = DataStore.showSplashScreen,
            showWelcomeAnim = DataStore.showWelcomeAnim,
            showBottomBar = DataStore.showBottomBar,
            blurBottomStatus = DataStore.blurBottomStatus,
            blurBottomBlobAnim = DataStore.blurBottomBlobAnim,
            blurBottomRadius = DataStore.blurBottomRadius,
            blurBottomAlpha = DataStore.blurBottomAlpha,
            connectionTestWithIp = DataStore.connectionTestWithIp,
            showIpInTwoLine = DataStore.showIpInTwoLine,
            showFabTimer = DataStore.showFabTimer,
            fabStyle = DataStore.fabStyle,
            doubleColumnProfileList = DataStore.doubleColumnProfileList,
            showQuickActions = DataStore.showQuickActions,
            showSnowflakes = DataStore.showSnowflakes,
            disableBottomSheet = DataStore.disableBottomSheet,
            disableBottomSheetHome = DataStore.disableBottomSheetHome,
            disableParticlesSheet = DataStore.disableParticlesSheet,
            customSheetBannerUri = DataStore.configurationStore.getString(
                "custom_sheet_banner_uri",
                null
            ),
            customProfileBannerUri = DataStore.configurationStore.getString(
                "custom_profile_banner_uri",
                null
            ),
            showWeatherInfo = DataStore.showWeatherInfo,
            manualWeatherEnabled = DataStore.manualWeatherEnabled,
            manualWeatherCity = DataStore.manualWeatherCity,
            showWeatherCondition = DataStore.showWeatherCondition,
            hideWeatherCity = DataStore.hideWeatherCity,
        )
    }
}

fun currentNormalizedAppLocale(): String {
    val raw = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when (raw) {
        "in" -> "id"
        "zh-Hant-TW" -> "zh-TW"
        "ru-RU" -> "ru"
        else -> raw
    }
}

class SettingsViewModel : ViewModel(), OnPreferenceDataStoreChangeListener {

    private val _uiState = MutableStateFlow(SettingsUiState.fromDataStore())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        DataStore.initGlobal()
        DataStore.configurationStore.registerChangeListener(this)
        refreshFromDataStore()
    }

    override fun onCleared() {
        DataStore.configurationStore.unregisterChangeListener(this)
        super.onCleared()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        refreshFromDataStore()
    }

    fun refreshFromDataStore() {
        _uiState.update { current ->
            SettingsUiState.fromDataStore().copy(isCheckingUpdate = current.isCheckingUpdate)
        }
    }

    fun checkForUpdate(
        context: Context,
        onUpToDate: (String) -> Unit,
        onUpdateAvailable: (UpdateCheckDialogState) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_uiState.value.isCheckingUpdate) return
        _uiState.update { it.copy(isCheckingUpdate = true) }

        val jsonUrl = "https://www.nkbe.top/mikubox/update.json"
        thread(name = "MikuUpdateCheck") {
            try {
                val jsonStr = URL(jsonUrl).readText()
                val jsonObject = JSONObject(jsonStr)
                val remoteVersion = jsonObject.optString("latestVersion", "")
                val remotePreVersion = jsonObject.optString("latestPreVersion", "")
                val downloadUrl = jsonObject.optString("url", "")

                val localVersion = BuildConfig.VERSION_NAME
                val localPreVersion = runCatching { BuildConfig.PRE_VERSION_NAME }.getOrDefault("") ?: ""

                val hasUpdate = if (remoteVersion.isNotEmpty() && remoteVersion != localVersion) {
                    true
                } else {
                    remotePreVersion.isNotEmpty() && remotePreVersion != localPreVersion
                }

                val displayRemote =
                    if (remotePreVersion.isNotEmpty()) "$remoteVersion-$remotePreVersion" else remoteVersion
                val displayLocal =
                    if (localPreVersion.isNotEmpty()) "$localVersion-$localPreVersion" else localVersion

                mainHandler.post {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    if (hasUpdate) {
                        onUpdateAvailable(
                            UpdateCheckDialogState(
                                remoteVersion = displayRemote,
                                localVersion = displayLocal,
                                downloadUrl = downloadUrl,
                            )
                        )
                    } else {
                        onUpToDate(context.getString(R.string.update_not_available_message, displayLocal))
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    onError(context.getString(R.string.check_update_failed, e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun clearAppCache(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val cacheDir = SagerNet.application.cacheDir
            clearDirFiles(cacheDir, skipFiles = setOf("neko.log"))
            val parentDir = cacheDir.parentFile
            val relativeCache = File(parentDir, "cache")
            if (relativeCache.exists() && relativeCache.isDirectory) {
                clearDirFiles(relativeCache)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(context.getString(R.string.clear_cache_failed, e.message ?: "Unknown error"))
        }
    }

    private fun clearDirFiles(dir: File, skipFiles: Set<String> = emptySet()): Boolean {
        if (dir.isDirectory) {
            val children = dir.list() ?: return true
            for (child in children) {
                val childFile = File(dir, child)
                if (child in skipFiles) continue
                val ok = clearDirFiles(childFile, skipFiles)
                if (!ok) return false
            }
        }
        return if (dir.name in skipFiles) true else dir.delete()
    }

    fun deleteStoredBanner(context: Context, prefKey: String) {
        val oldUri = DataStore.configurationStore.getString(prefKey, null)
        deleteOldFile(context, oldUri)
        DataStore.configurationStore.putString(prefKey, null)
        refreshFromDataStore()
    }

    fun saveStoredBanner(
        context: Context,
        sourceUri: Uri,
        prefix: String,
        prefKey: String,
        extension: String = ".jpg",
    ) {
        val oldUri = DataStore.configurationStore.getString(prefKey, null)
        deleteOldFile(context, oldUri)
        val savedUri = saveToCache(context, sourceUri, prefix, extension)
        DataStore.configurationStore.putString(prefKey, savedUri.toString())
        refreshFromDataStore()
    }

    private fun deleteOldFile(context: Context, uriString: String?) {
        if (uriString.isNullOrEmpty()) return
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) file.delete()
            } else {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        } catch (_: Exception) {
        }
    }

    @Throws(IOException::class)
    private fun saveToCache(
        context: Context,
        sourceCacheUri: Uri,
        fileNamePrefix: String,
        extension: String,
    ): Uri {
        val fileName = "${fileNamePrefix}${System.currentTimeMillis()}$extension"
        val destFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(sourceCacheUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        runCatching {
            if (sourceCacheUri.scheme == "file" && sourceCacheUri.path != null) {
                val tempFile = File(sourceCacheUri.path!!)
                if (tempFile.exists() && tempFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                    tempFile.delete()
                }
            }
        }
        return Uri.fromFile(destFile)
    }
}
