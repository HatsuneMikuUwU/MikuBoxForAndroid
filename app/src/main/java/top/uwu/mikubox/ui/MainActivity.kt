package top.uwu.mikubox.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.drawerlayout.widget.DrawerLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
import top.uwu.mikubox.core.AppSettings
import top.uwu.mikubox.core.MihomoCore
import top.uwu.mikubox.databinding.ActivityMainBinding
import top.uwu.mikubox.profile.MihomoProfileImporter
import top.uwu.mikubox.profile.MihomoProfileStore
import top.uwu.mikubox.profile.MihomoSubscriptionUpdater
import top.uwu.mikubox.service.VpnController

/**
 * Minimal functional home: manage Mihomo profiles/subscriptions and start/stop
 * the VPN. A faithful MikuRay-style redesign comes later; this restores the
 * core MikuBox workflow (import a subscription/config, then connect).
 */
class MainActivity : AppCompatActivity(), AddProfileBottomSheet.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProfileAdapter

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

        adapter = ProfileAdapter(
            onSelect = { profile ->
                MihomoProfileStore.select(this, profile.id)
                refresh()
            },
            onUpdate = { profile -> pull(profile) },
            onDelete = { profile ->
                MihomoProfileStore.remove(this, profile.id)
                refresh()
            },
            onShare = { profile -> shareProfile(profile) },
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter

        binding.btnAdd.setOnClickListener {
            AddProfileBottomSheet().show(supportFragmentManager, AddProfileBottomSheet.TAG)
        }
        binding.fab.setOnClickListener { toggleConnection() }
        binding.cardBottomStatus.setOnClickListener { toggleConnection() }
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            val target: Class<*>? = when (item.itemId) {
                R.id.nav_settings -> SettingsActivity::class.java
                R.id.nav_apps -> AppListActivity::class.java
                R.id.nav_logcat -> LogcatActivity::class.java
                R.id.nav_tools -> ToolsActivity::class.java
                R.id.nav_about -> AboutActivity::class.java
                else -> null
            }
            target?.let { startActivity(Intent(this, it)) }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { backCallback.isEnabled = true }
            override fun onDrawerClosed(drawerView: View) { backCallback.isEnabled = false }
        })

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.post(trafficTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(trafficTick)
    }

    private fun refresh() {
        val profiles = MihomoProfileStore.profiles(this)
        val selected = MihomoProfileStore.selected(this)
        adapter.submit(profiles, selected?.id)
        binding.emptyCard.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.particlesView.visibility =
            if (AppSettings.particlesEnabled(this)) View.VISIBLE else View.GONE

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
                .onSuccess { toast(getString(R.string.toast_subscription_updated)) }
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
