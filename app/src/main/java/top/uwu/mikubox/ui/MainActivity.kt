package top.uwu.mikubox.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
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
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProfileAdapter

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
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter

        binding.btnAddSub.setOnClickListener { addSubscription() }
        binding.btnImportClipboard.setOnClickListener { importClipboard() }
        binding.btnConnect.setOnClickListener { toggleConnection() }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val profiles = MihomoProfileStore.profiles(this)
        val selected = MihomoProfileStore.selected(this)
        adapter.submit(profiles, selected?.id)
        binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE

        val running = VpnController.isRunning
        binding.btnConnect.setText(if (running) R.string.action_disconnect else R.string.action_connect)
        binding.tvStatus.text = when {
            running && selected != null -> getString(R.string.status_connected, selected.name)
            running -> getString(R.string.status_connected_no_profile)
            selected == null -> getString(R.string.status_no_profile)
            else -> getString(R.string.status_disconnected)
        }
    }

    private fun addSubscription() {
        val url = binding.etSubUrl.text?.toString()?.trim().orEmpty()
        val name = binding.etName.text?.toString()?.trim().orEmpty()
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
        binding.etSubUrl.text = null
        binding.etName.text = null
        toast(getString(R.string.toast_subscription_added))
        refresh()
        pull(profile)
    }

    private fun importClipboard() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (clip.isEmpty()) {
            toast(getString(R.string.toast_clipboard_empty))
            return
        }
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        try {
            MihomoProfileImporter.importConfig(this, name, clip)
            binding.etName.text = null
            toast(getString(R.string.toast_config_imported))
            refresh()
        } catch (e: Exception) {
            toast(getString(R.string.toast_import_failed, e.message ?: ""))
        }
    }

    private fun pull(profile: MihomoProfileStore.Profile) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MihomoSubscriptionUpdater.update(this@MainActivity, profile) }
            }
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
        binding.btnConnect.postDelayed({ refresh() }, 600)
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
