package top.uwu.mikubox.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
import top.uwu.mikubox.core.MihomoCore
import top.uwu.mikubox.core.MihomoCoreSettings
import top.uwu.mikubox.databinding.ActivityMainBinding
import top.uwu.mikubox.databinding.ItemDrawerEntryBinding
import java.util.Calendar
import top.uwu.mikubox.profile.MihomoProfileImporter
import top.uwu.mikubox.profile.MihomoProfileStore
import top.uwu.mikubox.profile.MihomoSubscriptionUpdater
import top.uwu.mikubox.service.MikuVpnService
import top.uwu.mikubox.service.VpnController

/**
 * Minimal functional home: manage Mihomo profiles/subscriptions and start/stop
 * the VPN. A faithful MikuRay-style redesign comes later; this restores the
 * core MikuBox workflow (import a subscription/config, then connect).
 */
class MainActivity : EdgeToEdgeActivity(), AddProfileBottomSheet.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProfileAdapter

    private enum class SortMode { NAME, UPDATED }
    private var sortMode = SortMode.NAME
    private var query = ""

    private val vpnFailureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val detail = intent.getStringExtra(MikuVpnService.EXTRA_FAILURE_DETAIL).orEmpty()
            toast(getString(R.string.toast_vpn_start_failed, detail))
            refresh()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val trafficTick = object : Runnable {
        override fun run() {
            updateTraffic()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyMainSystemBarInsets()

        adapter = ProfileAdapter(
            onSelect = { profile ->
                MihomoProfileStore.select(this, profile.id)
                refresh()
            },
            onUpdate = { profile -> confirmUpdate(profile) },
            onDelete = { profile ->
                MihomoProfileStore.remove(this, profile.id)
                refresh()
            },
            onShare = { profile -> shareProfile(profile) },
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter
        binding.groupTab.addTab(binding.groupTab.newTab().setText(getString(R.string.profiles_header)))

        binding.btnAddConfig.setOnClickListener { showSortMenu(it) }
        binding.btnAddProfile.setOnClickListener {
            AddProfileBottomSheet().show(supportFragmentManager, AddProfileBottomSheet.TAG)
        }
        binding.btnHome.setOnClickListener { openDrawer() }
        binding.btnMoreMenu.setOnClickListener { confirmRefreshAllSubscriptions() }
        binding.etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty()
            refresh()
        }
        setupDrawer()
        binding.fab.setOnClickListener { toggleConnection() }
        binding.cardBottomStatus.setOnClickListener { toggleConnection() }
        binding.tvGreeting.setText(greetingRes())

        requestNotificationPermission()

        if (savedInstanceState == null &&
            MihomoCoreSettings.autoConnectOnStart(this) &&
            !VpnController.isRunning &&
            MihomoProfileStore.selected(this) != null
        ) {
            VpnController.connect(this)
        }
    }

    private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    private fun applyMainSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = maxOf(bars.left, cutout.left),
                right = maxOf(bars.right, cutout.right),
                bottom = maxOf(bars.bottom, cutout.bottom),
            )
            binding.headerContent.updatePadding(top = maxOf(bars.top, cutout.top))
            insets
        }
        ViewCompat.requestApplyInsets(binding.mainContent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(trafficTick)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            vpnFailureReceiver,
            IntentFilter(MikuVpnService.ACTION_VPN_START_FAILED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(vpnFailureReceiver)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(trafficTick)
    }

    private fun refreshAllSubscriptions() {
        val subscriptions = MihomoProfileStore.profiles(this).filter { it.isSubscription }
        if (subscriptions.isEmpty()) {
            toast(getString(R.string.toast_no_subscriptions))
            return
        }
        lifecycleScope.launch {
            var updated = 0
            for (profile in subscriptions) {
                adapter.setUpdating(profile.id)
                val result = withContext(Dispatchers.IO) {
                    runCatching { MihomoSubscriptionUpdater.update(this@MainActivity, profile) }
                }
                if (result.isSuccess) updated++
            }
            adapter.setUpdating(null)
            toast(getString(R.string.toast_subscriptions_updated, updated, subscriptions.size))
            refresh()
        }
    }

    private fun confirmRefreshAllSubscriptions() {
        val count = MihomoProfileStore.profiles(this).count { it.isSubscription }
        if (count == 0) {
            toast(getString(R.string.toast_no_subscriptions))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_refresh_subscriptions_title)
            .setMessage(getString(R.string.dialog_refresh_subscriptions_message, count))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_update) { _, _ -> refreshAllSubscriptions() }
            .show()
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 0, 0, R.string.sort_by_name)
            menu.add(0, 1, 1, R.string.sort_by_updated)
            setOnMenuItemClickListener { item ->
                sortMode = if (item.itemId == 1) SortMode.UPDATED else SortMode.NAME
                refresh()
                true
            }
            show()
        }
    }

    private fun refresh() {
        val selected = MihomoProfileStore.selected(this)
        val profiles = MihomoProfileStore.profiles(this)
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let { list ->
                when (sortMode) {
                    SortMode.NAME -> list.sortedBy { it.name.lowercase(java.util.Locale.getDefault()) }
                    SortMode.UPDATED -> list.sortedByDescending { it.updatedAtMillis }
                }
            }
        adapter.submit(profiles, selected?.id)
        binding.emptyCard.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        val running = VpnController.isRunning
        binding.fab.setImageResource(if (running) R.drawable.ic_service_busy else R.drawable.ic_service_idle)
        binding.status.text = when {
            running && selected != null -> getString(R.string.status_connected, selected.name)
            running -> getString(R.string.status_connected_no_profile)
            selected == null -> getString(R.string.status_no_profile)
            else -> getString(R.string.status_disconnected)
        }
        updateTraffic()
    }

    private fun updateTraffic() {
        if (!VpnController.isRunning) {
            binding.tx.text = getString(R.string.traffic_up, formatBytes(0))
            binding.rx.text = getString(R.string.traffic_down, formatBytes(0))
            return
        }
        val traffic = runCatching { MihomoCore.traffic() }.getOrNull() ?: return
        binding.tx.text = getString(R.string.traffic_up, formatBytes(traffic.uploadPerSecond))
        binding.rx.text = getString(R.string.traffic_down, formatBytes(traffic.downloadPerSecond))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }

    override fun onAddSubscription(url: String, name: String) {
        if (url.isEmpty()) {
            toast(getString(R.string.error_subscription_url_blank))
            return
        }
        val profile = try {
            MihomoProfileImporter.importSubscription(this, name, url)
        } catch (e: Exception) {
            toast(getString(R.string.toast_import_failed, e.message ?: ""))
            return
        }
        toast(getString(R.string.toast_subscription_added))
        refresh()
        pull(profile)
    }

    private fun confirmUpdate(profile: MihomoProfileStore.Profile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_update_subscription_title)
            .setMessage(getString(R.string.dialog_update_subscription_message, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_update) { _, _ -> pull(profile) }
            .show()
    }

    override fun onImportClipboard(name: String) {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (clip.isEmpty()) {
            toast(getString(R.string.toast_clipboard_empty))
            return
        }
        try {
            MihomoProfileImporter.importConfig(this, name, clip)
            toast(getString(R.string.toast_config_imported))
            refresh()
        } catch (e: Exception) {
            toast(getString(R.string.toast_import_failed, e.message ?: ""))
        }
    }

    private fun pull(profile: MihomoProfileStore.Profile) {
        lifecycleScope.launch {
            adapter.setUpdating(profile.id)
            val result = withContext(Dispatchers.IO) {
                runCatching { MihomoSubscriptionUpdater.update(this@MainActivity, profile) }
            }
            adapter.setUpdating(null)
            result
                .onSuccess { changes ->
                    toast(getString(R.string.toast_subscription_updated))
                    showSubscriptionChanges(profile, changes)
                }
                .onFailure { toast(getString(R.string.toast_update_failed, it.message ?: "")) }
            refresh()
        }
    }

    private fun toggleConnection() {
        if (VpnController.isRunning) {
            VpnController.disconnect(this)
        } else {
            if (MihomoProfileStore.selected(this) == null) {
                toast(getString(R.string.toast_select_profile_first))
                return
            }
            VpnController.connect(this)
        }
        binding.fab.postDelayed({ refresh() }, 600)
    }

    private fun showSubscriptionChanges(
        profile: MihomoProfileStore.Profile,
        changes: MihomoSubscriptionUpdater.UpdateResult,
    ) {
        if (changes.added.isEmpty() && changes.deleted.isEmpty()) return
        val message = buildList {
            if (changes.added.isNotEmpty()) {
                add(getString(R.string.subscription_changes_added, changes.added.joinToString("\n")))
            }
            if (changes.deleted.isNotEmpty()) {
                add(getString(R.string.subscription_changes_deleted, changes.deleted.joinToString("\n")))
            }
        }.joinToString("\n\n")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.subscription_changes_title, profile.name))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupDrawer() {
        bindEntry(binding.drawerProxies, R.drawable.ic_lan, R.string.menu_proxies, ProxiesActivity::class.java)
        bindEntry(binding.drawerSettings, R.drawable.ic_settings_24dp, R.string.settings, SettingsActivity::class.java)
        bindEntry(binding.drawerApps, R.drawable.ic_subscriptions_24dp, R.string.settings_per_app, AppListActivity::class.java)
        bindEntry(binding.drawerLogcat, R.drawable.ic_logcat_24dp, R.string.menu_log, LogcatActivity::class.java)
        bindEntry(binding.drawerTools, R.drawable.baseline_construction_24, R.string.menu_tools, ToolsActivity::class.java)
        bindEntry(binding.drawerAbout, R.drawable.ic_about_24dp, R.string.menu_about, AboutActivity::class.java)
    }

    private fun bindEntry(entry: ItemDrawerEntryBinding, iconRes: Int, labelRes: Int, target: Class<*>) {
        entry.icon.setImageResource(iconRes)
        entry.label.setText(labelRes)
        entry.root.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, target))
        }
    }

    private fun openDrawer() = binding.drawerLayout.openDrawer(GravityCompat.START)

    @Deprecated("Handles drawer close before default back navigation")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun shareProfile(profile: MihomoProfileStore.Profile) {
        val url = profile.subscriptionUrl
        if (!url.isNullOrBlank()) {
            QrCode.show(this, profile.name, url)
        } else {
            startActivity(
                android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, profile.config),
                    profile.name,
                )
            )
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
