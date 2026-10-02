package com.miku.ray.ui.checkupdate

import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.base.BaseActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.miku.ray.util.showBlur
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityCheckUpdateBinding
import com.miku.ray.dto.CheckUpdateResult
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.UpdateCheckerManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.launch

class CheckUpdateActivity : BaseActivity() {

    private val binding by lazy { ActivityCheckUpdateBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.checkUpdateScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.update_check_for_update), subtitle = getString(R.string.subtitle_check_update))

        binding.layoutCheckUpdate.setOnClickListener {
            checkForUpdates(binding.checkPreRelease.isChecked)
        }

        binding.checkPreRelease.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, isChecked)
        }
        binding.checkPreRelease.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false)

        // PackageManager is the authority for the installed version; compiled-in
        // constants live in the vendored library module and can lag the build.
        val installedName = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        getString(R.string.version_line_with_core, installedName, CoreNativeManager.getLibVersion()).also {
            binding.tvVersion.text = it
        }

        checkForUpdates(binding.checkPreRelease.isChecked)
    }

    private fun checkForUpdates(includePreRelease: Boolean) {
        showLoading()

        lifecycleScope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(this@CheckUpdateActivity, includePreRelease)
                if (result.hasUpdate) {
                    showUpdateDialog(result)
                } else {
                    snackbarSuccess(
                        getString(R.string.update_already_latest_version),
                        title = getString(R.string.title_alerter_success)
                    )
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                snackbarError(
                    e.message ?: getString(R.string.update_check_for_update),
                    title = getString(R.string.title_alerter_error)
                )
            } finally {
                hideLoading()
            }
        }
    }

    private fun showUpdateDialog(result: CheckUpdateResult) {
        AlertDialog.Builder(this)
        .setTitle(getString(R.string.update_new_version_found, result.latestVersion))
        .setIcon(RemixR.drawable.rmx_device_restart_line)
        .setMessage(result.releaseNotes)
        // Confirming hands off to the release page in the browser, where the
        // matching APK is; installing straight from the app is not attempted.
        .setPositiveButton(R.string.update_now) { _, _ ->
            result.releaseUrl?.let { Utils.openUri(this, it) }
                ?: result.downloadUrl?.let { Utils.openUri(this, it) }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .showBlur()
    }
}
