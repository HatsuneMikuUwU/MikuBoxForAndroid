package com.miku.ray.ui.main

import com.miku.ray.remixicon.R as RemixR
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.text.style.ClickableSpan
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.king.camera.scan.CameraScan
import com.miku.ray.AppConfig
import com.miku.ray.util.SearchBarChipMode
import com.miku.ray.BuildConfig
import com.miku.ray.R
import com.miku.ray.core.LauncherManager
import com.miku.ray.databinding.ActivityMainBinding
import com.miku.ray.databinding.ItemQrcodeBinding
import com.miku.ray.dto.entities.MikuRayExportPayload
import com.miku.ray.enums.EConfigType
import com.miku.ray.enums.PermissionType
import com.miku.ray.extension.delay
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toSpeedString
import com.miku.ray.extension.toastError
import com.miku.ray.extension.toastInfo
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MikuRayGroupFileManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.ui.about.AboutActivity
import com.miku.ray.ui.backup.BackupActivity
import com.miku.ray.ui.backup.BackupManager
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.bottomsheet.AddConfigBottomSheet
import com.miku.ray.ui.bottomsheet.MainMenuBottomSheet
import com.miku.ray.ui.bottomsheet.MoreMenuBottomSheet
import com.miku.ray.ui.bottomsheet.ShareConfigBottomSheet
import com.miku.ray.ui.logcat.LogcatActivity
import com.miku.ray.ui.preference.activity.SettingsActivity
import com.miku.ray.ui.routing.RoutingSettingActivity
import com.miku.ray.ui.scanner.QrCaptureActivity
import com.miku.ray.ui.server.ServerHysteria2Activity
import com.miku.ray.ui.server.ServerShadowsocksActivity
import com.miku.ray.ui.server.ServerSocksActivity
import com.miku.ray.ui.server.ServerTrojanActivity
import com.miku.ray.ui.server.ServerVlessActivity
import com.miku.ray.ui.server.ServerVmessActivity
import com.miku.ray.ui.server.ServerWireguardActivity
import com.miku.ray.ui.subscription.SubEditActivity
import com.miku.ray.ui.subscription.SubSettingActivity
import com.miku.ray.ui.weather.WeatherForecastActivity
import com.miku.ray.ui.weather.WeatherHelper
import com.miku.ray.util.BlurBottomStatusController
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MikuRayFileCrypto
import com.miku.ray.util.QRCodeDecoder
import com.miku.ray.util.SearchChipGradientController
import com.miku.ray.util.TestProgressDialogController
import com.miku.ray.util.ThemeShareManager
import com.miku.ray.util.Utils
import com.miku.ray.util.getColorAttr
import com.miku.ray.util.requestSubscriptionImportName
import com.miku.ray.util.showBlur
import com.miku.ray.util.showDeleteConfirmDialog
import com.miku.ray.util.showMikuRayExportPasswordDialog
import com.miku.ray.util.showMikuRayImportPasswordDialog
import com.miku.ray.util.showSubUpdateDiffDialog
import com.miku.ray.util.showTotalTrafficDetailDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : HelperBaseActivity(),
MainMenuBottomSheet.OnOptionClickListener,
AddConfigBottomSheet.OnAddConfigClickListener,
MoreMenuBottomSheet.OnMoreOptionClickListener,
ShareConfigBottomSheet.OnShareOptionClickListener {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    val mainViewModel: MainViewModel by viewModels()

    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private var isColdStart = true
    private var dualSwipeChipSelection = SearchBarChipMode.WEATHER
    private var pendingConnectionTest = false
    private var lastIpStateText: String = ""
    private var lastTrafficSpeedText: String = ""
    private var lastTestResultText: String = ""
    private var fabTimerJob: Job? = null

    private val urlTestProgressDialog: TestProgressDialogController by lazy {
        TestProgressDialogController(this, TestProgressDialogController.Mode.URL_TEST) { mainViewModel.cancelRealPingTest() }
    }

    private val countryCodeProgressDialog: TestProgressDialogController by lazy {
        TestProgressDialogController(this, TestProgressDialogController.Mode.COUNTRY_CODE) { mainViewModel.cancelCountryCodeTest() }
    }

    private val TAG_HOME_BANNER_DEFAULT = "DEFAULT_HOME_BANNER"
    private val TAG_HOME_BANNER_HIDDEN = "HIDDEN_HOME_BANNER"

    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            applyTabSelectedStyle(tab, true, tab.position, binding.tabGroup.tabCount)
        }

        override fun onTabUnselected(tab: TabLayout.Tab) {
            applyTabSelectedStyle(tab, false, tab.position, binding.tabGroup.tabCount)
        }

        override fun onTabReselected(tab: TabLayout.Tab) {}
    }

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startV2Ray()
    }

    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService()) {
            LauncherManager.restartService(this)
        }

        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }

    private val scanQrCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(CameraScan.SCAN_RESULT)?.let { importBatchConfig(it) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        // Back on the home screen backgrounds the app instead of leaving it.
        // With predictive back enabled in the manifest the system no longer
        // sends KEYCODE_BACK through onKeyDown, so the behaviour lives here;
        // onKeyDown keeps it for the legacy key dispatch and for BUTTON_B.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(false)
            }
        })
        showTestBuildInfoIfNeeded()

        hideLoading()
        window.statusBarColor = Color.TRANSPARENT

        binding.blurBottomStatus.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val status = binding.blurBottomStatus
            val margin = (status.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            binding.routingMode.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = margin + status.height + (8 * resources.displayMetrics.density).toInt()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val selectionVersion = binding.routingMode.selectionVersion
                    val state = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.miku.ray.MikuRouting.impl?.state()
                    }
                    if (selectionVersion == binding.routingMode.selectionVersion) {
                        state?.let { binding.routingMode.render(it) }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
        setupViewPager()
        setupListeners()
        setupInlineSearchView()
        setupSearchBarChipSwipe()
        setupGroupTab()
        setupViewModel()
        setupBannerHome()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsChangeManager.uiCustomizationChanged.collect {
                    refreshUiCustomizations()
                }
            }
        }

        SubscriptionUpdater.sync()
        syncWeatherBackgroundUpdates()

        mainViewModel.reloadServerList()
        refreshGroupTabTitles(true)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        maybeShowTrafficDetailFromIntent(intent)
        handleIncomingFileIntent(intent)

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeShowTrafficDetailFromIntent(intent)
        handleIncomingFileIntent(intent)
    }

    private fun handleIncomingFileIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        if (launchIntent.action != Intent.ACTION_VIEW) return
        val uri = launchIntent.data ?: return

        launchIntent.action = Intent.ACTION_MAIN
        launchIntent.data = null

        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
        when {
            fileName.endsWith(BackupManager.FILE_EXTENSION, ignoreCase = true) ->
                confirmBackupImportFromUri(uri, fileName)

            fileName.endsWith(ThemeShareManager.FILE_EXTENSION, ignoreCase = true) ->
                confirmThemeImportFromUri(uri, fileName)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.lastPathSegment
                else -> contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve display name for $uri", e)
            null
        }
    }

    private fun confirmBackupImportFromUri(uri: Uri, fileName: String) {
        MaterialAlertDialogBuilder(this)
        .setIcon(RemixR.drawable.rmx_device_save_3_line)
        .setTitle(R.string.backup_import_title)
        .setMessage(getString(R.string.backup_import_message, fileName))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            lifecycleScope.launch {
                showLoading()
                val result = withContext(Dispatchers.IO) {
                    BackupManager.importFrom(this@MainActivity, uri)
                }
                hideLoading()
                when (result) {
                    is BackupManager.ImportResult.Success -> {
                        SettingsChangeManager.makeRestartService()
                        SettingsChangeManager.makeSetupGroupTab()
                        SettingsChangeManager.makeRefreshDisplayPrefs()
                        SettingsManager.setNightMode()
                        snackbarSuccess(
                            getString(R.string.title_configuration_restore),
                            title = getString(R.string.title_alerter_success)
                        )
                        restartApplication()
                    }
                    is BackupManager.ImportResult.Error -> {
                        snackbarError(
                            result.message.ifBlank { getString(R.string.title_configuration_restore) },
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            }
        }
        .showBlur()
    }

    private fun confirmThemeImportFromUri(uri: Uri, fileName: String) {
        MaterialAlertDialogBuilder(this)
        .setIcon(RemixR.drawable.rmx_system_import_line)
        .setTitle(R.string.ui_theme_import_title)
        .setMessage(R.string.ui_theme_import_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            lifecycleScope.launch {
                showLoading()
                val result = withContext(Dispatchers.IO) {
                    ThemeShareManager.importFrom(this@MainActivity, uri)
                }
                hideLoading()
                when (result) {
                    is ThemeShareManager.ImportResult.Success -> {
                        SettingsChangeManager.makeRestartService()
                        SettingsChangeManager.makeSetupGroupTab()
                        SettingsChangeManager.makeRefreshDisplayPrefs()
                        SettingsManager.setNightMode()
                        restartApplication()
                    }
                    is ThemeShareManager.ImportResult.Error -> {
                        toastError(getString(R.string.ui_theme_import_failed, result.message))
                    }
                }
            }
        }
        .showBlur()
    }

    private fun restartApplication() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            recreate()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
        finishAffinity()
    }

    private fun maybeShowTrafficDetailFromIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        if (!launchIntent.getBooleanExtra(AppConfig.EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL, false)) return
        launchIntent.removeExtra(AppConfig.EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL)
        window.decorView.post { showTotalTrafficDetailDialog(this) }
    }

    private fun showTestBuildInfoIfNeeded() {
        if (BuildConfig.BUILD_TYPE != "releaseTesting") return
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DISMISS_TEST_BUILD_INFO, false)) return

        val channelLabel = getString(R.string.test_build_info_channel)
        val message = getString(R.string.test_build_info_message, channelLabel)
        val styledMessage = SpannableString(message)
        val channelStart = message.indexOf(channelLabel)
        if (channelStart >= 0) {
            styledMessage.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/uwuowoumuchannel")
                            )
                        )
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = getColorAttr("colorPrimary")
                        ds.isUnderlineText = false
                    }
                },
                channelStart,
                channelStart + channelLabel.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.test_build_info_title)
        .setIcon(RemixR.drawable.rmx_system_alarm_warning_line)
        .setMessage(styledMessage)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(R.string.test_build_info_dont_show_again) { _, _ ->
            MmkvManager.encodeSettings(AppConfig.PREF_DISMISS_TEST_BUILD_INFO, true)
        }
        .showBlur()

        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        com.miku.ray.MikuProfiles.impl?.let {
            lifecycleScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { it.sync() }
                mainViewModel.reloadServerList()
                refreshGroupTabTitles(true)
            }
        }

        refreshSearchBarChip()
        refreshIpStateText()
        updateSnowflakesVisibility()
        refreshUiCustomizations()

        if (SettingsChangeManager.consumeRefreshDisplayPrefs()) {
            refreshAllGroupListDisplays()
        }

        if (SettingsChangeManager.consumeSetupGroupTab()) {
            refreshGroupTabTitles()
        }

        mainViewModel.startListenBroadcast()
    }

    override fun onContentChanged() {
        super.onContentChanged()

        val root = findViewById<View>(R.id.main_content) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            view.updatePadding(
                top = 0,
                left = maxOf(systemBars.left, displayCutout.left),
                right = maxOf(systemBars.right, displayCutout.right),
                bottom = 0
            )

            val quickActionsEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_QUICK_ACTIONS, false)

            val bottomStatus = view.findViewById<View>(R.id.blur_bottom_status)
            val quickActions = view.findViewById<View>(R.id.layout_quick_actions)
            val routingMode = view.findViewById<View>(R.id.routing_mode)
            val routingBaseMargin = (routingMode?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

            val baseMarginBottom = (16 * resources.displayMetrics.density).toInt() + systemBars.bottom
            val statusGap = resources.getDimensionPixelSize(R.dimen.padding_spacing_dp8)

            fun applyBottomStatusMargin() {
                val shifted = quickActionsEnabled && quickActions?.isVisible == true
                val bottomStatusParams = bottomStatus?.layoutParams as? ViewGroup.MarginLayoutParams
                bottomStatusParams?.let {
                    it.bottomMargin = if (shifted) baseMarginBottom + quickActions.height + statusGap else baseMarginBottom
                    bottomStatus.layoutParams = it
                }
                // The routing bar rides above the status strip; when the quick
                // actions row pushes the strip up, the bar moves with it or the
                // row covers its lower clickable half.
                routingMode?.let { bar ->
                    (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                        it.bottomMargin = routingBaseMargin + if (shifted) quickActions.height + statusGap else 0
                        bar.layoutParams = it
                    }
                }
            }

            val quickActionsParams = quickActions?.layoutParams as? ViewGroup.MarginLayoutParams
            quickActionsParams?.let {
                it.bottomMargin = baseMarginBottom
                quickActions.layoutParams = it
            }

            if (quickActionsEnabled && quickActions != null) {
                quickActions.doOnLayout { applyBottomStatusMargin() }
            }
            applyBottomStatusMargin()

            val headerContent = view.findViewById<View>(R.id.header_content)
            headerContent?.updatePadding(top = systemBars.top)

            insets
        }
    }

    private fun updateQuickActionsVisibility() {
        binding.layoutQuickActions.visibility = if (
            MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_QUICK_ACTIONS, false)
        ) View.VISIBLE else View.GONE

        binding.mainContent.requestApplyInsets()
    }

    private fun updateSnowflakesVisibility() {
        binding.snowflakesView.configure(
            speed = MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_SNOWFLAKES_SPEED,
                AppConfig.SNOWFLAKES_SPEED_DEFAULT
            ),
            count = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_SNOWFLAKES_COUNT,
                AppConfig.SNOWFLAKES_COUNT_DEFAULT
            ),
            size = MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_SNOWFLAKES_SIZE,
                AppConfig.SNOWFLAKES_SIZE_DEFAULT
            ),
            opacity = MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_SNOWFLAKES_OPACITY,
                AppConfig.SNOWFLAKES_OPACITY_DEFAULT
            ),
            wind = MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_SNOWFLAKES_WIND,
                AppConfig.SNOWFLAKES_WIND_DEFAULT
            ),
            life = MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_SNOWFLAKES_LIFE,
                AppConfig.SNOWFLAKES_LIFE_DEFAULT
            )
        )
        binding.snowflakesView.visibility = if (
            MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_SNOWFLAKES, false)
        ) View.VISIBLE else View.GONE
    }

    private fun weatherLocationReady(): Boolean =
    WeatherHelper.hasCustomLocation() || WeatherHelper.hasLocationPermission(this)

    private fun syncWeatherBackgroundUpdates() {
        val weatherEnabled = SearchBarChipMode.current() in setOf(
            SearchBarChipMode.WEATHER,
            SearchBarChipMode.DUAL_SWIPE
        )
        val canRunInBackground = WeatherHelper.hasCustomLocation() || WeatherHelper.hasBackgroundLocationPermission(this)

        if (weatherEnabled && canRunInBackground) {
            WeatherHelper.scheduleBackgroundUpdates(this)
        } else if (!weatherEnabled) {
            WeatherHelper.cancelBackgroundUpdates(this)
        }
    }

    private fun refreshIpStateText() {
        val showRealtimeTraffic = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, false)

        val value = if (showRealtimeTraffic) {
            if (mainViewModel.isRunning.value && lastTrafficSpeedText.isNotEmpty()) {
                lastTrafficSpeedText
            } else {
                "↑ ${0L.toSpeedString()}  ↓ ${0L.toSpeedString()}"
            }
        } else {
            lastIpStateText.ifEmpty { getString(R.string.ip_unknown) }
        }
        binding.tvIpState.showValue(value, !showRealtimeTraffic)
    }


    private fun refreshAllGroupListDisplays() {
        for (i in groupPagerAdapter.groups.indices) {
            val itemId = groupPagerAdapter.getItemId(i)
            val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment
            fragment?.refreshDisplayPrefs()
        }
    }

    private fun setupSearchBarChipSwipe() {
        val swipeThreshold = 64 * resources.displayMetrics.density
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    binding.layoutWeatherChip.performClick()
                    return true
                }

                override fun onFling(
                    firstEvent: MotionEvent?,
                    lastEvent: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (firstEvent == null) return false
                    val distanceX = lastEvent.x - firstEvent.x
                    val distanceY = lastEvent.y - firstEvent.y
                    if (abs(distanceY) <= abs(distanceX) || abs(distanceY) < swipeThreshold) return false

                    dualSwipeChipSelection = if (dualSwipeChipSelection == SearchBarChipMode.WEATHER) {
                        SearchBarChipMode.TOTAL_TRAFFIC
                    } else {
                        SearchBarChipMode.WEATHER
                    }
                    SearchBarChipMode.saveDualSelection(dualSwipeChipSelection)
                    refreshSearchBarChip()
                    return true
                }
        })

        binding.layoutWeatherChip.setOnTouchListener { view, event ->
            if (SearchBarChipMode.current() == SearchBarChipMode.DUAL_SWIPE) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                gestureDetector.onTouchEvent(event)
                true
            } else {
                false
            }
        }

        // N13: the chip is 32dp tall — under the 48dp minimum touch target.
        // Keep the visual size and extend the hit area through a delegate on
        // the parent instead of inflating the layout.
        binding.layoutWeatherChip.doOnLayout { view ->
            val extra = (8 * resources.displayMetrics.density).toInt()
            val area = android.graphics.Rect()
            view.getHitRect(area)
            area.inset(-extra, -extra)
            (view.parent as? View)?.touchDelegate = android.view.TouchDelegate(area, view)
        }
    }

    private fun isWeatherChipSelected(): Boolean {
        val mode = SearchBarChipMode.current()
        return mode == SearchBarChipMode.WEATHER ||
        (mode == SearchBarChipMode.DUAL_SWIPE && dualSwipeChipSelection == SearchBarChipMode.WEATHER)
    }

    private fun isTotalTrafficChipSelected(): Boolean {
        val mode = SearchBarChipMode.current()
        return mode == SearchBarChipMode.TOTAL_TRAFFIC ||
        (mode == SearchBarChipMode.DUAL_SWIPE && dualSwipeChipSelection == SearchBarChipMode.TOTAL_TRAFFIC)
    }

    private fun refreshSearchBarChip() {
        val mode = SearchBarChipMode.current()
        if (mode == SearchBarChipMode.DUAL_SWIPE) {
            dualSwipeChipSelection = SearchBarChipMode.currentDualSelection()
        } else {
            dualSwipeChipSelection = SearchBarChipMode.WEATHER
        }
        val weatherEnabled = isWeatherChipSelected()
        val totalTrafficEnabled = isTotalTrafficChipSelected()

        SearchChipGradientController.applyState(this, binding)

        when {
            weatherEnabled -> {
                hideTotalTrafficChip()
                refreshWeatherChip()
            }
            totalTrafficEnabled -> {
                hideWeatherChipViews()
                refreshTotalTrafficChip()
            }
            else -> {
                binding.layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun hideWeatherChipViews() {
        binding.ivWeatherIcon.isVisible = false
        binding.tvWeatherTemp.isVisible = false
    }

    private fun hideTotalTrafficChip() {
        binding.ivTotalTrafficIcon.isVisible = false
        binding.tvTotalTraffic.isVisible = false
    }

    private fun refreshTrafficChipIfNeeded() {
        if (SearchBarChipMode.current() in setOf(
                SearchBarChipMode.TOTAL_TRAFFIC,
                SearchBarChipMode.DUAL_SWIPE
        )) {
            SearchChipGradientController.applyState(this, binding)
            if (isTotalTrafficChipSelected()) refreshTotalTrafficChip()
        }
    }

    private fun refreshTotalTrafficChip() {
        val totalTraffic = MmkvManager.getTotalTrafficString()

        binding.tvTotalTraffic.text = totalTraffic
        if (isTotalTrafficChipSelected()) {
            binding.ivTotalTrafficIcon.isVisible = true
            binding.tvTotalTraffic.isVisible = true
            binding.layoutWeatherChip.isVisible = true
        }
    }

    private fun refreshWeatherChip() {
        if (!isWeatherChipSelected()) {
            binding.layoutWeatherChip.isVisible = false
            return
        }

        val coldStart = isColdStart.also { isColdStart = false }

        if (weatherLocationReady()) {
            if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
        } else {
            checkAndRequestPermission(PermissionType.LOCATION) {
                if (coldStart) forceRefreshWeatherChip() else loadWeatherChip()
            }
        }
    }

    private fun forceRefreshWeatherChip() {
        if (!isWeatherChipSelected()) return

        if (!weatherLocationReady()) {
            checkAndRequestPermission(PermissionType.LOCATION) {
                forceRefreshWeatherChip()
            }
            return
        }

        binding.layoutWeatherChip.isVisible = true
        WeatherHelper.publishChipWeatherFromCache()

        val cachedNow = WeatherHelper.chipWeather.value
        if (cachedNow == null) {
            binding.ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        } else {
            applyWeatherToChip(cachedNow)
        }

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity, force = true)
            if (weather == null && WeatherHelper.chipWeather.value == null && isWeatherChipSelected()) {
                binding.layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun loadWeatherChip() {
        if (!isWeatherChipSelected()) return
        binding.layoutWeatherChip.isVisible = true

        val fresh = WeatherHelper.getCachedWeather()
        val stale = fresh ?: WeatherHelper.getCachedWeatherStale()

        WeatherHelper.publishChipWeatherFromCache()

        if (stale == null) {
            binding.ivWeatherIcon.setImageResource(RemixR.drawable.rmx_cloud_line)
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.text = getString(R.string.weather_loading)
            binding.tvWeatherTemp.isVisible = true
        } else {
            applyWeatherToChip(stale)
        }

        if (fresh != null) return

        lifecycleScope.launch {
            val weather = WeatherHelper.fetchCurrentWeather(this@MainActivity)
            if (weather == null && WeatherHelper.chipWeather.value == null && isWeatherChipSelected()) {
                binding.layoutWeatherChip.isVisible = false
            }
        }
    }

    private fun applyWeatherToChip(weather: WeatherHelper.WeatherResult) {
        binding.ivWeatherIcon.setImageResource(weather.iconRes)
        binding.tvWeatherTemp.text = weather.getTemperatureString(WeatherHelper.isCelsius())

        if (isWeatherChipSelected()) {
            binding.ivWeatherIcon.isVisible = true
            binding.tvWeatherTemp.isVisible = true
            binding.layoutWeatherChip.isVisible = true
        }
    }

    private fun setupBannerHome() {
        binding.headerImage.setLayerType(View.LAYER_TYPE_NONE, null)
        refreshBannerAndHeader()
    }

    private fun refreshUiCustomizations() {
        binding.routingMode.refreshTheme()
        refreshBannerAndHeader()
        BlurBottomStatusController.applyState(this, binding) { mainViewModel.onLayoutTestClicked() }
        updateQuickActionsVisibility()
        refreshFabExtendedState()
    }

    private fun refreshFabExtendedState() {
        if (!mainViewModel.isRunning.value) return
        if (isFabExtended()) startFabTimer() else stopFabTimer()
    }

    private fun refreshBannerAndHeader() {
        val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
        applyBannerVisibility(!disableBanner)
        applyBannerHeight()
        applyHeaderTopRowPadding()
        loadBannerImage()
    }

    private fun applyBannerHeight() {
        val heightDp = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        )
        val heightPx = (heightDp * resources.displayMetrics.density).toInt()

        val lp = binding.bannerHome.layoutParams
        lp.height = heightPx
        binding.bannerHome.layoutParams = lp
        binding.headerImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyBannerVisibility(show: Boolean) {
        binding.bannerHome.visibility = if (show) View.VISIBLE else View.GONE
        val topPad = if (show) (16 * resources.displayMetrics.density).toInt() else 0

        binding.headerTopRow.setPadding(
            binding.headerTopRow.paddingLeft,
            topPad,
            binding.headerTopRow.paddingRight,
            binding.headerTopRow.paddingBottom
        )
    }

    private fun applyHeaderTopRowPadding() {
        val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
        val paddingDp = if (!disableBanner) {
            MmkvManager.decodeSettingsInt(
                AppConfig.PREF_HEADER_TOP_ROW_PADDING,
                AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
            )
        } else 0

        val paddingPx = (paddingDp * resources.displayMetrics.density).toInt()

        binding.headerTopRow.setPadding(
            binding.headerTopRow.paddingLeft,
            paddingPx,
            binding.headerTopRow.paddingRight,
            binding.headerTopRow.paddingBottom
        )
    }

    private fun loadBannerImage() {
        if (isDestroyed || isFinishing) return
        val headerImage = binding.headerImage

        val disableBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
        if (disableBanner) {
            Glide.with(headerImage).clear(headerImage)
            headerImage.setImageDrawable(null)
            headerImage.tag = TAG_HOME_BANNER_HIDDEN
            return
        }

        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
        val targetTag = if (uriString.isNullOrBlank()) TAG_HOME_BANNER_DEFAULT else uriString

        if (headerImage.tag == targetTag) return

        if (!uriString.isNullOrBlank()) {
            val isGif = uriString.lowercase().endsWith(".gif")
            if (isGif) {
                Glide.with(this@MainActivity)
                .asGif()
                .load(Uri.parse(uriString))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .error(R.drawable.uwu_banner_home)
                .into(headerImage)
            } else {
                Glide.with(this@MainActivity)
                .load(Uri.parse(uriString))
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .error(R.drawable.uwu_banner_home)
                .into(headerImage)
            }
        } else {
            Glide.with(this@MainActivity).clear(headerImage)
            headerImage.setImageResource(R.drawable.uwu_banner_home)
        }

        headerImage.tag = targetTag
    }

    private fun setupViewPager() {
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.apply {
            adapter = groupPagerAdapter
            isUserInputEnabled = true
        }
    }

    private fun setupListeners() {
        binding.routingMode.onSelectionChanged = { mainViewModel.fetchCurrentIp(delayMs = 300L) }
        binding.fab.setOnClickListener { mainViewModel.onFabClicked() }
        binding.fab.shrink()

        binding.blurBottomStatus.setOnClickListener { mainViewModel.onLayoutTestClicked() }
        // N10: the whole card is one big tap-to-test button, but nothing said
        // so — to a screen reader it was an unlabeled clickable rectangle.
        // The node itself carries the action as its description (the audit's
        // strict "every clickable is labeled" bar), the click action is named,
        // and a disconnected tunnel makes the tap a no-op. The IP/state texts
        // inside stay separately focusable for their live values.
        binding.blurBottomStatus.contentDescription =
            getString(R.string.a11y_status_card_test_action)
        ViewCompat.setAccessibilityDelegate(binding.blurBottomStatus, object :
            androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val connected = mainViewModel.isRunning.value
                info.isEnabled = connected
                info.removeAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
                info.addAction(
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK,
                        getString(R.string.a11y_status_card_test_action),
                    )
                )
            }
        })

        binding.btnHome.setOnClickListener {
            MainMenuBottomSheet().show(supportFragmentManager, MainMenuBottomSheet.TAG)
        }

        binding.btnAddConfig.setOnClickListener {
            AddConfigBottomSheet().show(supportFragmentManager, AddConfigBottomSheet.TAG)
        }

        binding.btnMoreMenu.setOnClickListener {
            MoreMenuBottomSheet.newInstance(mainViewModel.subscriptionId)
            .show(supportFragmentManager, MoreMenuBottomSheet.TAG)
        }

        binding.btnAddSub.contentDescription = getString(R.string.btn_add_sub_description)
        binding.btnAddSub.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.menu_add_title)
                .setItems(arrayOf(
                    getString(R.string.menu_add_subscription),
                    getString(R.string.mihomo_group_create_title),
                    getString(R.string.mihomo_group_manage_title),
                )) { _, choice ->
                    if (choice == 0) requestActivityLauncher.launch(Intent(this, SubEditActivity::class.java).putExtra("groupId", mainViewModel.subscriptionId))
                    else if (choice == 1) com.miku.ray.ui.server.ProfileGroupPicker.create(this) {
                        SettingsChangeManager.makeSetupGroupTab()
                        setupGroupTab()
                    } else com.miku.ray.ui.server.ProfileGroupPicker.manage(this) {
                        SettingsChangeManager.makeSetupGroupTab()
                        setupGroupTab()
                    }
                }.show()
        }

        binding.btnQuickSubUpdate.setOnClickListener {
            importConfigViaSub()
        }

        binding.btnQuickCountryCode.setOnClickListener {
            val targetCount = mainViewModel.quickActionTargetGuids().count()
            if (targetCount == 0) {
                toastInfo(getString(R.string.empty_server_list_message))
                return@setOnClickListener
            }
            countryCodeProgressDialog.show(targetCount)
            mainViewModel.testAllCountryCodes()
        }

        binding.btnQuickTcping.setOnClickListener {
            val targetCount = mainViewModel.quickActionTargetGuids().count()
            if (targetCount == 0) {
                toastInfo(getString(R.string.empty_server_list_message))
                return@setOnClickListener
            }
            urlTestProgressDialog.show(targetCount, R.string.title_ping_all_server)
            mainViewModel.testAllRealPing(true)
        }

        binding.btnQuickRealPing.setOnClickListener {
            val targetCount = mainViewModel.quickActionTargetGuids().count()
            if (targetCount == 0) {
                toastInfo(getString(R.string.empty_server_list_message))
                return@setOnClickListener
            }
            urlTestProgressDialog.show(targetCount, R.string.title_real_ping_all_server)
            mainViewModel.testAllRealPing()
        }

        binding.layoutWeatherChip.setOnClickListener {
            when {
                isWeatherChipSelected() -> {
                    startActivity(Intent(this, WeatherForecastActivity::class.java))
                }
                isTotalTrafficChipSelected() -> {
                    showTotalTrafficDetailDialog(this) { refreshTotalTrafficChip() }
                }
            }
        }
    }

    private fun setupInlineSearchView() {
        binding.searchViewInline.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
        })

        binding.searchViewInline.setOnCloseListener {
            mainViewModel.filterConfig("")
            false
        }
    }

    override fun onOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.menu_sub_setting -> requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
            R.id.menu_routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.menu_settings -> requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.menu_logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.menu_backup_restore -> requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
            R.id.menu_about -> startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onAddConfigOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.import_qrcode -> importQRcode()
            R.id.import_clipboard -> importClipboard()
            R.id.import_local -> importConfigLocal()
            R.id.import_manually_policy_group -> importManually(EConfigType.POLICYGROUP.value)
            R.id.import_manually_proxy_chain -> importManually(EConfigType.PROXYCHAIN.value)
            R.id.import_manually_vmess -> importManually(EConfigType.VMESS.value)
            R.id.import_manually_vless -> importManually(EConfigType.VLESS.value)
            R.id.import_manually_ss -> importManually(EConfigType.SHADOWSOCKS.value)
            R.id.import_manually_socks -> importManually(EConfigType.SOCKS.value)
            R.id.import_manually_http -> importManually(EConfigType.HTTP.value)
            R.id.import_manually_trojan -> importManually(EConfigType.TROJAN.value)
            R.id.import_manually_wireguard -> importManually(EConfigType.WIREGUARD.value)
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
        }
    }

    override fun onMoreOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.export_all -> exportAll()
            R.id.export_group_file -> exportGroupAsFile()
            R.id.real_ping_all -> {
                val targetCount = mainViewModel.quickActionTargetGuids().count()
                if (targetCount == 0) {
                    toastInfo(getString(R.string.empty_server_list_message))
                    return
                }
                urlTestProgressDialog.show(targetCount, R.string.title_real_ping_all_server)
                mainViewModel.testAllRealPing()
            }
            R.id.country_code_all -> {
                val targetCount = mainViewModel.quickActionTargetGuids().count()
                if (targetCount == 0) {
                    toastInfo(getString(R.string.empty_server_list_message))
                    return
                }
                countryCodeProgressDialog.show(targetCount)
                mainViewModel.testAllCountryCodes()
            }
            R.id.tcping_all -> {
                val targetCount = mainViewModel.quickActionTargetGuids().count()
                if (targetCount == 0) {
                    toastInfo(getString(R.string.empty_server_list_message))
                    return
                }
                urlTestProgressDialog.show(targetCount, R.string.title_ping_all_server)
                mainViewModel.testAllRealPing(true)
            }
            R.id.service_restart -> LauncherManager.restartServiceOrStart(this, ::startV2Ray)
            R.id.action_scroll_to_selected -> locateSelectedServer()
            R.id.del_all_config -> delAllConfig()
            R.id.del_duplicate_config -> delDuplicateConfig()
            R.id.del_invalid_config -> delInvalidConfig()
            R.id.sub_update -> importConfigViaSub()
            R.id.clear_test_results -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_clear_test_results)
                .setIcon(RemixR.drawable.rmx_refresh_line)
                .setItems(options) { _, which ->
                    val msgRes: Int
                    val action: () -> Unit

                    when (which) {
                        0 -> {
                            msgRes = R.string.confirm_clear_test_results_group
                            action = {
                                mainViewModel.clearTestResultsForGroup()
                                refreshAllGroupListDisplays()
                            }
                        }
                        else -> {
                            msgRes = R.string.confirm_clear_test_results_all
                            action = {
                                mainViewModel.clearTestResults()
                                refreshAllGroupListDisplays()
                            }
                        }
                    }

                    showDeleteConfirmDialog(
                        context = this,
                        titleRes = R.string.title_clear_test_results,
                        messageRes = msgRes
                    ) { action() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
            }
            R.id.clear_country_codes -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_clear_country_codes)
                .setIcon(RemixR.drawable.rmx_refresh_line)
                .setItems(options) { _, which ->
                    val msgRes: Int
                    val action: () -> Unit

                    when (which) {
                        0 -> {
                            msgRes = R.string.confirm_clear_country_codes_group
                            action = {
                                mainViewModel.clearCountryCodesForGroup()
                                refreshAllGroupListDisplays()
                            }
                        }
                        else -> {
                            msgRes = R.string.confirm_clear_country_codes_all
                            action = {
                                mainViewModel.clearCountryCodes()
                                refreshAllGroupListDisplays()
                            }
                        }
                    }

                    showDeleteConfirmDialog(
                        context = this,
                        titleRes = R.string.title_clear_country_codes,
                        messageRes = msgRes
                    ) { action() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
            }
            R.id.reset_traffic -> {
                val options = arrayOf(
                    getString(R.string.reset_traffic_scope_profile),
                    getString(R.string.reset_traffic_scope_group, currentGroupDisplayName()),
                    getString(R.string.reset_traffic_scope_all)
                )

                MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_reset_traffic)
                .setIcon(RemixR.drawable.rmx_refresh_line)
                .setItems(options) { _, which ->
                    val msgRes: Int
                    val action: () -> Unit

                    when (which) {
                        0 -> {
                            msgRes = R.string.confirm_reset_traffic_profile
                            action = { mainViewModel.resetCurrentProfileTraffic() }
                        }
                        1 -> {
                            msgRes = R.string.confirm_reset_traffic_group
                            action = { mainViewModel.resetGroupTraffic() }
                        }
                        else -> {
                            msgRes = R.string.confirm_reset_traffic_all
                            action = {
                                mainViewModel.resetAllTraffic()
                                refreshAllGroupListDisplays()
                            }
                        }
                    }

                    showDeleteConfirmDialog(
                        context = this,
                        titleRes = R.string.title_reset_traffic,
                        messageRes = msgRes
                    ) { action() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
            }
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay -> {
                mainViewModel.reloadServerList()
            }
        }
    }

    private fun setupViewModel() {
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.updateGroupBadgeEvent.collect { refreshTabBadges() }
                }

                launch {
                    WeatherHelper.chipWeather.collect { weather ->
                        if (!isWeatherChipSelected()) return@collect
                        if (weather != null) {
                            applyWeatherToChip(weather)
                        }
                    }
                }

                launch {
                    mainViewModel.updateListItemEvent.collect {
                        refreshTabBadges()
                        refreshTrafficChipIfNeeded()
                    }
                }

                launch {
                    mainViewModel.updateGroupOrderEvent.collect {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                    }
                }

                launch {
                    mainViewModel.testResultText.collect { text ->
                        lastTestResultText = text
                        setTestState(text)
                    }
                }

                launch {
                    mainViewModel.testProgress.collect { info ->
                        if (info == null) {
                            urlTestProgressDialog.finish()
                        } else {
                            urlTestProgressDialog.update(info)
                        }
                    }
                }

                launch {
                    mainViewModel.countryCodeProgress.collect { info ->
                        if (info == null) {
                            countryCodeProgressDialog.finish()
                        } else {
                            countryCodeProgressDialog.update(info)
                        }
                    }
                }

                launch {
                    mainViewModel.ipResultText.collect { ip ->
                        // A probe that answers after the tunnel went down must
                        // not repaint the exit line as if it were current —
                        // the disconnected screen shows the unknown placeholder
                        // until the next connection probes again.
                        if (!mainViewModel.isRunning.value) return@collect
                        lastIpStateText = if (ip.isEmpty()) {
                            getString(R.string.ip_unknown)
                        } else {
                            getString(R.string.ip_connected, ip)
                        }
                        refreshIpStateText()
                    }
                }

                launch {
                    mainViewModel.trafficSpeedText.collect { speedText ->
                        lastTrafficSpeedText = speedText
                        refreshIpStateText()
                        refreshTrafficChipIfNeeded()
                    }
                }

                launch {
                    mainViewModel.isRunning.collect { running ->
                        applyRunningState(isRunning = running)
                        if (running && pendingConnectionTest) {
                            pendingConnectionTest = false
                            setTestState(getString(R.string.connection_test_testing))
                            mainViewModel.testCurrentServerRealPing()
                        }
                    }
                }

                launch {
                    mainViewModel.serviceRestartEvent.collect {
                        stopFabTimer()
                        pendingConnectionTest = true
                        lastTestResultText = ""
                        setTestState(getString(R.string.connection_test_testing))
                    }
                }

                launch {
                    mainViewModel.requestServiceStartEvent.collect { requestServiceStart() }
                }

                launch {
                    mainViewModel.requestLayoutTestUiEvent.collect {
                        setTestState(getString(R.string.connection_test_testing))
                    }
                }

                launch {
                    mainViewModel.alertEvent.collect { (isSuccess, message) ->
                        if (isSuccess) {
                            snackbarSuccess(message, title = getString(R.string.title_alerter_success))
                            mainViewModel.fetchCurrentIp(delayMs = 800L)
                            if (mainViewModel.isRunning.value) {
                                applyRunningState(isRunning = true)
                                if (pendingConnectionTest) {
                                    pendingConnectionTest = false
                                    setTestState(getString(R.string.connection_test_testing))
                                    mainViewModel.testCurrentServerRealPing()
                                }
                            }
                        } else {
                            snackbarError(message, title = getString(R.string.title_alerter_error))
                        }
                    }
                }

            }
        }

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setBadgeVisibility(badge: TextView, label: TextView, count: Int) {
        if (count > 0) {
            val limit = MmkvManager.decodeSettingsString(AppConfig.PREF_TAB_BADGE_LIMIT, "0")
                ?.toIntOrNull()
                ?: 0
            badge.text = if (limit > 0 && count > limit) "${limit}+" else count.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun setTabIcon(iconView: ImageView?, iconName: String?) {
        iconView ?: return
        if (iconName.isNullOrBlank()) {
            iconView.visibility = View.GONE
            return
        }

        val resId = resources.getIdentifier(iconName, "drawable", packageName)
        if (resId == 0) {
            iconView.visibility = View.GONE
            return
        }

        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    private fun applyTabSelectedStyle(
        tab: TabLayout.Tab?,
        selected: Boolean,
        position: Int = tab?.position ?: 0,
        tabCount: Int = binding.tabGroup.tabCount
    ) {
    }

    private fun setupGroupTab() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Reused group fragments need new contents as well as new tab metadata.
            mainViewModel.reloadServerList()
            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                val currentIds = (0 until binding.tabGroup.tabCount).map { binding.tabGroup.getTabAt(it)?.tag }
                val structureUnchanged = currentIds == groups.map { it.id } &&
                groupPagerAdapter.groups.map { it.icon } == groups.map { it.icon } &&
                groupPagerAdapter.groups.map { it.remarks } == groups.map { it.remarks }

                groupPagerAdapter.update(groups)
                binding.viewPager.offscreenPageLimit = groups.size.coerceAtLeast(1)

                if (structureUnchanged && binding.tabGroup.tabCount == groups.size) {
                    refreshTabBadges()
                    return@withContext
                }

                val selectedGroup = com.miku.ray.MikuProfiles.impl
                    ?.get(MmkvManager.getSelectServer().orEmpty())?.groupId
                val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }
                    .takeIf { it >= 0 }
                    ?: groups.indexOfFirst { it.id == selectedGroup }.takeIf { it >= 0 }
                    ?: 0

                tabMediator?.detach()

                tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
                    groupPagerAdapter.groups.getOrNull(position)?.let { group ->
                        tab.tag = group.id
                        val tabView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_tab_group, null)

                        val tabIcon = tabView.findViewById<ImageView>(R.id.tab_icon)
                        val tabLabel = tabView.findViewById<TextView>(R.id.tab_label)
                        val tabBadge = tabView.findViewById<TextView>(R.id.tab_badge)

                        tabLabel.text = group.remarks
                        setTabIcon(tabIcon, group.icon)
                        setBadgeVisibility(tabBadge, tabLabel, group.serverCount)

                        tab.customView = tabView
                        tab.view.setOnLongClickListener {
                            com.miku.ray.ui.server.ProfileGroupPicker.confirmDelete(this@MainActivity, com.miku.ray.MikuProfiles.Group(group.id, group.remarks)) {
                                SettingsChangeManager.makeSetupGroupTab()
                                setupGroupTab()
                            }
                            true
                        }
                    }
                }.also { it.attach() }

                binding.tabGroup.post {
                    for (i in 0 until binding.tabGroup.tabCount) {
                        val tab = binding.tabGroup.getTabAt(i)
                        applyTabSelectedStyle(tab, i == binding.tabGroup.selectedTabPosition, i, binding.tabGroup.tabCount)
                    }
                }

                binding.tabGroup.removeOnTabSelectedListener(tabSelectedListener)
                binding.tabGroup.addOnTabSelectedListener(tabSelectedListener)

                if (targetIndex >= 0) {
                    binding.viewPager.setCurrentItem(targetIndex, false)

                    binding.tabGroup.post {
                        binding.tabGroup.setScrollPosition(targetIndex, 0f, true)
                    }
                }

                val hasAnyGroup = groups.isNotEmpty()
                binding.layoutTabWrapper.isVisible = hasAnyGroup
                binding.tabGroup.isVisible = hasAnyGroup
                (binding.tabGroup.parent as? View)?.isVisible = hasAnyGroup
            }
        }
    }

    fun refreshGroupTabTitles(refreshAll: Boolean = false) {

        setupGroupTab()
    }

    private fun refreshTabBadges() {
        lifecycleScope.launch(Dispatchers.IO) {
            val groups = mainViewModel.getSubscriptions(this@MainActivity)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                for (i in groups.indices) {
                    val tab = binding.tabGroup.getTabAt(i) ?: continue
                    val tabBadge = tab.customView?.findViewById<TextView>(R.id.tab_badge) ?: continue
                    val tabLabel = tab.customView?.findViewById<TextView>(R.id.tab_label) ?: continue

                    val count = groups.getOrNull(i)?.serverCount ?: 0
                    setBadgeVisibility(tabBadge, tabLabel, count)
                }
            }
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        // A tap can land while the tunnel is already up but this screen has not
        // heard about it — the process being killed with the connection on is
        // the usual route: the sticky restart re-connects in the background and
        // the screen still reports Disconnected, so the tap used to dead-click.
        // Asking again lets the running service answer before anything starts.
        mainViewModel.startListenBroadcast()
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            // The mirror key can be stale right after an update or a restore,
            // before the home screen's resume-time re-sync has run; the app's
            // own profile store is the source of truth, so re-sync before
            // refusing to connect instead of dead-clicking the button.
            com.miku.ray.MikuProfiles.impl?.sync()
        }
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            snackbarError(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_error))
            applyRunningState(isRunning = false)
            return
        }

        // 37 is the release that introduced the local-network permission; the
        // constant itself is not in the SDK this app compiles against.
        if (Build.VERSION.SDK_INT >= 37 && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        LauncherManager.startService(this)
        // The service announces the outcome (success or failure) on its own;
        // until that lands, show progress instead of leaving a tap looking
        // dead while the core warms up.
        setTestState(getString(R.string.connection_pending))
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun isFabExtended(): Boolean =
    MmkvManager.decodeSettingsBool(AppConfig.PREF_FAB_EXTENDED, false)

    private fun startFabTimer() {
        if (!isFabExtended()) return
        updateFabTimerText()

        val density = resources.displayMetrics.density
        val paddingStartPx = (16 * density).toInt()
        val paddingTopPx = (12 * density).toInt()
        val paddingEndPx = (16 * density).toInt()
        val paddingBottomPx = (12 * density).toInt()
        val iconPaddingPx = (4 * density).toInt()
        val minimumWidthPx = (132 * density).toInt()

        binding.fab.setPaddingRelative(
            paddingStartPx,
            paddingTopPx,
            paddingEndPx,
            paddingBottomPx
        )

        binding.fab.minimumWidth = minimumWidthPx
        binding.fab.iconPadding = iconPaddingPx
        binding.fab.extend()

        if (fabTimerJob?.isActive == true) return
        fabTimerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000L)
                updateFabTimerText()
            }
        }
    }

    private fun stopFabTimer() {
        fabTimerJob?.cancel()
        fabTimerJob = null
        binding.fab.shrink()
        binding.fab.text = null
    }

    private fun updateFabTimerText() {
        val elapsed = com.miku.ray.MikuCoreBridge.connectionElapsedMillis() / 1000
        val h = elapsed / 3600
        val m = (elapsed % 3600) / 60
        val s = elapsed % 60

        binding.fab.text = "%02d:%02d:%02d".format(h, m, s)
    }

    private fun applyRunningState(isRunning: Boolean) {
        binding.fab.isEnabled = true
        binding.blurBottomStatus.isClickable = true
        binding.blurBottomStatus.isFocusable = true

        if (isRunning) {
            binding.fab.setIconResource(RemixR.drawable.rmx_media_stop_line)
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            startFabTimer()

            setTestState(lastTestResultText.ifEmpty { getString(R.string.connection_connected) })
        } else {
            binding.fab.setIconResource(RemixR.drawable.rmx_media_play_line)
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            stopFabTimer()

            setTestState(getString(R.string.connection_not_connected))
            lastTestResultText = ""
            lastTrafficSpeedText = ""
            lastIpStateText = getString(R.string.ip_unknown)
            refreshIpStateText()
            pendingConnectionTest = false
        }
    }

    private fun importManually(createConfigType: Int) {
        val targetActivity = when (EConfigType.fromInt(createConfigType)) {
            // Policy groups and proxy chains describe multi-profile structures the
            // per-protocol forms do not model; their cards keep opening the config
            // editor with a matching template.
            EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN, EConfigType.CUSTOM, null ->
                com.miku.ray.ui.server.ServerCustomConfigActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS, EConfigType.HTTP -> ServerSocksActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerVmessActivity::class.java
        }

        startActivity(
            Intent(this, targetActivity)
                .putExtra("createConfigType", createConfigType)
                .putExtra("groupId", mainViewModel.subscriptionId)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
        )
    }

    private fun importQRcode(): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_START_SCAN_IMMEDIATE)) {
            launchScan()
        } else {
            showQRCodeSelectionDialog()
        }
        return true
    }

    private fun showQRCodeSelectionDialog() {
        val options = arrayOf(
            getString(R.string.scan_code),
            getString(R.string.select_photo)
        )

        MaterialAlertDialogBuilder(this)
        .setTitle(R.string.menu_item_import_config_qrcode)
        .setIcon(RemixR.drawable.rmx_qr_code_line)
        .setItems(options) { _, which ->
            when (which) {
                0 -> launchScan()
                1 -> showQRFileChooser()
            }
        }
        .showBlur()
    }

    private fun launchScan() {
        scanQrCode.launch(Intent(this, QrCaptureActivity::class.java))
    }

    private fun showQRFileChooser() {
        launchFileChooser("image/*") { uri ->
            if (uri == null) return@launchFileChooser

            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    snackbarDefault(R.string.toast_decoding_failed, title = getString(R.string.title_alerter_info))
                } else {
                    importBatchConfig(text)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                snackbarDefault(R.string.toast_decoding_failed, title = getString(R.string.title_alerter_info))
            }
        }
    }

    private fun importClipboard(): Boolean {
        return try {
            Utils.getClipboard(this).let { importBatchConfig(it) }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            false
        }
    }

    private fun importBatchConfig(server: String?) {
        if (server.isNullOrEmpty()) return

        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    server,
                    mainViewModel.subscriptionId,
                    true,
                ) { suggested, existing ->
                    requestSubscriptionImportName(suggested, existing)
                }
                delay(500L)

                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            snackbarSuccess(
                                getString(R.string.title_import_config_count, count),
                                title = getString(R.string.title_alerter_success)
                            )
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }
                        countSub > 0 -> setupGroupTab()
                        else -> snackbarError(
                            getString(R.string.import_configuration),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.import_configuration),
                        title = getString(R.string.title_alerter_error)
                    )
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    private fun importConfigLocal(): Boolean {
        return try {
            showFileChooser()
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            false
        }
    }

    fun importConfigViaSub(): Boolean {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = mainViewModel.updateConfigViaSubAll()
                delay(500L)

                withContext(Dispatchers.Main) {
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 -> {
                            toastInfo(getString(R.string.title_update_subscription_no_subscription))
                        }
                        result.successCount > 0 && result.failureCount + result.skipCount == 0 -> {
                            toastSuccess(getString(R.string.title_update_config_count, result.configCount))
                        }
                        else -> {
                            toastInfo(
                                getString(
                                    R.string.title_update_subscription_result,
                                    result.configCount, result.successCount, result.failureCount, result.skipCount
                                )
                            )
                        }
                    }

                    if (result.configCount > 0) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                    }
                    if (result.addedProfiles.isNotEmpty() || result.deletedProfiles.isNotEmpty()) {
                        showSubUpdateDiffDialog(this@MainActivity, result)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to update config via subscription", e)
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.title_update_subscription_no_subscription),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ret = mainViewModel.exportAllServer()
                withContext(Dispatchers.Main) {
                    if (ret > 0) {
                        snackbarSuccess(
                            getString(R.string.title_export_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    } else {
                        snackbarError(
                            getString(R.string.action_export),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to export all configs", e)
                withContext(Dispatchers.Main) {
                    snackbarError(getString(R.string.action_export), title = getString(R.string.title_alerter_error))
                }
            } finally {
                withContext(Dispatchers.Main) { hideLoading() }
            }
        }
    }

    private fun currentGroupDisplayName(): String =
    mainViewModel.getSubscriptions(this)
    .firstOrNull { it.id == mainViewModel.subscriptionId }
    ?.remarks
    ?: getString(R.string.filter_config_all)

    private fun exportGroupAsFile() {
        val currentGroupName = currentGroupDisplayName()

        val payload = MikuRayGroupFileManager.buildGroupExportPayload(mainViewModel.subscriptionId, currentGroupName)
        if (payload == null) {
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
            return
        }

        exportPayloadAndShare(payload, currentGroupName)
    }

    private fun shareProfileAsFile(guid: String) {
        val payload = MikuRayGroupFileManager.buildProfileExportPayload(guid)
        if (payload == null) {
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
            return
        }

        exportPayloadAndShare(payload, payload.name)
    }

    private fun exportPayloadAndShare(payload: MikuRayExportPayload, fileNamePrefix: String) {
        showMikuRayExportPasswordDialog(this) { password ->
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = MikuRayGroupFileManager.encryptPayloadToFile(
                        this@MainActivity,
                        payload,
                        password,
                        fileNamePrefix
                    )
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        shareMikuRayFile(file)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to export .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_export_group_file),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            }
        }
    }

    private fun shareMikuRayFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.cache", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                    .setType("application/octet-stream")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, uri),
                    getString(R.string.title_configuration_share)
                )
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share .mikuray file", e)
            snackbarError(getString(R.string.title_export_group_file), title = getString(R.string.title_alerter_error))
        }
    }

    private fun importMikuRayFile(bytes: ByteArray) {
        showMikuRayImportPasswordDialog(this) { password ->
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val payload = MikuRayGroupFileManager.decryptPayloadFromFile(bytes, password)
                    val count = MikuRayGroupFileManager.importPayload(payload, mainViewModel.subscriptionId)

                    withContext(Dispatchers.Main) {
                        hideLoading()
                        if (count > 0) {
                            snackbarSuccess(
                                getString(R.string.title_import_mikuray_count, count),
                                title = getString(R.string.title_alerter_success)
                            )
                            if (payload.type == MikuRayExportPayload.TYPE_GROUP) {
                                setupGroupTab()
                            } else {
                                mainViewModel.reloadServerList()
                                refreshGroupTabTitles()
                            }
                        } else {
                            snackbarError(
                                getString(R.string.title_import_mikuray_error),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                } catch (e: MikuRayFileCrypto.MikuRayCryptoException) {
                    LogUtil.e(AppConfig.TAG, "Failed to decrypt .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_import_mikuray_wrong_password),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import .mikuray file", e)
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        snackbarError(
                            getString(R.string.title_import_mikuray_error),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                }
            }
        }
    }

    private fun delAllConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeAllServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(
                            getString(R.string.title_del_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove all configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_config_dialog_comfirm_message),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
            }
        }
    }

    private fun delDuplicateConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeDuplicateServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(
                            getString(R.string.title_del_duplicate_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove duplicate configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_config_dialog_comfirm_message),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
            }
        }
    }

    private fun delInvalidConfig() {
        showDeleteConfirmDialog(context = this, messageRes = R.string.del_invalid_config_comfirm) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val ret = mainViewModel.removeInvalidServer()
                    withContext(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        snackbarSuccess(
                            getString(R.string.title_del_config_count, ret),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove invalid configs", e)
                    withContext(Dispatchers.Main) {
                        snackbarError(
                            getString(R.string.del_invalid_config_comfirm),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) { hideLoading() }
                }
            }
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            uri?.let { readContentFromUri(it) }
        }
    }

    private fun readContentFromUri(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (MikuRayFileCrypto.isMikuRayFile(bytes)) {
                importMikuRayFile(bytes)
            } else {
                importBatchConfig(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            snackbarDefault(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_info))
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
        if (targetGroupIndex < 0) {
            snackbarDefault(getString(R.string.toast_server_not_found_in_group), title = getString(R.string.title_alerter_info))
            return
        }

        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            snackbarDefault(getString(R.string.toast_fragment_not_available), title = getString(R.string.title_alerter_info))
        }
    }

    fun showShareBottomSheet(guid: String, configType: Int) {
        ShareConfigBottomSheet.newInstance(guid, configType).show(supportFragmentManager, ShareConfigBottomSheet.TAG)
    }

    override fun onShareOptionClicked(optionId: Int, guid: String) {
        when (optionId) {
            R.id.share_qrcode -> {
                try {
                    val ivBinding = ItemQrcodeBinding.inflate(LayoutInflater.from(this))
                    ivBinding.ivQcode.setImageBitmap(AngConfigManager.share2QRCode(guid))
                    ivBinding.ivQcode.contentDescription = getString(R.string.qr_code_description)
                    MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_qr_code)
                    .setIcon(RemixR.drawable.rmx_qr_code_line)
                    .setView(ivBinding.root).showBlur()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error when sharing QR code", e)
                }
            }
            R.id.share_clipboard -> {
                if (AngConfigManager.share2Clipboard(this, guid) == 0) {
                    snackbarSuccess(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_success)
                    )
                } else {
                    snackbarError(
                        getString(R.string.menu_item_export_proxy_app),
                        title = getString(R.string.title_alerter_error)
                    )
                }
            }
            R.id.share_full_clipboard -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
                    withContext(Dispatchers.Main) {
                        if (result == 0) {
                            snackbarSuccess(
                                getString(R.string.menu_item_export_proxy_app),
                                title = getString(R.string.title_alerter_success)
                            )
                        } else {
                            snackbarError(
                                getString(R.string.menu_item_export_proxy_app),
                                title = getString(R.string.title_alerter_error)
                            )
                        }
                    }
                }
            }
            R.id.share_file -> shareProfileAsFile(guid)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        hideLoading()
        urlTestProgressDialog.dismiss()
        tabMediator?.detach()
        runCatching {
            Glide.with(applicationContext).clear(binding.headerImage)
            binding.headerImage.setImageDrawable(null)
            binding.headerImage.tag = null
        }

        super.onDestroy()
    }
}
