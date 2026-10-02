package com.miku.ray.ui.preference.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.checkupdate.CheckUpdateActivity
import com.miku.ray.ui.preference.CustomBannerPreference
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.showBlur
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** About the app and its updates: version banner, update entry, launch auto-check. */
class AboutUpdateActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_about_update), subtitle = getString(R.string.subtitle_about_update))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, AboutUpdateFragment())
            .commit()
        }
    }

    class AboutUpdateFragment : PreferenceFragmentCompat() {

        private val navigateCheckUpdate by lazy { findPreference<CustomBannerPreference>(AppConfig.PREF_NAVIGATE_CHECK_UPDATE) }

        private val pickThemeBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) startCropThemeBannerActivity(uri)
        }

        private val cropThemeBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                lifecycleScope.launch {
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveBannerFile(cacheUri, "theme_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_THEME_BANNER_URI, savedUri.toString())
                        SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                        navigateCheckUpdate?.refresh()
                        requireContext().toastSuccess(getString(R.string.theme_banner_updated))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                UCrop.getError(result.data!!)?.printStackTrace()
            }
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore(triggersServiceRestart = false)
            addPreferencesFromResource(R.xml.pref_about_update)
            updateCheckUpdateSummary()
            CategoryStyleHelper.applyToFragment(this)

            navigateCheckUpdate?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), CheckUpdateActivity::class.java))
                true
            }

            navigateCheckUpdate?.onImageClick = {
                pickThemeBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            navigateCheckUpdate?.onImageLongClick = {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.theme_banner_delete_title)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.theme_banner_delete_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_THEME_BANNER_URI, "")
                            navigateCheckUpdate?.refresh()
                            requireContext().snackbarSuccess(getString(R.string.theme_banner_delete_summary), title = getString(R.string.title_alerter_success))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        override fun onResume() {
            super.onResume()
            // The display name is set on the appearance page; re-reading here
            // keeps the banner's greeting honest after a round trip.
            updateCheckUpdateSummary()
        }

        private fun updateCheckUpdateSummary() {
            val appName = AppNameHelper.getDisplayName(requireContext())
            navigateCheckUpdate?.summary = getString(R.string.uwu_update_summary, appName)
        }

        private fun startCropThemeBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_theme_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val screenHeightPx = displayMetrics.heightPixels.toFloat()

            val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(screenWidthPx, screenHeightPx)
            .withMaxResultSize(896, 1984)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                        setDimmedLayerColor(Color.parseColor("#CC000000"))
                        setCircleDimmedLayer(false)
                        setShowCropGrid(true)
                        setFreeStyleCropEnabled(true)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropThemeBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        @Throws(IOException::class)
        private suspend fun saveBannerFile(sourceUri: Uri, fileNamePrefix: String, ext: String = "jpg"): Uri = withContext(Dispatchers.IO) {
            val ctx = requireContext()
            val bannersDir = File(ctx.filesDir, "banners").apply { mkdirs() }
            val destFile = File(bannersDir, "${fileNamePrefix}${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                if (sourceUri.scheme == "file") {
                    val tempFile = File(sourceUri.path!!)
                    if (tempFile.exists() && tempFile.absolutePath.contains(ctx.cacheDir.absolutePath)) {
                        tempFile.delete()
                    }
                }
            } catch (_: Exception) {}
            return@withContext Uri.fromFile(destFile)
        }

        private suspend fun deleteOldFile(uriString: String?) = withContext(Dispatchers.IO) {
            if (uriString.isNullOrEmpty()) return@withContext
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    File(uri.path!!).takeIf { it.exists() }?.delete()
                } else {
                    try { requireContext().contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
}
