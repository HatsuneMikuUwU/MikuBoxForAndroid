package com.miku.ray.ui.preference.activity

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.toastError
import com.miku.ray.extension.toastInfo
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.ui.dialog.DpiSliderDialog
import com.miku.ray.ui.dialog.FontSizeSliderDialog
import kotlin.math.roundToInt
import com.miku.ray.ui.dialog.BlurIntensityDialog
import com.miku.ray.ui.dialog.BlurBottomIntensityDialog
import com.miku.ray.ui.dialog.ThemeColorDialog
import com.miku.ray.ui.dialog.AppIconPickerDialog
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.ThemeShareManager
import com.miku.ray.util.showBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Appearance hub: theme, appearance, font and blur live here; home-screen
 * behaviour, banners and notification/sound are one tap away in their own
 * pages, and the version/update banner moved to AboutUpdateActivity.
 */
class UiSettingsActivity : BaseActivity() {
    private val exportUiTheme = registerForActivityResult(
        ActivityResultContracts.CreateDocument(ThemeShareManager.MIME_TYPE)
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ThemeShareManager.exportTo(this@UiSettingsActivity, uri) }
            }
            if (result.isSuccess) {
                toastSuccess(getString(R.string.ui_theme_exported))
            } else {
                toastError(getString(R.string.ui_theme_export_failed))
            }
        }
    }

    private val importUiTheme = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(::confirmThemeImport)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_settings), subtitle = getString(R.string.subtitle_ui_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, UiSettingsFragment())
            .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ui_settings, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_export_ui_theme -> {
            showThemeExportNameDialog()
            true
        }
        R.id.action_import_ui_theme -> {
            importUiTheme.launch(arrayOf(ThemeShareManager.MIME_TYPE, "application/octet-stream"))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showThemeExportNameDialog() {
        val inputView = layoutInflater.inflate(R.layout.uwu_dialog_edittext, null)
        val messageView = inputView.findViewById<TextView>(android.R.id.message)
        val nameInput = inputView.findViewById<TextInputEditText>(android.R.id.edit)

        messageView.setText(R.string.ui_theme_export_name_message)
        nameInput.hint = getString(R.string.ui_theme_export_name_hint)
        nameInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        nameInput.imeOptions = EditorInfo.IME_ACTION_DONE

        val dialog = MaterialAlertDialogBuilder(this)
        .setIcon(RemixR.drawable.rmx_design_palette_line)
        .setTitle(R.string.ui_theme_export_name_title)
        .setView(inputView)
        .setPositiveButton(R.string.action_export_ui_theme, null)
        .setNegativeButton(android.R.string.cancel, null)
        .showBlur()

        fun submitName() {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.ui_theme_export_name_error_empty)
                return
            }

            dialog.dismiss()
            exportUiTheme.launch(buildThemeExportFileName(name))
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            submitName()
        }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitName()
                true
            } else {
                false
            }
        }
    }

    private fun buildThemeExportFileName(themeName: String): String {
        val extension = ThemeShareManager.FILE_EXTENSION
        val baseName = themeName
        .trim()
        .removeSuffix(extension)
        .trim()
        .map { character ->
            when {
                character.isLetterOrDigit() || character == ' ' || character == '-' ||
                character == '_' || character == '.' -> character
                else -> '_'
            }
        }
        .joinToString("")
        .trim()
        .trim('.')
        .ifBlank { "MikuRay-theme" }

        return "$baseName$extension"
    }

    private fun confirmThemeImport(uri: Uri) {
        MaterialAlertDialogBuilder(this)
        .setIcon(RemixR.drawable.rmx_system_import_line)
        .setTitle(R.string.ui_theme_import_title)
        .setMessage(R.string.ui_theme_import_message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            lifecycleScope.launch {
                showLoading()
                val result = withContext(Dispatchers.IO) {
                    ThemeShareManager.importFrom(this@UiSettingsActivity, uri)
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

    class UiSettingsFragment : PreferenceFragmentCompat() {

        private val appTheme by lazy { findPreference<Preference>(AppConfig.PREF_APP_THEME) }
        private val dynamicColor by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR) }
        private val dynamicColorBanner by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_DYNAMIC_COLOR_BANNER) }
        private val trueBlack by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TRUE_BLACK) }
        private val enableBlur by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_ENABLE_BLUR) }
        private val useSystemBlur by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_USE_SYSTEM_BLUR) }
        private val blurBottomStatus by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_BLUR_BOTTOM_STATUS) }
        private val nightTheme by lazy { findPreference<ListPreference>(AppConfig.PREF_UI_MODE_NIGHT) }
        private val iconShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ICON_SHAPE) }
        private val arrowShape by lazy { findPreference<ListPreference>(AppConfig.PREF_ARROW_SHAPE) }
        private val appIcon by lazy { findPreference<AppIconPickerDialog>(AppConfig.PREF_APP_ICON) }
        private val customAppName by lazy { findPreference<ListPreference>(AppConfig.PREF_CUSTOM_APP_NAME) }
        private val customDpi by lazy { findPreference<DpiSliderDialog>(AppConfig.PREF_CUSTOM_DPI) }
        private val fontSizeSlider by lazy { findPreference<FontSizeSliderDialog>(AppConfig.PREF_APP_FONT_SIZE) }
        private val blurIntensity by lazy { findPreference<BlurIntensityDialog>(AppConfig.PREF_BLUR_INTENSITY) }
        private val blurBottomIntensity by lazy { findPreference<BlurBottomIntensityDialog>(AppConfig.PREF_BLUR_BOTTOM_INTENSITY) }
        private val appFont by lazy { findPreference<Preference>(AppConfig.PREF_APP_FONT) }
        private val customFontSwitch by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_APP_FONT_USE_CUSTOM) }
        private val customFontPick by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_PICK_CUSTOM_FONT) }
        private val customFontDelete by lazy { findPreference<Preference>(AppConfig.PREF_ACTION_DELETE_CUSTOM_FONT) }
        private val categoryStyle by lazy { findPreference<ListPreference>(AppConfig.PREF_CATEGORY_STYLE) }
        private val showSplash by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_SPLASH) }
        private val navigateUiHome by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_HOME) }
        private val navigateUiBanner by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_BANNER) }
        private val navigateUiAlerts by lazy { findPreference<Preference>(AppConfig.PREF_NAVIGATE_UI_ALERTS) }

        private val pickCustomFontFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val displayName = queryDisplayName(uri)
            lifecycleScope.launch {
                val savedFile = withContext(Dispatchers.IO) {
                    CustomFontManager.saveFontFile(requireContext(), uri, displayName)
                }
                if (savedFile != null) {
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, true)
                    customFontSwitch?.isChecked = true
                    appFont?.isEnabled = false
                    updateCustomFontSummary()
                    SettingsChangeManager.requestRecreate()
                } else {
                    requireContext().toastError(getString(R.string.custom_font_invalid))
                }
            }
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore(triggersServiceRestart = false)
            addPreferencesFromResource(R.xml.pref_ui_settings)
            initPreferenceSummaries()

            navigateUiHome?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), HomeSettingsActivity::class.java))
                true
            }

            navigateUiBanner?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), BannerSettingsActivity::class.java))
                true
            }

            navigateUiAlerts?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), AlertSettingsActivity::class.java))
                true
            }

            appTheme?.setOnPreferenceClickListener {
                ThemeColorDialog.show(parentFragmentManager)
                true
            }

            dynamicColor?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)

                if (enabled) {
                    MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                    dynamicColorBanner?.isChecked = false
                }

                dynamicColorBanner?.isEnabled = !enabled && disableHomeBannerEnabled()
                appTheme?.isEnabled = !enabled

                SettingsChangeManager.requestRecreate()
                true
            }

            dynamicColorBanner?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR_BANNER, enabled)

                if (enabled) {
                    MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
                    dynamicColor?.isChecked = false
                }

                dynamicColor?.isEnabled = !enabled
                appTheme?.isEnabled = !enabled

                SettingsChangeManager.requestRecreate()
                true
            }

            trueBlack?.apply {
                val isNightModeActive = ThemeManager.isDarkMode(requireActivity())
                isEnabled = isNightModeActive
                summary = if (!isNightModeActive) getString(R.string.pref_true_black_only_in_night_mode)
                else getString(R.string.summary_pref_true_black)
                setOnPreferenceChangeListener { _, _ ->
                    SettingsChangeManager.requestRecreate()
                    true
                }
            }

            enableBlur?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_ENABLE_BLUR, newValue as Boolean)
                true
            }

            useSystemBlur?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_USE_SYSTEM_BLUR, newValue as Boolean)
                val savedRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
                val savedRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
                blurIntensity?.updateSummary(savedRadius, savedRounds)
                true
            }

            blurBottomStatus?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_STATUS, newValue as Boolean)
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            nightTheme?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                updateTrueBlackState(isNightModeAfterChange(valueStr.toInt()))
                true
            }

            iconShape?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            arrowShape?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            appIcon?.setOnPreferenceChangeListener { _, _ ->
                requireContext().toastSuccess(getString(R.string.app_icon_updated))
                true
            }

            customAppName?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                com.miku.ray.util.LauncherAliasSwitcher.applyNameVariant(requireContext().applicationContext, valueStr)
                true
            }

            appFont?.setOnPreferenceClickListener {
                val currentValue = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "default"
                com.miku.ray.ui.bottomsheet.FontPickerBottomSheet(requireContext(), currentValue) { value, label ->
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT, value)
                    appFont?.summary = label
                    SettingsChangeManager.requestRecreate()
                }.show()
                true
            }
            updateAppFontSummary()
            setupCustomFontPreferences()

            CategoryStyleHelper.applyToFragment(this)
            categoryStyle?.setOnPreferenceChangeListener { pref, newValue ->
                val styleValue = newValue as String
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(styleValue)
                    lp.summary = if (idx >= 0) lp.entries[idx] else styleValue
                }
                MmkvManager.encodeSettings(AppConfig.PREF_CATEGORY_STYLE, styleValue)
                preferenceScreen?.let { screen ->
                    CategoryStyleHelper.applyToGroup(styleValue, screen)
                    listView.adapter?.notifyDataSetChanged()
                }
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            showSplash?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_SPLASH, newValue as Boolean)
                true
            }
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        override fun onResume() {
            super.onResume()
            // Recomputed here rather than onStart: the home-banner-disable
            // switch lives on the banners page and can flip while this screen
            // is in the back stack.
            val isDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
            val isDynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
            val isDisableHomeBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)

            appTheme?.isEnabled = !isDynamicColor && !isDynamicBanner

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                dynamicColor?.isEnabled = false
                dynamicColor?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
                dynamicColorBanner?.isEnabled = false
                dynamicColorBanner?.summary = requireContext().getString(R.string.summary_pref_dynamic_color_unavailable)
            } else {
                dynamicColor?.isEnabled = !isDynamicBanner
                dynamicColorBanner?.isEnabled = !isDynamicColor && !isDisableHomeBanner
            }

            val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            val systemDpi = Resources.getSystem().displayMetrics.densityDpi
            val currentDpi = if (savedDpi > 0) savedDpi else systemDpi
            val currentPercent = (currentDpi * 100f / systemDpi / 5f).roundToInt() * 5
            customDpi?.summary = "$currentPercent%"

            val savedFontSize = MmkvManager.decodeSettingsFloat(AppConfig.PREF_APP_FONT_SIZE, AppConfig.FONT_SIZE_DEFAULT)
            fontSizeSlider?.summary = "${(savedFontSize * 100f).roundToInt()}%"

            val savedRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
            val savedRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)
            blurIntensity?.updateSummary(savedRadius, savedRounds)

            val savedBottomRadius = MmkvManager.decodeSettingsFloat(AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS)
            val savedBottomAlpha = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_BOTTOM_ALPHA, AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA)
            blurBottomIntensity?.updateSummary(savedBottomRadius, savedBottomAlpha)
        }

        /** The dynamic-banner option also dies with the home banner (banners page). */
        private fun disableHomeBannerEnabled(): Boolean =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_HOME_BANNER, false)

        private fun setupCustomFontPreferences() {
            updateCustomFontSummary()

            val useCustom = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            customFontSwitch?.isChecked = useCustom
            appFont?.isEnabled = !useCustom

            customFontSwitch?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                if (checked && CustomFontManager.getFontFile(requireContext()) == null) {
                    pickCustomFontFile.launch(arrayOf("*/*"))
                    false
                } else {
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, checked)
                    appFont?.isEnabled = !checked
                    SettingsChangeManager.requestRecreate()
                    true
                }
            }

            customFontPick?.setOnPreferenceClickListener {
                pickCustomFontFile.launch(arrayOf("*/*"))
                true
            }

            customFontDelete?.setOnPreferenceClickListener {
                if (CustomFontManager.getFontFile(requireContext()) == null) {
                    requireContext().toastInfo(getString(R.string.custom_font_none_to_remove))
                    return@setOnPreferenceClickListener true
                }
                MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_pref_app_font_custom_delete)
                .setIcon(RemixR.drawable.rmx_delete_bin_line)
                .setMessage(R.string.custom_font_delete_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CustomFontManager.clearFont(requireContext())
                    MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
                    customFontSwitch?.isChecked = false
                    appFont?.isEnabled = true
                    updateCustomFontSummary()
                    SettingsChangeManager.requestRecreate()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
                true
            }
        }

        private fun updateAppFontSummary() {
            val currentValue = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT) ?: "default"
            val values = resources.getStringArray(R.array.app_font_values)
            val labels = resources.getStringArray(R.array.app_font_entries)
            val idx = values.indexOf(currentValue)
            appFont?.summary = if (idx >= 0) labels[idx] else currentValue
        }

        private fun updateCustomFontSummary() {
            val name = CustomFontManager.getFontDisplayName()
            customFontPick?.summary = name ?: getString(R.string.summary_pref_app_font_custom_pick_empty)
            customFontDelete?.apply {
                isEnabled = name != null
                summary = if (name != null) {
                    getString(R.string.summary_pref_app_font_custom_delete_set, name)
                } else {
                    getString(R.string.summary_pref_app_font_custom_delete_empty)
                }
            }
        }

        private fun queryDisplayName(uri: Uri): String? {
            return try {
                requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun initPreferenceSummaries() {
            appIcon?.refreshSummary()
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

        private fun updateTrueBlackState(isNight: Boolean) {
            trueBlack?.isEnabled = isNight
            trueBlack?.summary = if (!isNight) getString(R.string.pref_true_black_only_in_night_mode)
            else getString(R.string.summary_pref_true_black)
            if (!isNight && trueBlack?.isChecked == true) {
                trueBlack?.isChecked = false
                MmkvManager.encodeSettings(AppConfig.PREF_TRUE_BLACK, false)
            }
        }

        private fun isNightModeAfterChange(mode: Int): Boolean = when (mode) {
            1    -> true
            2    -> false
            3    -> !ThemeManager.isAutoDayTime()
            else -> ThemeManager.isDarkMode(requireActivity())
        }
    }
}
