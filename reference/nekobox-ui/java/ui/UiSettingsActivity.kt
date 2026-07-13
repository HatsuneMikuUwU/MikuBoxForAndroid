package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.yalantis.ucrop.UCrop
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.matsuri.nb4a.ui.SimpleMenuPreference
import java.io.File

/**
 * Scoped port of MikuRay's `UiSettingsActivity`. MikuRay's original screen
 * also covers weather chips, blur, custom fonts, home banners and more —
 * none of those subsystems exist in NekoBox, so this only exposes the
 * settings actually wired up by the ported main menu bottom sheet: icon
 * shape, the sheet banner, and profile identity.
 */
class UiSettingsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_config_settings)
        setupCollapsingToolbar(R.string.title_ui_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings), ListListener)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, UiSettingsFragment())
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun snackbarInternal(text: CharSequence) =
        Snackbar.make(findViewById(R.id.main_content), text, Snackbar.LENGTH_LONG)

    class UiSettingsFragment : PreferenceFragmentCompat() {

        private val pickSheetBanner =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    if (isGif(uri)) {
                        saveSheetBannerDirect(uri)
                    } else {
                        startCropSheetBanner(uri)
                    }
                }
            }

        private val pickProfileBanner =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) startCropProfileBanner(uri)
            }

        private val cropSheetBanner =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    saveSheetBanner(croppedUri, "jpg")
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    result.data?.let { UCrop.getError(it) }?.printStackTrace()
                }
            }

        private val cropProfileBanner =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                    saveProfileBanner(croppedUri)
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    result.data?.let { UCrop.getError(it) }?.printStackTrace()
                }
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.configurationStore
            addPreferencesFromResource(R.xml.pref_ui_settings)

            findPreference<SimpleMenuPreference>("iconShape")?.setOnPreferenceChangeListener { _, _ ->
                requireContext().sendBroadcast(Intent(Action.ICON_SHAPE_CHANGED))
                true
            }

            findPreference<SimpleMenuPreference>("profileBannerShape")?.setOnPreferenceChangeListener { _, _ ->
                requireContext().sendBroadcast(Intent(Action.PROFILE_BANNER_CHANGED))
                true
            }

            findPreference<Preference>("action_pick_sheet_banner")?.setOnPreferenceClickListener {
                pickSheetBanner.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
                true
            }

            findPreference<Preference>("action_delete_sheet_banner")?.setOnPreferenceClickListener {
                deleteSheetBanner()
                true
            }

            findPreference<Preference>("action_pick_profile_banner")?.setOnPreferenceClickListener {
                pickProfileBanner.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
                true
            }

            findPreference<Preference>("action_delete_profile_banner")?.setOnPreferenceClickListener {
                deleteProfileBanner()
                true
            }
        }

        // --- crop launch helpers ---

        private fun startCropSheetBanner(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_sheet_banner_temp.jpg")
            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val targetHeightPx = displayMetrics.density * 150

            val uCrop = UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withAspectRatio(screenWidthPx, targetHeightPx)
                .withMaxResultSize(1920, 1080)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(false)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }

            cropSheetBanner.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropProfileBanner(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_profile_banner_temp.jpg")

            val uCrop = UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(512, 512)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                    setDimmedLayerColor(Color.parseColor("#CC000000"))
                    setCircleDimmedLayer(true)
                    setShowCropGrid(true)
                    setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }

            cropProfileBanner.launch(uCrop.getIntent(requireContext()))
        }

        // --- save/delete ---

        private fun saveSheetBannerDirect(sourceUri: Uri) {
            lifecycleScope.launch {
                try {
                    deleteOldFile(DataStore.customSheetBannerUri)
                    val savedUri = saveBannerFile(sourceUri, "sheet_banner_", "gif")
                    DataStore.customSheetBannerUri = savedUri.toString()
                    (activity as? UiSettingsActivity)?.snackbar(getString(R.string.sheet_banner_updated))?.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun saveSheetBanner(croppedUri: Uri, ext: String) {
            lifecycleScope.launch {
                try {
                    deleteOldFile(DataStore.customSheetBannerUri)
                    val savedUri = saveBannerFile(croppedUri, "sheet_banner_", ext)
                    DataStore.customSheetBannerUri = savedUri.toString()
                    (activity as? UiSettingsActivity)?.snackbar(getString(R.string.sheet_banner_updated))?.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun deleteSheetBanner() {
            lifecycleScope.launch {
                deleteOldFile(DataStore.customSheetBannerUri)
                DataStore.customSheetBannerUri = ""
                (activity as? UiSettingsActivity)?.snackbar(getString(R.string.sheet_banner_deleted))?.show()
            }
        }

        private fun saveProfileBanner(croppedUri: Uri) {
            lifecycleScope.launch {
                try {
                    deleteOldFile(DataStore.profileBannerUri)
                    val savedUri = saveBannerFile(croppedUri, "profile_banner_", "jpg")
                    DataStore.profileBannerUri = savedUri.toString()
                    requireContext().sendBroadcast(Intent(Action.PROFILE_BANNER_CHANGED))
                    (activity as? UiSettingsActivity)?.snackbar(getString(R.string.custom_banner_profile_set))?.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun deleteProfileBanner() {
            lifecycleScope.launch {
                deleteOldFile(DataStore.profileBannerUri)
                DataStore.profileBannerUri = ""
                requireContext().sendBroadcast(Intent(Action.PROFILE_BANNER_CHANGED))
                (activity as? UiSettingsActivity)?.snackbar(getString(R.string.custom_banner_profile_deleted))?.show()
            }
        }

        private fun isGif(uri: Uri): Boolean {
            val mimeType = requireContext().contentResolver.getType(uri)
            if (mimeType == "image/gif") return true
            return uri.path?.lowercase()?.endsWith(".gif") == true
        }

        // Persistent banners live in filesDir/banners, not cacheDir: the system's
        // "Clear Cache" wipes cacheDir but leaves filesDir untouched. The cropped
        // temp file UCrop wrote to cacheDir is copied out then left for the OS to
        // reclaim.
        private suspend fun saveBannerFile(
            sourceUri: Uri,
            fileNamePrefix: String,
            ext: String,
        ): Uri = withContext(Dispatchers.IO) {
            val ctx = requireContext()
            val bannersDir = File(ctx.filesDir, "banners").apply { mkdirs() }
            val destFile = File(bannersDir, "$fileNamePrefix${System.currentTimeMillis()}.$ext")
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
            } catch (_: Exception) {
            }
            Uri.fromFile(destFile)
        }

        private suspend fun deleteOldFile(uriString: String?) = withContext(Dispatchers.IO) {
            if (uriString.isNullOrEmpty()) return@withContext
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it) }?.takeIf { it.exists() }?.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
