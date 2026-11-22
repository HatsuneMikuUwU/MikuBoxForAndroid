package io.nekohasekai.sagernet.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.viewModels
import com.yalantis.ucrop.UCrop
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.dp2pxf
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity
import io.nekohasekai.sagernet.utils.DPIController
import io.nekohasekai.sagernet.utils.Theme
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.settings.MikuConfirmDialog
import top.nkbe.mikubox.ui.page.settings.MikuUpdateDialog
import top.nkbe.mikubox.ui.page.settings.SettingsScreen
import top.nkbe.mikubox.ui.page.settings.SettingsScreenCallbacks
import top.nkbe.mikubox.ui.page.settings.SettingsViewModel
import top.nkbe.mikubox.ui.page.settings.UpdateCheckDialogState
import java.io.File

class SettingsFragment : ToolbarFragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private var pendingUpdateDialog by mutableStateOf<UpdateCheckDialogState?>(null)
    private var showLocationRationaleDialog by mutableStateOf(false)

    private val pickSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    try {
                        viewModel.saveStoredBanner(
                            context = requireContext(),
                            sourceUri = uri,
                            prefix = "sheet_banner_",
                            prefKey = "custom_sheet_banner_uri",
                            extension = ".gif",
                        )
                        snackbar(R.string.custom_banner_set).show()
                    } catch (e: Exception) {
                        Logs.e("Failed to save GIF banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                } else {
                    startCropSheetActivity(uri)
                }
            }
        }

    private val cropSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        viewModel.saveStoredBanner(
                            context = requireContext(),
                            sourceUri = cacheUri,
                            prefix = "sheet_banner_",
                            prefKey = "custom_sheet_banner_uri",
                            extension = ".jpg",
                        )
                        snackbar(R.string.custom_banner_set).show()
                    } catch (e: Exception) {
                        Logs.e("Failed to save sheet banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = result.data?.let { UCrop.getError(it) }
                cropError?.let { Logs.e("Sheet UCrop error", it) }
                snackbar("Crop failed: ${cropError?.message}").show()
            }
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            DataStore.showWeatherInfo = granted
            viewModel.refreshFromDataStore()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return mikuComposeView {
            val callbacks = SettingsScreenCallbacks(
                onOpenMenu = {
                    (activity as? NavigationHost)?.showNavigationSheet()
                        ?: activity?.onBackPressedDispatcher?.onBackPressed()
                },
                onOpenAppManager = {
                    startActivity(Intent(requireContext(), AppManagerActivity::class.java))
                },
                onOpenGlobalCustomConfig = {
                    startActivity(
                        Intent(requireContext(), ConfigEditActivity::class.java).apply {
                            putExtra("useConfigStore", "1")
                            putExtra("key", Key.GLOBAL_CUSTOM_CONFIG)
                        }
                    )
                },
                onSetAutoConnect = { enabled ->
                    DataStore.configurationStore.putBoolean(Key.PERSIST_ACROSS_REBOOT, enabled)
                },
                onSetServiceMode = { mode ->
                    DataStore.serviceMode = mode
                    if (DataStore.serviceState.started) SagerNet.stopService()
                },
                onSetTunImplementation = { impl ->
                    DataStore.tunImplementation = impl
                    needReload()
                },
                onSetMtu = { mtu ->
                    DataStore.mtu = mtu
                    needReload()
                },
                onSetSpeedInterval = { interval ->
                    DataStore.speedInterval = interval
                    needReload()
                },
                onSetProfileTrafficStatistics = { enabled ->
                    DataStore.profileTrafficStatistics = enabled
                },
                onSetShowDirectSpeed = { enabled ->
                    DataStore.showDirectSpeed = enabled
                    needReload()
                },
                onSetShowGroupInNotification = { enabled ->
                    DataStore.showGroupInNotification = enabled
                },
                onSetConfirmProfileDelete = { enabled ->
                    DataStore.confirmProfileDelete = enabled
                },
                onSetAlwaysShowAddress = { enabled ->
                    DataStore.alwaysShowAddress = enabled
                },
                onSetMeteredNetwork = { enabled ->
                    DataStore.meteredNetwork = enabled
                },
                onSetAcquireWakeLock = { enabled ->
                    DataStore.acquireWakeLock = enabled
                    needReload()
                },
                onSetHideFromRecentApps = { enabled ->
                    DataStore.hideFromRecentApps = enabled
                },
                onSetLogLevel = { level ->
                    DataStore.logLevel = level
                    needRestart()
                },
                onSetLogBufSize = { sizeKb ->
                    DataStore.logBufSize = sizeKb
                    needRestart()
                },
                onSetProxyApps = { enabled ->
                    DataStore.proxyApps = enabled
                    if (enabled) DataStore.dirty = true
                    startActivity(Intent(requireContext(), AppManagerActivity::class.java))
                },
                onSetBypassLan = { enabled ->
                    DataStore.bypassLan = enabled
                    needReload()
                },
                onSetBypassLanInCore = { enabled ->
                    DataStore.bypassLanInCore = enabled
                    needReload()
                },
                onSetTrafficSniffing = { value ->
                    DataStore.trafficSniffing = value
                    needReload()
                },
                onSetResolveDestination = { enabled ->
                    DataStore.resolveDestination = enabled
                    needReload()
                },
                onSetIpv6Mode = { mode ->
                    DataStore.ipv6Mode = mode
                    needReload()
                },
                onSetRulesProvider = { provider ->
                    DataStore.rulesProvider = provider
                },
                onSetRulesGeositeUrl = { url ->
                    DataStore.rulesGeositeUrl = url
                },
                onSetRulesGeoipUrl = { url ->
                    DataStore.rulesGeoipUrl = url
                },
                onSetRemoteDns = { dns ->
                    DataStore.remoteDns = dns
                    needReload()
                },
                onSetDomainStrategyRemote = { strategy ->
                    DataStore.configurationStore.putString("domain_strategy_for_remote", strategy)
                },
                onSetDirectDns = { dns ->
                    DataStore.directDns = dns
                    needReload()
                },
                onSetDomainStrategyServer = { strategy ->
                    DataStore.configurationStore.putString("domain_strategy_for_server", strategy)
                },
                onSetEnableDnsRouting = { enabled ->
                    DataStore.enableDnsRouting = enabled
                    needReload()
                },
                onSetEnableFakeDns = { enabled ->
                    DataStore.enableFakeDns = enabled
                    needReload()
                },
                onSetMixedPort = { port ->
                    DataStore.mixedPort = port
                    needReload()
                },
                onSetAppendHttpProxy = { enabled ->
                    DataStore.appendHttpProxy = enabled
                    needReload()
                },
                onSetAllowAccess = { enabled ->
                    DataStore.allowAccess = enabled
                    needReload()
                },
                onSetConnectionTestConfig = { url, concurrent ->
                    DataStore.connectionTestURL = url
                    DataStore.connectionTestConcurrent = concurrent
                },
                onSetEnableClashApi = { enabled ->
                    DataStore.enableClashAPI = enabled
                    needReload()
                },
                onSetNetworkChangeResetConnections = { enabled ->
                    DataStore.networkChangeResetConnections = enabled
                },
                onSetWakeResetConnections = { enabled ->
                    DataStore.wakeResetConnections = enabled
                },
                onSetGlobalAllowInsecure = { enabled ->
                    DataStore.globalAllowInsecure = enabled
                },
                onSetAllowInsecureOnRequest = { enabled ->
                    DataStore.allowInsecureOnRequest = enabled
                },
                onSetAppTlsVersion = { version ->
                    DataStore.appTLSVersion = version
                },
                onClearCache = {
                    viewModel.clearAppCache(
                        context = requireContext(),
                        onSuccess = {
                            Toast.makeText(
                                requireContext(),
                                R.string.clear_cache_success,
                                Toast.LENGTH_SHORT
                            ).show()
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (isAdded) needReload()
                            }, 500)
                        },
                        onError = { msg ->
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
                onCheckUpdate = {
                    snackbar(getString(R.string.check_update_checking)).show()
                    viewModel.checkForUpdate(
                        context = requireContext(),
                        onUpToDate = { msg -> snackbar(msg).show() },
                        onUpdateAvailable = { state -> pendingUpdateDialog = state },
                        onError = { msg -> snackbar(msg).show() },
                    )
                },
                onSetAppTheme = { themeId ->
                    DataStore.appTheme = themeId
                    if (DataStore.serviceState.started) SagerNet.reloadService()
                    val themeRes = Theme.getTheme(themeId)
                    requireActivity().apply {
                        setTheme(themeRes)
                        ActivityCompat.recreate(this)
                    }
                },
                onSetDynamicTheme = { isDynamic ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (isDynamic) {
                            if (DataStore.appTheme != Theme.DYNAMIC) {
                                DataStore.lastAppTheme = DataStore.appTheme
                            }
                            DataStore.appTheme = Theme.DYNAMIC
                        } else {
                            DataStore.appTheme =
                                DataStore.lastAppTheme.takeIf { it != Theme.DYNAMIC && it != 0 }
                                    ?: Theme.TEAL
                        }
                        Theme.apply(requireContext().applicationContext)
                        requireActivity().recreate()
                    }
                },
                onSetNightTheme = { mode ->
                    DataStore.nightTheme = mode
                    Theme.currentNightMode = mode
                    Theme.applyNightTheme()
                    if (!Theme.usingNightMode() && DataStore.trueBlackEnabled) {
                        DataStore.trueBlackEnabled = false
                    }
                },
                onSetTrueBlack = { enabled ->
                    DataStore.trueBlackEnabled = enabled
                    Theme.apply(requireContext().applicationContext)
                    requireActivity().recreate()
                },
                onSetBoldFont = { enabled ->
                    DataStore.boldFontEnabled = enabled
                    Theme.apply(requireContext().applicationContext)
                    requireActivity().recreate()
                },
                onSetFontScale = { scale ->
                    DataStore.fontSize = scale
                    requireActivity().recreate()
                },
                onApplyDpi = { dpi ->
                    val clamped = dpi.coerceIn(200, 500)
                    DataStore.dpiValue = clamped
                    DPIController.applyDpi(requireContext(), clamped)
                    requireActivity().recreate()
                },
                onResetDpi = {
                    DataStore.dpiValue = 0
                    val sysDpi = resources.displayMetrics.densityDpi
                    snackbar(getString(R.string.dpi_reset_to_default, sysDpi)).show()
                    requireActivity().recreate()
                },
                onSetLanguage = { localeTag ->
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(localeTag)
                    )
                    viewModel.refreshFromDataStore()
                },
                onSetSoundOnConnect = { enabled ->
                    DataStore.soundOnConnect = enabled
                },
                onSetShowSplashScreen = { enabled ->
                    DataStore.showSplashScreen = enabled
                },
                onSetShowWelcomeAnim = { enabled ->
                    DataStore.showWelcomeAnim = enabled
                },
                onSetBannerCharacter = { key ->
                    DataStore.bannerSettingsCharacter = key
                },
                onSetCustomProfileName = { name ->
                    DataStore.customProfileName = name
                },
                onSetShowBottomBar = { enabled ->
                    DataStore.showBottomBar = enabled
                },
                onSetBlurBottomStatus = { enabled ->
                    DataStore.blurBottomStatus = enabled
                },
                onSetBlurBottomBlobAnim = { enabled ->
                    DataStore.blurBottomBlobAnim = enabled
                },
                onSetBlurBottomRadius = { radius ->
                    DataStore.blurBottomRadius = radius
                },
                onSetBlurBottomAlpha = { alpha ->
                    DataStore.blurBottomAlpha = alpha
                },
                onSetConnectionTestWithIp = { enabled ->
                    DataStore.connectionTestWithIp = enabled
                },
                onSetShowIpInTwoLine = { enabled ->
                    DataStore.showIpInTwoLine = enabled
                },
                onSetShowFabTimer = { enabled ->
                    DataStore.showFabTimer = enabled
                },
                onSetFabStyle = { style ->
                    DataStore.fabStyle = style
                    requireActivity().apply {
                        finish()
                        startActivity(intent)
                    }
                },
                onSetDoubleColumnProfileList = { enabled ->
                    DataStore.doubleColumnProfileList = enabled
                },
                onSetShowQuickActions = { enabled ->
                    DataStore.showQuickActions = enabled
                },
                onSetShowSnowflakes = { enabled ->
                    DataStore.showSnowflakes = enabled
                },
                onSetDisableBottomSheet = { enabled ->
                    DataStore.disableBottomSheet = enabled
                },
                onSetDisableBottomSheetHome = { enabled ->
                    DataStore.disableBottomSheetHome = enabled
                },
                onSetDisableParticlesSheet = { enabled ->
                    DataStore.disableParticlesSheet = enabled
                },
                onPickSheetBanner = {
                    pickSheetBannerImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDeleteSheetBanner = {
                    viewModel.deleteStoredBanner(requireContext(), "custom_sheet_banner_uri")
                    snackbar(R.string.custom_banner_removed).show()
                },
                onToggleWeatherInfo = { enabled ->
                    if (enabled) {
                        val granted = ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            DataStore.showWeatherInfo = true
                        } else {
                            showLocationRationaleDialog = true
                        }
                    } else {
                        DataStore.showWeatherInfo = false
                    }
                },
                onSetManualWeatherEnabled = { enabled ->
                    DataStore.manualWeatherEnabled = enabled
                    if (!enabled) {
                        DataStore.manualWeatherCity = ""
                    }
                },
                onSetManualWeatherCity = { city ->
                    DataStore.manualWeatherCity = city
                },
                onSetShowWeatherCondition = { enabled ->
                    DataStore.showWeatherCondition = enabled
                },
                onSetHideWeatherCity = { enabled ->
                    DataStore.hideWeatherCity = enabled
                },
                onOpenUrl = { url ->
                    requireContext().launchCustomTab(url)
                },
            )

            SettingsScreen(
                viewModel = viewModel,
                callbacks = callbacks,
            )

            pendingUpdateDialog?.let { updateState ->
                MikuUpdateDialog(
                    state = updateState,
                    onUpdateNow = { url ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            snackbar(getString(R.string.error_open_link)).show()
                        }
                    },
                    onDismiss = { pendingUpdateDialog = null },
                )
            }

            if (showLocationRationaleDialog) {
                MikuConfirmDialog(
                    title = getString(R.string.permission_location_title),
                    message = getString(R.string.permission_location_message),
                    confirmText = getString(R.string.permission_grant),
                    dismissText = getString(R.string.permission_deny),
                    onConfirm = {
                        requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onDismiss = {
                        showLocationRationaleDialog = false
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFromDataStore()
    }

    private fun startCropSheetActivity(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_sheet_banner_temp.jpg"))
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val targetHeightPx = dp2pxf(150)

        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1920, 1080)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(false)
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropSheetBannerImage.launch(uCrop.getIntent(requireContext()))
    }

    private fun isGif(uri: Uri): Boolean {
        return try {
            requireContext().contentResolver.getType(uri)?.contains("gif", ignoreCase = true) == true
        } catch (_: Exception) {
            false
        }
    }
}
