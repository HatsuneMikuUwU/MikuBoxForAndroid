package com.miku.ray.ui.preference.activity

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.bottomsheet.IndicatorStyleBottomSheet
import com.miku.ray.ui.dialog.BannerHeightSliderDialog
import com.miku.ray.ui.dialog.HeaderTopRowPaddingDialog
import com.miku.ray.ui.dialog.ParticlesSettingsDialog
import com.miku.ray.ui.dialog.SelectedBannerDimSliderDialog
import com.miku.ray.ui.dialog.SnowflakesSettingsDialog
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.util.BannerColorExtractor
import com.miku.ray.util.SelectedProfileBannerController
import com.miku.ray.util.showBlur
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Banners and personalization: home banner, sheet banner, selected-profile style, profile. */
class BannerSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_banner), subtitle = getString(R.string.subtitle_ui_banner))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, BannerSettingsFragment())
            .commit()
        }
    }

    class BannerSettingsFragment : PreferenceFragmentCompat() {

        private val disableHomeBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DISABLE_HOME_BANNER) }
        private val bannerHeightSlider by lazy { findPreference<BannerHeightSliderDialog>(AppConfig.PREF_HOME_BANNER_HEIGHT) }
        private val headerTopRowPaddingSlider by lazy { findPreference<HeaderTopRowPaddingDialog>(AppConfig.PREF_HEADER_TOP_ROW_PADDING) }
        private val changeHomeBannerImageAction by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_HOME_BANNER) }
        private val deleteHomeBannerImageAction by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_HOME_BANNER) }
        private val indicatorStyle by lazy { findPreference<Preference>(AppConfig.PREF_INDICATOR_STYLE) }
        private val selectedBannerStyleEnabled by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED) }
        private val selectedBannerCategory by lazy { findPreference<PreferenceCategory>("pref_category_selected_banner") }

        private val pickProfileImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) startCropProfileActivity(uri)
        }

        private val pickHomeBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    saveGifBannerDirectly(uri, AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "home_banner_") {
                        extractAndSaveBannerColor(it)
                        broadcastHomeBannerChanged()
                        requireContext().toastSuccess(getString(R.string.home_banner_updated))
                    }
                } else {
                    startCropHomeBannerActivity(uri)
                }
            }
        }

        private val pickSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    saveGifBannerDirectly(uri, AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, "sheet_banner_") {
                        requireContext().toastSuccess(getString(R.string.sheet_banner_updated))
                    }
                } else {
                    startCropSheetBannerActivity(uri)
                }
            }
        }

        private val pickSelectedBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) startCropSelectedBannerActivity(uri)
        }

        private val cropHomeBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                lifecycleScope.launch {
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveBannerFile(cacheUri, "home_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, savedUri.toString())
                        SettingsManager.preloadBanner(requireContext(), savedUri.toString())

                        extractAndSaveBannerColor(savedUri)
                        broadcastHomeBannerChanged()
                        requireContext().toastSuccess(getString(R.string.home_banner_updated))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                UCrop.getError(result.data!!)?.printStackTrace()
            }
        }

        private val cropProfileImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                lifecycleScope.launch {
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveBannerFile(cacheUri, "profile_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, savedUri.toString())
                        SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                        broadcastProfileChanged()
                        requireContext().toastSuccess(getString(R.string.custom_banner_profile_set))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                UCrop.getError(result.data!!)?.printStackTrace()
            }
        }

        private val cropSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                lifecycleScope.launch {
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveBannerFile(cacheUri, "sheet_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, savedUri.toString())
                        SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                        requireContext().toastSuccess(getString(R.string.sheet_banner_updated))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                UCrop.getError(result.data!!)?.printStackTrace()
            }
        }

        private val cropSelectedBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                lifecycleScope.launch {
                    try {
                        val oldUri = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
                        deleteOldFile(oldUri)
                        val savedUri = saveBannerFile(cacheUri, "selected_banner_")
                        MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_URI, savedUri.toString())
                        SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                        updateIndicatorStyleEnabledState()
                        broadcastSelectedBannerChanged()
                        requireContext().toastSuccess(getString(R.string.selected_banner_updated))
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
            addPreferencesFromResource(R.xml.pref_ui_banner)
            initPreferenceSummaries()

            setupProfilePreferences()
            setupHomeBannerPreferences()
            setupSheetBannerPreferences()
            setupSelectedBannerPreferences()
            setupParticlesPreferences()
            updateSelectedBannerCategoryVisibility()

            CategoryStyleHelper.applyToFragment(this)
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        override fun onResume() {
            super.onResume()
            // The double-column switch lives on the home page; re-reading here
            // keeps the selected-banner group honest after a round trip.
            updateSelectedBannerCategoryVisibility()
        }

        private fun extractAndSaveBannerColor(uri: Uri) {
            lifecycleScope.launch {
                BannerColorExtractor.extractAndSave(requireContext(), uri) { colorChanged ->
                    if (colorChanged && MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)) {
                        SettingsChangeManager.requestRecreate()
                    }
                }
            }
        }

        private fun setupSheetBannerPreferences() {
            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_SHEET_BANNER)?.setOnPreferenceClickListener {
                pickSheetBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_SHEET_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.sheet_banner_delete_title)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.sheet_banner_delete_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, "")
                            requireContext().snackbarSuccess(getString(R.string.sheet_banner_delete_summary), title = getString(R.string.title_alerter_success))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                }
                true
            }
        }

        private fun setupSelectedBannerPreferences() {
            updateIndicatorStyleEnabledState()

            selectedBannerStyleEnabled?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)
                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, checked)
                    updateIndicatorStyleEnabledState()
                    broadcastSelectedBannerChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_SELECTED_BANNER)?.setOnPreferenceClickListener {
                pickSelectedBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_SELECTED_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_SELECTED_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.selected_banner_delete_title)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.selected_banner_delete_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_URI, "")
                            updateIndicatorStyleEnabledState()
                            broadcastSelectedBannerChanged()
                            requireContext().snackbarSuccess(getString(R.string.selected_banner_delete_summary), title = getString(R.string.title_alerter_success))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                }
                true
            }
        }

        private fun updateIndicatorStyleEnabledState() {
            val bannerEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED, false)

            indicatorStyle?.apply {
                isEnabled = !bannerEnabled
                summary = if (bannerEnabled) {
                    getString(R.string.pref_indicator_style_summary_disabled_by_banner)
                } else {
                    getString(R.string.pref_indicator_style_summary)
                }
            }
        }

        private fun setupProfilePreferences() {
            findPreference<EditTextPreference>(AppConfig.PREF_CUSTOM_PROFILE_NAME)?.apply {
                val currentName = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_PROFILE_NAME) ?: ""
                text = currentName
                summary = currentName.ifEmpty { getString(R.string.uwu_profile_banner_title) }
                setOnBindEditTextListener { editText ->
                    editText.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
                    editText.setSingleLine()
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val newName = newValue.toString()
                    MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_PROFILE_NAME, newName)
                    summary = newName.ifEmpty { getString(R.string.uwu_profile_banner_title) }
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_PROFILE_BANNER)?.setOnPreferenceClickListener {
                pickProfileImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<ListPreference>(AppConfig.PREF_PROFILE_BANNER_SHAPE)?.apply {
                val savedShape = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_SHAPE)
                ?: AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT
                value = savedShape
                summary = "%s"
                setOnPreferenceChangeListener { _, newValue ->
                    MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_SHAPE, newValue.toString())
                    broadcastProfileChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_PROFILE_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_custom_banner_profile)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.delete_custom_banner_profile_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_PROFILE_BANNER_URI, "")
                            broadcastProfileChanged()
                            requireContext().snackbarSuccess(getString(R.string.delete_custom_banner_profile_summary), title = getString(R.string.title_alerter_success))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                }
                true
            }
        }

        private fun setupHomeBannerPreferences() {
            disableHomeBanner?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)
                updateHomeBannerChildrenEnabled(isChecked)

                setOnPreferenceChangeListener { _, newValue ->
                    val checked = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_DISABLE_HOME_BANNER, checked)
                    updateHomeBannerChildrenEnabled(checked)

                    if (checked) {
                        // A disabled banner cannot feed the dynamic colour; the
                        // switch itself lives on the appearance page, so only the
                        // persisted state is corrected here.
                        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)) {
                            MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                            SettingsChangeManager.requestRecreate()
                        }
                    }

                    broadcastHomeBannerChanged()
                    true
                }
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_CHANGE_HOME_BANNER)?.setOnPreferenceClickListener {
                pickHomeBannerImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                true
            }

            findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_HOME_BANNER)?.setOnPreferenceClickListener {
                val savedUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI)
                if (!savedUri.isNullOrEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.home_banner_delete_title)
                    .setIcon(RemixR.drawable.rmx_delete_bin_line)
                    .setMessage(R.string.home_banner_delete_summary)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            deleteOldFile(savedUri)
                            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "")
                            MmkvManager.encodeSettings(AppConfig.PREF_BANNER_COLOR, 0)

                            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)) {
                                SettingsChangeManager.requestRecreate()
                            }
                            broadcastHomeBannerChanged()
                            requireContext().snackbarSuccess(getString(R.string.home_banner_delete_summary), title = getString(R.string.title_alerter_success))
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showBlur()
                }
                true
            }
        }

        private fun updateHomeBannerChildrenEnabled(disabled: Boolean) {
            bannerHeightSlider?.isEnabled = !disabled
            headerTopRowPaddingSlider?.isEnabled = !disabled
            changeHomeBannerImageAction?.isEnabled = !disabled
            deleteHomeBannerImageAction?.isEnabled = !disabled
        }

        private fun setupParticlesPreferences() {
            fun applySettingsEnabled(enabled: Boolean) {
                findPreference<Preference>(AppConfig.PREF_PARTICLES_SETTINGS)?.isEnabled = enabled
            }

            findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_PARTICLES_SHEET)?.apply {
                isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_PARTICLES_SHEET, false)
                applySettingsEnabled(isChecked)
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_PARTICLES_SHEET, enabled)
                    applySettingsEnabled(enabled)
                    true
                }
            }
        }

        private fun startCropSheetBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_sheet_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)
            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val targetHeightPx = displayMetrics.density * 150

            val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1920, 1080)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                        setDimmedLayerColor(Color.parseColor("#CC000000"))
                        setCircleDimmedLayer(false)
                        setShowCropGrid(true)
                        setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropSheetBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropSelectedBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_selected_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            val targetHeightPx = displayMetrics.density * 120

            val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1280, 720)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                        setDimmedLayerColor(Color.parseColor("#CC000000"))
                        setCircleDimmedLayer(false)
                        setShowCropGrid(true)
                        setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropSelectedBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropHomeBannerActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_home_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)

            val displayMetrics = resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()

            val heightDp = MmkvManager.decodeSettingsInt(
                AppConfig.PREF_HOME_BANNER_HEIGHT,
                AppConfig.HOME_BANNER_HEIGHT_DEFAULT
            )
            val targetHeightPx = displayMetrics.density * heightDp

            val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1920, 1080)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                        setDimmedLayerColor(Color.parseColor("#CC000000"))
                        setCircleDimmedLayer(false)
                        setShowCropGrid(true)
                        setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }

            cropHomeBannerImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun startCropProfileActivity(sourceUri: Uri) {
            val destFile = File(requireContext().cacheDir, "cropped_profile_banner_temp.jpg")
            val destUri = Uri.fromFile(destFile)
            val uCrop = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)

            try {
                uCrop.withOptions(UCrop.Options().apply {
                        setDimmedLayerColor(Color.parseColor("#CC000000"))
                        setCircleDimmedLayer(true)
                        setShowCropGrid(true)
                        setFreeStyleCropEnabled(false)
                })
            } catch (e: Exception) { e.printStackTrace() }
            cropProfileImage.launch(uCrop.getIntent(requireContext()))
        }

        private fun isGif(uri: Uri): Boolean {
            val mimeType = requireContext().contentResolver.getType(uri)
            if (mimeType == "image/gif") return true
            val path = uri.path ?: return false
            return path.lowercase().endsWith(".gif")
        }

        private fun saveGifBannerDirectly(
            sourceUri: Uri,
            prefKey: String,
            fileNamePrefix: String,
            onSuccess: (Uri) -> Unit
        ) {
            lifecycleScope.launch {
                try {
                    val oldUri = MmkvManager.decodeSettingsString(prefKey)
                    deleteOldFile(oldUri)
                    val savedUri = saveBannerFile(sourceUri, fileNamePrefix, ext = "gif")
                    MmkvManager.encodeSettings(prefKey, savedUri.toString())
                    SettingsManager.preloadBanner(requireContext(), savedUri.toString())
                    onSuccess(savedUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

        private fun broadcastProfileChanged() {
            SettingsChangeManager.notifyUiCustomizationChanged()
        }

        private fun broadcastHomeBannerChanged() {
            SettingsChangeManager.notifyUiCustomizationChanged()
        }

        private fun broadcastSelectedBannerChanged() {
            SelectedProfileBannerController.notifyChanged(requireContext())
        }

        private fun updateSelectedBannerCategoryVisibility() {
            val isGridMode = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
            selectedBannerCategory?.isVisible = !isGridMode
        }

        private fun initPreferenceSummaries() {
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is ListPreference -> {
                            if (p.value == null && !p.entryValues.isNullOrEmpty()) {
                                p.value = p.entryValues[0].toString()
                            }
                            p.summary = p.entry ?: ""
                            p.setOnPreferenceChangeListener { pref, newValue ->
                                val lp = pref as ListPreference
                                val idx = lp.findIndexOfValue(newValue as? String)
                                lp.summary = (if (idx >= 0) lp.entries[idx] else newValue) as CharSequence?
                                true
                            }
                        }
                        else -> {}
                    }
                }
            }
            preferenceScreen?.let { traverse(it) }
        }
    }
}
