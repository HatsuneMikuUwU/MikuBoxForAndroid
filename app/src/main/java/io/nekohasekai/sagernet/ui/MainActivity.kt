package io.nekohasekai.sagernet.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan
import android.view.MenuItem
import android.os.RemoteException
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceDataStore
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.group.GroupInterfaceAdapter
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.showBlur
import io.nekohasekai.sagernet.widget.FabStyle
import moe.matsuri.nb4a.utils.Util
import io.nekohasekai.sagernet.ui.toolbar.NavMenuController
import top.nkbe.mikubox.ui.component.MikuInsets
import top.nkbe.mikubox.ui.component.clearDefaultSheetBackground
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.sheets.MikuNavigationSheetContent

class MainActivity : ThemedActivity(),
    SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener,
    NavigationHost { 

    lateinit var binding: LayoutMainBinding
    private lateinit var navMenuController: NavMenuController

    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutMainBinding.inflate(layoutInflater)
        updateNavBannerImage()
        
        navMenuController = NavMenuController(this)

        binding.fab.initProgress(binding.fabProgress)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomDockContainer) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = dp2px(16) + navInsets.bottom)
            view.post {
                val visibleDockHeight = if (binding.stats.isShown) view.height else 0
                MikuInsets.updateBottomDock(visibleDockHeight, navInsets.bottom)
            }
            insets
        }
        binding.bottomDockContainer.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if ((bottom - top) != (oldBottom - oldTop)) {
                val visibleDockHeight = if (binding.stats.isShown) (bottom - top) else 0
                MikuInsets.updateDockVisibilityHeight(visibleDockHeight)
            }
        }

        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_configuration)
        }
        
        onBackPressedDispatcher.addCallback {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else if (supportFragmentManager.findFragmentById(R.id.fragment_holder) is ConfigurationFragment) {
                moveTaskToBack(true)
            } else {
                displayFragmentWithId(R.id.nav_configuration, reverseAnim = true)
            }
        }

        binding.fab.setOnClickListener {
            if (DataStore.serviceState.canStop) {
                SagerNet.stopService()
            } else {
                ensureSelectedProfileThenConnect()
            }
        }

        binding.stats.applyMikuStyle(binding.coordinator) {
            if (DataStore.serviceState.connected) binding.stats.testConnection()
        }

        setupQuickActions()
        updateSnowflakesVisibility()

        setContentView(binding.root)
        
        setupNavigationView()
        
        updateDrawerLockMode()

        val lottieView: LottieAnimationView = binding.lottieWelcome

        if (DataStore.showWelcomeAnim) {
            lottieView.visibility = View.VISIBLE
            lottieView.playAnimation()

            lottieView.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lottieView.animate()
                        .alpha(0f)
                        .setDuration(900)
                        .withEndAction { 
                            lottieView.visibility = View.GONE
                            lottieView.alpha = 1f 
                        }
                        .start()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        } else {
            lottieView.visibility = View.GONE
        }

        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)

        if (intent?.action == Intent.ACTION_VIEW) {
            onNewIntent(intent)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val checkPermission = ContextCompat.checkSelfPermission(this@MainActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(POST_NOTIFICATIONS), 0
                )
            }
        }

        if (isPreview && !DataStore.hidePreviewDialog) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.PRE_VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.hide) { _, _ ->
                    DataStore.hidePreviewDialog = true
                }
                .showBlur()
        }
    }
    
    private fun updateDrawerLockMode() {
        if (::binding.isInitialized) {
            val lockMode = if (DataStore.disableBottomSheetHome) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
            binding.drawerLayout.setDrawerLockMode(lockMode)
        }
    }
    
    fun refreshNavMenu(clashApi: Boolean) {
        if (::binding.isInitialized) {
            binding.navView.menu.findItem(R.id.nav_traffic)?.isVisible = clashApi
        }
    }

    override fun showNavigationSheet() {
        if (::navMenuController.isInitialized) {
            navMenuController.showMenu()
        }
    }

    private fun setupNavigationView() {
        val navView = binding.navView
        
        refreshNavMenu(DataStore.enableClashAPI)
        
        applyFontToNavigation()

        val dimAlpha = if (Build.VERSION.SDK_INT >= 31) {
            77
        } else {
            153
        }
        val dimColor = android.graphics.Color.argb(dimAlpha, 0, 0, 0)
        binding.drawerLayout.setScrimColor(dimColor)

        if (Build.VERSION.SDK_INT >= 31) {
            binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    super.onDrawerSlide(drawerView, slideOffset)
                    val radius = slideOffset * 30f
                    try {
                        if (radius > 1f) {
                            binding.coordinator.setRenderEffect(
                                android.graphics.RenderEffect.createBlurEffect(
                                    radius,
                                    radius,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                            )
                        } else {
                            binding.coordinator.setRenderEffect(null)
                        }
                    } catch (_: Throwable) {
                    }
                }

                override fun onDrawerClosed(drawerView: View) {
                    super.onDrawerClosed(drawerView)
                    try {
                        binding.coordinator.setRenderEffect(null)
                    } catch (_: Throwable) {
                    }
                }
            })
        }

        navView.setNavigationItemSelectedListener { item ->
            displayFragmentWithId(item.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
    
    private fun applyFontToNavigation() {
        val navView = binding.navView
        val menu = navView.menu
        val appFont = DataStore.appFont

        val typeface = getCustomTypeface(this, appFont)

        if (typeface != null) {
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                val subMenu = menuItem.subMenu
                
                applyFontToMenuItem(menuItem, typeface)

                if (subMenu != null && subMenu.size() > 0) {
                    for (j in 0 until subMenu.size()) {
                        val subMenuItem = subMenu.getItem(j)
                        applyFontToMenuItem(subMenuItem, typeface)
                    }
                }
            }
        }
    }
    
    private fun applyFontToMenuItem(menuItem: MenuItem, typeface: Typeface) {
        val title = menuItem.title.toString()
        val spannableString = SpannableString(title)
        
        spannableString.setSpan(
            CustomTypefaceSpan(typeface),
            0,
            spannableString.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        menuItem.title = spannableString
    }
    
    fun showOriginalNavigationSheet() {
        val dialog = BottomSheetDialog(this)
        Theme.applyWindowBlur(dialog.window)

        val currentNavId = when (supportFragmentManager.findFragmentById(R.id.fragment_holder)) {
            is GroupFragment -> R.id.nav_group
            is RouteFragment -> R.id.nav_route
            is SettingsFragment -> R.id.nav_settings
            is WebviewFragment -> R.id.nav_traffic
            is ToolsFragment -> R.id.nav_tools
            is LogcatFragment -> R.id.nav_logcat
            is AboutFragment -> R.id.nav_about
            else -> R.id.nav_configuration
        }

        val composeView = mikuComposeView {
            MikuNavigationSheetContent(
                selectedNavId = currentNavId,
                showTraffic = DataStore.enableClashAPI,
                onSelectNav = { id ->
                    displayFragmentWithId(id)
                    dialog.dismiss()
                },
            )
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.clearDefaultSheetBackground()
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        dialog.show()
    }

    @SuppressLint("CommitTransaction")
    fun displayFragment(fragment: ToolbarFragment, reverseAnim: Boolean = false) {
        if (fragment is ConfigurationFragment) {
            binding.stats.allowShow = true
            binding.stats.performShow()
            binding.fab.show()
            updateQuickActionsVisibility(isHome = true)
            binding.bottomDockContainer.post {
                MikuInsets.updateDockVisibilityHeight(binding.bottomDockContainer.height)
            }
        } else if (!DataStore.showBottomBar) {
            binding.stats.allowShow = false
            binding.stats.performHide()
            binding.fab.hide()
            updateQuickActionsVisibility(isHome = false)
            MikuInsets.updateDockVisibilityHeight(0)
        } else {
            updateQuickActionsVisibility(isHome = false)
            binding.bottomDockContainer.post {
                val h = if (binding.stats.isShown) binding.bottomDockContainer.height else 0
                MikuInsets.updateDockVisibilityHeight(h)
            }
        }

        val transaction = supportFragmentManager.beginTransaction()

        if (reverseAnim) {
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        transaction.replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
            
        val id = when(fragment) {
            is ConfigurationFragment -> R.id.nav_configuration
            is GroupFragment -> R.id.nav_group
            is RouteFragment -> R.id.nav_route
            is SettingsFragment -> R.id.nav_settings
            is WebviewFragment -> R.id.nav_traffic
            is ToolsFragment -> R.id.nav_tools
            is LogcatFragment -> R.id.nav_logcat
            is AboutFragment -> R.id.nav_about
            else -> null
        }
        if (id != null) {
            binding.navView.setCheckedItem(id)
        }
    }

    fun displayFragmentWithId(@IdRes id: Int, reverseAnim: Boolean = false): Boolean {
        when (id) {
            R.id.nav_configuration -> displayFragment(ConfigurationFragment(), reverseAnim)
            R.id.nav_group -> displayFragment(GroupFragment()) 
            R.id.nav_route -> displayFragment(RouteFragment())
            R.id.nav_settings -> displayFragment(SettingsFragment())
            R.id.nav_traffic -> displayFragment(WebviewFragment())
            R.id.nav_tools -> displayFragment(ToolsFragment())
            R.id.nav_logcat -> displayFragment(LogcatFragment())
            R.id.nav_about -> displayFragment(AboutFragment())
            else -> return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        MessageStore.setCurrentActivity(this)
        if (DataStore.hideFromRecentApps) {
            applyHideFromRecentApps(DataStore.hideFromRecentApps)
        }
    }

    fun applyHideFromRecentApps(hide: Boolean) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                val task = tasks[0]
                task.setExcludeFromRecents(hide)
            }
        } catch (e: Exception) {
            Logs.w("Failed to set excludeFromRecents: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        runOnDefaultDispatcher {
            if (uri.scheme == "sn" && uri.host == "subscription" || uri.scheme == "clash") {
                importSubscription(uri)
            } else {
                importProfile(uri)
            }
        }
    }

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error("not started")
        }
        return connection.service!!.urlTest()
    }

    suspend fun importSubscription(uri: Uri) {
        val group: ProxyGroup
        val url = uri.getQueryParameter("url")
        if (!url.isNullOrBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()
            group.subscription = subscription
            subscription.link = url
            group.name = uri.getQueryParameter("name")
        } else {
            val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply { export = true }, Util.zlibDecompress(Util.b64Decode(data))
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    alert(e.readableMessage).show()
                }
                return
            }
        }

        val name = group.name.takeIf { !it.isNullOrBlank() } ?: group.subscription?.link
        ?: group.subscription?.token
        if (name.isNullOrBlank()) return

        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: ("Subscription #" + System.currentTimeMillis())

        onMainDispatcher {
            displayFragmentWithId(R.id.nav_group)
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.subscription_import)
                .setMessage(getString(R.string.subscription_import_message, name))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportSubscription(group)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
        }
    }

    private fun ensureSelectedProfileThenConnect() {
        runOnDefaultDispatcher {
            val dao = io.nekohasekai.sagernet.database.SagerDatabase.proxyDao
            var selected = DataStore.selectedProxy.takeIf { it > 0L }?.let { dao.getById(it) }
            if (selected == null) {
                val currentGroupProxies = dao.getByGroup(DataStore.selectedGroup)
                selected = currentGroupProxies.firstOrNull()
                if (selected == null) {
                    val allGroups = io.nekohasekai.sagernet.database.SagerDatabase.groupDao.allGroups()
                    for (grp in allGroups) {
                        val list = dao.getByGroup(grp.id)
                        if (list.isNotEmpty()) {
                            selected = list.first()
                            DataStore.selectedGroup = grp.id
                            break
                        }
                    }
                }
                if (selected != null) {
                    DataStore.selectedProxy = selected.id
                }
            }
            onMainDispatcher {
                if (selected != null) {
                    connect.launch(null)
                } else {
                    snackbar(R.string.profile_empty).show()
                }
            }
        }
    }

    private suspend fun finishImportSubscription(subscription: ProxyGroup) {
        GroupManager.createGroup(subscription)
        val ok = GroupUpdater.executeUpdate(subscription, true)
        if (ok) {
            val dao = io.nekohasekai.sagernet.database.SagerDatabase.proxyDao
            val imported = dao.getByGroup(subscription.id)
            if (imported.isNotEmpty()) {
                DataStore.selectedGroup = subscription.id
                if (DataStore.selectedProxy == 0L || dao.getById(DataStore.selectedProxy) == null) {
                    DataStore.selectedProxy = imported.first().id
                }
                onMainDispatcher {
                    displayFragmentWithId(R.id.nav_configuration, reverseAnim = true)
                }
            }
        }
    }

    suspend fun importProfile(uri: Uri) {
        val profile = try {
            parseProxies(uri.toString()).getOrNull(0) ?: error(getString(R.string.no_proxies_found))
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
            }
            return
        }

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.profile_import)
                .setMessage(getString(R.string.profile_import_message, profile.displayName()))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportProfile(profile)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
        }
    }

    private suspend fun finishImportProfile(profile: AbstractBean) {
        val targetId = DataStore.selectedGroupForImport()
        ProfileManager.createProfile(targetId, profile)
        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)
            snackbar(resources.getQuantityString(R.plurals.added, 1, 1)).show()
        }
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        val pluginEntity = PluginEntry.find(pluginName)
        if (pluginEntity == null) {
            snackbar(getString(R.string.plugin_unknown, pluginName)).show()
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.missing_plugin)
            .setMessage(
                getString(
                    R.string.profile_requiring_plugin, profileName, pluginEntity.displayName
                )
            )
            .setPositiveButton(R.string.action_download) { _, _ ->
                launchCustomTab(pluginEntity.downloadLink)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_learn_more) { _, _ ->
                launchCustomTab("https://matsuridayo.github.io/nb4a-plugin/")
            }
            .showBlur()
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state
        binding.fab.changeState(state, DataStore.serviceState, animate)
        binding.stats.changeState(state)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
            if (binding.fab.isShown) {
                anchorView = binding.fab
            }
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: ISagerNetService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar(R.string.vpn_permission_denied).show()
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(data)
        }
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
    }

    private fun setupQuickActions() {
        binding.btnQuickMenu.setOnClickListener {
            navMenuController.showMenu()
        }
        binding.btnQuickSubUpdate.setOnClickListener {
            runOnDefaultDispatcher {
                val current = DataStore.currentGroup()
                if (current.type == GroupType.SUBSCRIPTION) {
                    GroupUpdater.startUpdate(current, true)
                } else {
                    io.nekohasekai.sagernet.database.SagerDatabase.groupDao.allGroups()
                        .filter { it.type == GroupType.SUBSCRIPTION }
                        .forEach { GroupUpdater.startUpdate(it, true) }
                }
            }
        }
        binding.btnQuickUrlTest.setOnClickListener {
            if (DataStore.serviceState.connected) {
                binding.stats.testConnection()
            } else {
                snackbar(R.string.not_connected).show()
            }
        }
        binding.btnQuickSettings.setOnClickListener {
            displayFragmentWithId(R.id.nav_settings)
        }
        updateQuickActionsVisibility(isHome = true)
    }

    private fun updateQuickActionsVisibility(isHome: Boolean) {
        binding.layoutQuickActions.visibility =
            if (isHome && DataStore.showQuickActions) View.VISIBLE else View.GONE
    }

    private fun updateSnowflakesVisibility() {
        binding.snowflakesView.visibility =
            if (DataStore.showSnowflakes) View.VISIBLE else View.GONE
    }

    private fun updateNavBannerImage() {
        binding.root.findViewById<com.neko.blur.BlurImageView>(R.id.nav_banner_image)?.let { iv ->
            val res = top.nkbe.mikubox.ui.theme.MikuBannerManager.getCurrentHomeBannerRes()
            if (iv.tag == res) return@let
            iv.tag = res
            iv.setImageResource(res)
            // setImageResource drops the blur applied from XML, so re-apply the layout's blur_radius.
            iv.setBlur(2)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        runOnUiThread {
            when (key) {
                "disable_bottom_sheet_home" -> updateDrawerLockMode()
                "home_banner_theme", "randomize_banner_on_launch" -> updateNavBannerImage()
                "blur_bottom_status", "blur_bottom_blob_anim", "blur_bottom_radius", "blur_bottom_alpha" -> {
                    binding.stats.refreshMikuStyle()
                }
                "show_fab_timer" -> binding.fab.refreshTimerVisibility()
                "show_quick_actions" -> {
                    val isHome = supportFragmentManager.findFragmentById(R.id.fragment_holder) is ConfigurationFragment
                    updateQuickActionsVisibility(isHome)
                }
                "show_snowflakes" -> updateSnowflakesVisibility()
                Key.SHOW_BOTTOM_BAR -> {
                    val current = supportFragmentManager.findFragmentById(R.id.fragment_holder)
                    val shouldShow = current is ConfigurationFragment || DataStore.showBottomBar
                    binding.stats.allowShow = shouldShow
                    if (shouldShow) {
                        binding.stats.performShow()
                        binding.fab.show()
                        binding.bottomDockContainer.post {
                            MikuInsets.updateDockVisibilityHeight(binding.bottomDockContainer.height)
                        }
                    } else {
                        binding.stats.performHide()
                        binding.fab.hide()
                        MikuInsets.updateDockVisibilityHeight(0)
                    }
                }
                Key.ENABLE_CLASH_API -> refreshNavMenu(DataStore.enableClashAPI)
                Key.SERVICE_MODE -> onBinderDied()
                Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL -> {
                    if (DataStore.serviceState.canStop) {
                        snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                            SagerNet.reloadService()
                        }.show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        if (::binding.isInitialized) {
            binding.stats.setForegroundActive(true)
            binding.fab.setForegroundActive(true)
            binding.snowflakesView.setPaused(false)
        }
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        if (::binding.isInitialized) {
            binding.stats.setForegroundActive(false)
            binding.fab.setForegroundActive(false)
            binding.snowflakesView.setPaused(true)
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        GroupManager.userInterface = null
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (super.onKeyDown(keyCode, event)) return true
        
        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        return fragment != null && fragment.onKeyDown(keyCode, event)
    }
}
