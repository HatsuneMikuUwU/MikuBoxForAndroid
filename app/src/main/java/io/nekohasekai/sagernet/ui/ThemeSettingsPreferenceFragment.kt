package io.nekohasekai.sagernet.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import com.yalantis.ucrop.UCrop
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DPIController
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.showBlur
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.CustomBannerPreference
import moe.matsuri.nb4a.ui.DpiEditTextPreference
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread
import java.util.Locale

class ThemeSettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var dynamicSwitch: SwitchPreference

    private val pickBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    processGifSelection(uri, "home_banner_", "custom_banner_uri", R.string.custom_banner_set)
                } else {
                    startCropActivity(uri)
                }
            }
        }

    private val cropBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("custom_banner_uri", null)
                        deleteOldFile(oldUriString)

                        val savedUri = saveToCache(cacheUri, "home_banner_")
                        DataStore.configurationStore.putString("custom_banner_uri", savedUri.toString())
                        snackbar(R.string.custom_banner_set).show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!) ?: Throwable("Unknown UCrop error")
                Logs.e("Cropping error: ", cropError)
            }
        }
        
        private val pickBannerBlurImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    processGifSelection(uri, "home_banner_blur_", "custom_banner_blur_uri", R.string.custom_banner_set)
                } else {
                    startCropBlurActivity(uri)
                }
            }
        }

    private val cropBannerBlurImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("custom_banner_blur_uri", null)
                        deleteOldFile(oldUriString)

                        val savedUri = saveToCache(cacheUri, "home_banner_blur_")
                        DataStore.configurationStore.putString("custom_banner_blur_uri", savedUri.toString())
                        snackbar(R.string.custom_banner_set).show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!) ?: Throwable("Unknown UCrop error")
                Logs.e("Cropping error: ", cropError)
            }
        }

    private val pickProfileBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                startCropProfileActivity(uri)
            }
        }

    private val cropProfileBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("profile_banner_uri", null)
                        deleteOldFile(oldUriString)

                        val savedUri = saveToCache(cacheUri, "profile_banner_")
                        DataStore.configurationStore.putString("profile_banner_uri", savedUri.toString())
                        snackbar(R.string.custom_banner_profile_set).show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save profile banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!) ?: Throwable("Unknown UCrop error")
                Logs.e("Cropping error: ", cropError)
            }
        }

    private val pickSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    processGifSelection(uri, "sheet_banner_", "custom_sheet_banner_uri", R.string.custom_banner_set)
                } else {
                    startCropSheetActivity(uri)
                }
            }
        }

    private val cropSheetBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("custom_sheet_banner_uri", null)
                        deleteOldFile(oldUriString)

                        val savedUri = saveToCache(cacheUri, "sheet_banner_")
                        DataStore.configurationStore.putString("custom_sheet_banner_uri", savedUri.toString())
                        snackbar(R.string.custom_banner_set).show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save sheet banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!) ?: Throwable("Unknown UCrop error")
                Logs.e("Cropping error: ", cropError)
            }
        }

    private val pickPreferenceBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (isGif(uri)) {
                    processGifSelection(uri, "preference_banner_", "custom_preference_banner_uri", R.string.custom_banner_set)
                } else {
                    startCropPreferenceActivity(uri)
                }
            }
        }

    private val cropPreferenceBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("custom_preference_banner_uri", null)
                        deleteOldFile(oldUriString)

                        val savedUri = saveToCache(cacheUri, "preference_banner_")
                        DataStore.configurationStore.putString("custom_preference_banner_uri", savedUri.toString())
                        snackbar(R.string.custom_banner_set).show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save preference banner", e)
                        snackbar("Failed to save: ${e.message}").show()
                    }
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!) ?: Throwable("Unknown UCrop error")
                Logs.e("Cropping error: ", cropError)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.theme_preferences)
        
        val disableBottomSheetSwitch = findPreference<SwitchPreference>("disable_bottom_sheet")
        disableBottomSheetSwitch?.apply {
            isChecked = DataStore.disableBottomSheet
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.disableBottomSheet = newValue as Boolean
                updateSheetBannerState()
                true
            }
        }

        val disableBottomSheetHomeSwitch = findPreference<SwitchPreference>("disable_bottom_sheet_home")
        disableBottomSheetHomeSwitch?.apply {
            isChecked = DataStore.disableBottomSheetHome
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.disableBottomSheetHome = newValue as Boolean
                updateSheetBannerState()
                true
            }
        }
        
        val disableParticlesSheetSwitch = findPreference<SwitchPreference>("disable_particles_sheet")
        disableParticlesSheetSwitch?.apply {
            isChecked = DataStore.disableParticlesSheet
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.disableParticlesSheet = newValue as Boolean
                true
            }
        }
        
        updateSheetBannerState()

        findPreference<CustomBannerPreference>("key_check_update")?.setOnPreferenceClickListener {
            val jsonUrl = "https://www.nkbe.top/mikubox/update.json"
            
            snackbar(getString(R.string.check_update_checking)).show()
            
            thread {
                try {
                    val jsonStr = URL(jsonUrl).readText()
                    val jsonObject = JSONObject(jsonStr)
                    
                    val remoteVersion = jsonObject.optString("latestVersion", "")
                    val remotePreVersion = jsonObject.optString("latestPreVersion", "") 
                    val downloadUrl = jsonObject.optString("url", "")
                    
                    val localVersion = BuildConfig.VERSION_NAME
                    val localPreVersion = runCatching { BuildConfig.PRE_VERSION_NAME }.getOrDefault("") ?: ""
                    
                    var hasUpdate = false

                    if (remoteVersion.isNotEmpty() && remoteVersion != localVersion) {
                        hasUpdate = true
                    } else {
                        if (remotePreVersion.isNotEmpty() && remotePreVersion != localPreVersion) {
                            hasUpdate = true
                        }
                    }
                    
                    val displayRemote = if (remotePreVersion.isNotEmpty()) "$remoteVersion-$remotePreVersion" else remoteVersion
                    val displayLocal = if (localPreVersion.isNotEmpty()) "$localVersion-$localPreVersion" else localVersion

                    activity?.runOnUiThread {
                        if (hasUpdate) {
                             MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.update_available_title)
                                .setMessage(getString(R.string.update_available_message, displayRemote, displayLocal))
                                .setPositiveButton(R.string.action_update_now) { _, _ ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        snackbar(getString(R.string.error_open_link)).show()
                                    }
                                }
                                .setNegativeButton(R.string.action_later, null)
                                .showBlur()
                        } else {
                            snackbar(getString(R.string.update_not_available_message, displayLocal)).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        snackbar(getString(R.string.check_update_failed, e.message)).show()
                    }
                }
            }
            true
        }

        val styleValue = DataStore.categoryStyle
        preferenceScreen?.let { screen ->
            updateAllCategoryStyles(styleValue, screen)
        }
        
        val customProfileNamePref = findPreference<EditTextPreference>("custom_profile_name")
        customProfileNamePref?.apply {
            val currentName = DataStore.customProfileName
            text = currentName
            summary = if (currentName.isEmpty()) getString(R.string.uwu_banner_title) else currentName
            setOnPreferenceChangeListener { _, newValue ->
                val newName = newValue.toString()
                DataStore.customProfileName = newName
                summary = if (newName.isEmpty()) getString(R.string.uwu_banner_title) else newName
                true
            }
        }

        val appTheme = findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        appTheme.setOnPreferenceChangeListener { _, newTheme ->
            if (DataStore.serviceState.started) SagerNet.reloadService()
            val theme = Theme.getTheme(newTheme as Int)
            requireActivity().apply {
                setTheme(theme)
                ActivityCompat.recreate(this)
            }
            true
        }

        val nightTheme = findPreference<SimpleMenuPreference>(Key.NIGHT_THEME)!!
        nightTheme.setOnPreferenceChangeListener { _, newTheme ->
            Theme.currentNightMode = (newTheme as String).toInt()
            Theme.applyNightTheme()
            true
        }

        dynamicSwitch = findPreference("dynamic_theme_switch")!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isDynamicInitially = DataStore.appTheme == Theme.DYNAMIC
            dynamicSwitch.isChecked = isDynamicInitially
            appTheme.isEnabled = !isDynamicInitially
            var lastAppTheme = DataStore.lastAppTheme
            if (lastAppTheme == 0) {
                lastAppTheme = Theme.TEAL
                DataStore.lastAppTheme = lastAppTheme
            }
            dynamicSwitch.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val isDynamic = newValue as Boolean
                    if (isDynamic) {
                        DataStore.lastAppTheme = DataStore.appTheme
                        DataStore.appTheme = Theme.DYNAMIC
                    } else {
                        DataStore.appTheme =
                            DataStore.lastAppTheme.takeIf { it != Theme.DYNAMIC } ?: Theme.TEAL
                    }
                    Theme.apply(requireContext().applicationContext)
                    appTheme.isEnabled = !isDynamic
                    requireActivity().recreate()
                    true
                }
        } else {
            dynamicSwitch.isEnabled = false
            dynamicSwitch.isChecked = false
            dynamicSwitch.summary = getString(R.string.dynamic_theme_min_android_12)
            appTheme.isEnabled = true
        }
        
        val appFont = findPreference<SimpleMenuPreference>("app_font_pref")
        appFont?.setOnPreferenceChangeListener { _, newValue ->
            DataStore.appFont = newValue as String
            requireActivity().recreate()
            true
        }
        
        val fontScalePref = findPreference<SeekBarPreference>("app_font_scale")
        fontScalePref?.apply {
            min = 50
            max = 150
            seekBarIncrement = 5
            value = DataStore.fontSize
            showSeekBarValue = true 
   
            setOnPreferenceChangeListener { _, newValue ->
                val newSize = newValue as Int
                DataStore.fontSize = newSize
                requireActivity().recreate()
                true
            }
            
            setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_font_scale_title)
                    .setMessage(R.string.reset_font_scale_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        val defaultSize = 100
                        if (DataStore.fontSize != defaultSize) {
                            DataStore.fontSize = defaultSize
                            value = defaultSize
                            requireActivity().recreate()
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
                true
            }
        }

        val boldFontSwitch = findPreference<SwitchPreference>("bold_font_switch")
        boldFontSwitch?.apply {
            isChecked = DataStore.boldFontEnabled
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.boldFontEnabled = newValue as Boolean
                Theme.apply(requireContext().applicationContext)
                requireActivity().recreate()
                true
            }
        }

        val trueBlackSwitch = findPreference<SwitchPreference>("true_dark_enabled")
        trueBlackSwitch?.apply {
            isChecked = DataStore.trueBlackEnabled
            val isNightModeActive = Theme.usingNightMode()
            isEnabled = isNightModeActive
            summary = if (!isNightModeActive) {
                getString(R.string.pref_true_black_only_in_night_mode)
            } else {
                getString(R.string.pref_true_black_summary)
            }
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                DataStore.trueBlackEnabled = enabled
                Theme.apply(requireContext().applicationContext)
                requireActivity().recreate()
                true
            }
            nightTheme.setOnPreferenceChangeListener { _, newValue ->
                val newMode = (newValue as String).toInt()
                Theme.currentNightMode = newMode
                Theme.applyNightTheme()
                val nowNight = Theme.usingNightMode()
                isEnabled = nowNight
                summary = if (nowNight) {
                    getString(R.string.pref_true_black_summary)
                } else {
                    getString(R.string.pref_true_black_only_in_night_mode)
                }
                if (!nowNight && DataStore.trueBlackEnabled) {
                    DataStore.trueBlackEnabled = false
                    isChecked = false
                }
                true
            }
        }
        
        val soundConnectSwitch = findPreference<SwitchPreference>("sound_connect")
        soundConnectSwitch?.apply {
            isChecked = DataStore.soundOnConnect
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.soundOnConnect = newValue as Boolean
                true
            }
        }

        val weatherController: SwitchPreference? = findPreference("show_weather_info")
        weatherController?.apply {
            isChecked = DataStore.showWeatherInfo
            setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                val showWeather = newValue as Boolean

                if (showWeather) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.permission_location_title)
                            .setMessage(R.string.permission_location_message)
                            .setPositiveButton(R.string.permission_grant) { _, _ ->
                                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
                            }
                            .setNegativeButton(R.string.permission_deny) { dialog, _ ->
                                dialog.dismiss()
                                this.isChecked = false
                            }
                            .setCancelable(false)
                            .showBlur()
                    } else {
                        DataStore.showWeatherInfo = true
                    }
                } else {
                    DataStore.showWeatherInfo = false
                }
                true
            }
        }

        val manualWeatherSwitch = findPreference<SwitchPreference>("manual_weather_enabled")
        val manualWeatherCity = findPreference<EditTextPreference>("manual_weather_city")

        manualWeatherSwitch?.apply {
            isChecked = DataStore.manualWeatherEnabled
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                DataStore.manualWeatherEnabled = enabled
                if (!enabled) {
                    DataStore.manualWeatherCity = ""
                    manualWeatherCity?.text = ""
                }
                true
            }
        }

        manualWeatherCity?.apply {
            text = DataStore.manualWeatherCity
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.manualWeatherCity = newValue.toString()
                true
            }
        }

        val weatherConditionSwitch = findPreference<SwitchPreference>("show_weather_condition")
        weatherConditionSwitch?.apply {
            isChecked = DataStore.showWeatherCondition
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.showWeatherCondition = newValue as Boolean
                true
            }
        }

        val hideWeatherCitySwitch = findPreference<SwitchPreference>("hide_weather_city")
        hideWeatherCitySwitch?.apply {
            isChecked = DataStore.hideWeatherCity
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.hideWeatherCity = newValue as Boolean
                true
            }
        }

        val dpiPref = findPreference<DpiEditTextPreference>("custom_dpi")
        dpiPref?.apply {
            val defaultDpi = resources.displayMetrics.densityDpi
            val currentDpi = DataStore.dpiValue.takeIf { it > 0 } ?: defaultDpi
            text = currentDpi.toString()
            setOnBindEditTextListener { editText ->
                editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
            }
            setOnPreferenceChangeListener { _, newValue ->
                val dpi = (newValue as String).toIntOrNull() ?: currentDpi
                val clamped = dpi.coerceIn(200, 500)
                DataStore.dpiValue = clamped
                DPIController.applyDpi(requireContext(), clamped)
                requireActivity().recreate()
                true
            }
        }

        fun getLanguageDisplayName(code: String): String {
            return when (code) {
                "" -> getString(R.string.language_system_default)
                "en-US" -> getString(R.string.language_en_display_name)
                "id" -> getString(R.string.language_id_display_name)
                "zh-Hans-CN" -> getString(R.string.language_zh_Hans_CN_display_name)
                "zh-TW", "zh-Hant-TW" -> getString(R.string.language_zh_Hant_TW_display_name)
                "ru", "ru-RU" -> getString(R.string.language_ru_display_name)
                "tr-TR" -> getString(R.string.language_tr_display_name)
                "ja-JP" -> getString(R.string.language_ja_display_name)
                else -> Locale.forLanguageTag(code).displayName
            }
        }

        val appLanguage = findPreference<SimpleMenuPreference>(Key.APP_LANGUAGE)
        appLanguage?.apply {
            val locale = when (val value = AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                "in" -> "id"
                "zh-Hant-TW" -> "zh-TW"
                "ru-RU" -> "ru"
                else -> value
            }
            summary = getLanguageDisplayName(locale)
            value = if (locale in resources.getStringArray(R.array.language_value)) locale else ""
            setOnPreferenceChangeListener { _, newValue ->
                val newLocale = newValue as String
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
                summary = getLanguageDisplayName(newLocale)
                value = newLocale
                true
            }
        }

        findPreference<SimpleMenuPreference>("fab_style")!!.setOnPreferenceChangeListener { _, _ ->
            requireActivity().apply {
                finish()
                startActivity(intent)
            }
            true
        }

        val layoutController: SwitchPreference? = findPreference("show_banner_layout")
        layoutController?.apply {
            isChecked = DataStore.showBannerLayout
            setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                val show = newValue as Boolean
                DataStore.showBannerLayout = show
                true
            }
        }
        
        val bannerBlurSwitch = findPreference<SwitchPreference>("enable_home_banner_blur")
        bannerBlurSwitch?.apply {
            isChecked = DataStore.blurBannerHome
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.blurBannerHome = newValue as Boolean
                true
            }
        }

        val changeBannerPref = findPreference<Preference>("action_change_banner_image")
        changeBannerPref?.setOnPreferenceClickListener {
            pickBannerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        }
        
        val changeBannerBlurPref = findPreference<Preference>("action_change_banner_blur_image")
        changeBannerBlurPref?.setOnPreferenceClickListener {
            pickBannerBlurImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        }

        val deleteBannerPref = findPreference<Preference>("action_delete_banner_image")
        deleteBannerPref?.setOnPreferenceClickListener {
            val savedUriString = DataStore.configurationStore.getString("custom_banner_uri", null)
            val secondUriString = DataStore.configurationStore.getString("custom_banner_blur_uri", null)

            if (!savedUriString.isNullOrEmpty() || !secondUriString.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_custom_banner_title)
                    .setMessage(R.string.delete_custom_banner_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        
                        if (!savedUriString.isNullOrEmpty()) {
                            deleteOldFile(savedUriString)
                            DataStore.configurationStore.putString("custom_banner_uri", null)
                        }

                        if (!secondUriString.isNullOrEmpty()) {
                            deleteOldFile(secondUriString)
                            DataStore.configurationStore.putString("custom_banner_blur_uri", null)
                        }

                        snackbar(R.string.custom_banner_removed).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
            } else {
                snackbar(R.string.no_custom_banner_to_remove).show()
            }
            true
        }


        val changeProfileBannerPref = findPreference<Preference>("action_change_profile_banner_image")
        changeProfileBannerPref?.setOnPreferenceClickListener {
            pickProfileBannerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        }
        
        val profileShapeController = findPreference<SimpleMenuPreference>("profile_banner_shape")
        profileShapeController?.apply {
            summary = "%s"
            value = DataStore.profileBannerShape
            setOnPreferenceChangeListener { _, newValue ->
                val newShape = newValue as String
                DataStore.profileBannerShape = newShape
                true
            }
        }

        val deleteProfileBannerPref = findPreference<Preference>("action_delete_profile_banner_image")
        deleteProfileBannerPref?.setOnPreferenceClickListener {
            val savedUriString = DataStore.configurationStore.getString("profile_banner_uri", null)
            if (!savedUriString.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_custom_banner_title)
                    .setMessage(R.string.delete_custom_banner_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        deleteOldFile(savedUriString)
                        DataStore.configurationStore.putString("profile_banner_uri", null)
                        snackbar(R.string.custom_banner_removed).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
            } else {
                snackbar(R.string.no_custom_banner_to_remove).show()
            }
            true
        }

        val changeSheetBannerPref = findPreference<Preference>("action_change_sheet_banner_image")
        changeSheetBannerPref?.setOnPreferenceClickListener {
            pickSheetBannerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        }

        val deleteSheetBannerPref = findPreference<Preference>("action_delete_sheet_banner_image")
        deleteSheetBannerPref?.setOnPreferenceClickListener {
            val savedUriString = DataStore.configurationStore.getString("custom_sheet_banner_uri", null)
            if (!savedUriString.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_custom_banner_title)
                    .setMessage(R.string.delete_custom_banner_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        deleteOldFile(savedUriString)
                        DataStore.configurationStore.putString("custom_sheet_banner_uri", null)
                        snackbar(R.string.custom_banner_removed).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
            } else {
                snackbar(R.string.no_custom_banner_to_remove).show()
            }
            true
        }
        
        val bannerPreferenceToggle = findPreference<SwitchPreference>("show_banner_preference")
        bannerPreferenceToggle?.apply {
            isChecked = DataStore.showBannerPreference
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                DataStore.showBannerPreference = isEnabled
                true
            }
        }
        
        val disableParticlesPrefSwitch = findPreference<SwitchPreference>("disable_particles_pref")
        disableParticlesPrefSwitch?.apply {
            isChecked = DataStore.disableParticlesPref
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.disableParticlesPref = newValue as Boolean
                true
            }
        }

        val changePreferenceBannerPref = findPreference<Preference>("action_change_preference_banner_image")
        changePreferenceBannerPref?.setOnPreferenceClickListener {
            pickPreferenceBannerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        }

        val deletePreferenceBannerPref = findPreference<Preference>("action_delete_preference_banner_image")
        deletePreferenceBannerPref?.setOnPreferenceClickListener {
            val savedUriString = DataStore.configurationStore.getString("custom_preference_banner_uri", null)
            if (!savedUriString.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_custom_banner_title)
                    .setMessage(R.string.delete_custom_banner_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        deleteOldFile(savedUriString)
                        DataStore.configurationStore.putString("custom_preference_banner_uri", null)
                        snackbar(R.string.custom_banner_removed).show()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
            } else {
                snackbar(R.string.no_custom_banner_to_remove).show()
            }
            true
        }

        val splashController: SwitchPreference? = findPreference("show_splash_screen")
        splashController?.apply {
            isChecked = DataStore.showSplashScreen
            setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                val showSplash = newValue as Boolean
                DataStore.showSplashScreen = showSplash
                true
            }
        }
        
        val welcomeAnimSwitch: SwitchPreference? = findPreference("key_show_welcome_anim")
        welcomeAnimSwitch?.apply {
            isChecked = DataStore.showWelcomeAnim
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                DataStore.showWelcomeAnim = isEnabled
                true
            }
        }
        
        val ipTestController: SwitchPreference? = findPreference("connection_test_with_ip")
        ipTestController?.apply {
            isChecked = DataStore.connectionTestWithIp
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                DataStore.connectionTestWithIp = isEnabled
                true
            }
        }

        val ipDisplayStyleController: SwitchPreference? = findPreference("show_ip_in_two_line")
        ipDisplayStyleController?.apply {
            isChecked = DataStore.showIpInTwoLine
            setOnPreferenceChangeListener { _, newValue ->
                val isTwoLines = newValue as Boolean
                DataStore.showIpInTwoLine = isTwoLines
                true
            }
        }

        val styleCategoryController: SimpleMenuPreference? = findPreference("key_category_style_menu")
        styleCategoryController?.setOnPreferenceChangeListener { _, newValue ->
            val styleValue = newValue as String
            preferenceScreen?.let { screen ->
                updateAllCategoryStyles(styleValue, screen)
                listView.adapter?.notifyDataSetChanged()
            }
            true
        }
        
        val iconShapeController = findPreference<SimpleMenuPreference>("preference_icon_shape")
        iconShapeController?.apply {
            summary = "%s"
            value = DataStore.preferenceIconShape
            setOnPreferenceChangeListener { _, newValue ->
                val newShape = newValue as String
                DataStore.preferenceIconShape = newShape
                true
            }
        }

        val layoutOffSwitchIcon = findPreference<SwitchPreference>("uwu_icon")
        layoutOffSwitchIcon?.apply {
            val targetLayoutKey = "uwu_icon"
            val sharedPrefs = requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            val currentValue = sharedPrefs.getInt(targetLayoutKey, 0)
            isChecked = currentValue == 1
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                sharedPrefs.edit().putInt(targetLayoutKey, if (isEnabled) 1 else 0).apply()
                true
            }
        }
        
        val layoutOffSwitchIconProfileServer = findPreference<SwitchPreference>("uwu_icon_profile_server")
        layoutOffSwitchIconProfileServer?.apply {
            val targetLayoutKey = "uwu_icon_profile_server"
            val sharedPrefs = requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            val currentValue = sharedPrefs.getInt(targetLayoutKey, 0)
            isChecked = currentValue == 1
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                sharedPrefs.edit().putInt(targetLayoutKey, if (isEnabled) 1 else 0).apply()
                true
            }
        }

        val layoutOffSwitchSummary = findPreference<SwitchPreference>("uwu_summary")
        layoutOffSwitchSummary?.apply {
            val targetLayoutKey = "uwu_summary"
            val sharedPrefs = requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            val currentValue = sharedPrefs.getInt(targetLayoutKey, 0)
            isChecked = currentValue == 1
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                sharedPrefs.edit().putInt(targetLayoutKey, if (isEnabled) 1 else 0).apply()
                true
            }
        }

        val layoutOffSwitchIconArrow = findPreference<SwitchPreference>("uwu_arrow")
        layoutOffSwitchIconArrow?.apply {
            val targetLayoutKey = "uwu_arrow"
            val sharedPrefs = requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            val currentValue = sharedPrefs.getInt(targetLayoutKey, 0)
            isChecked = currentValue == 1
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                sharedPrefs.edit().putInt(targetLayoutKey, if (isEnabled) 1 else 0).apply()
                true
            }
        }
    } 
    
    private fun updateSheetBannerState() {
        val isBottomSheetDisabled = DataStore.disableBottomSheet
        val isBottomSheetHomeDisabled = DataStore.disableBottomSheetHome
        val shouldEnableActions = !(isBottomSheetDisabled && isBottomSheetHomeDisabled)
        findPreference<Preference>("action_change_sheet_banner_image")?.isEnabled = shouldEnableActions
        findPreference<Preference>("action_delete_sheet_banner_image")?.isEnabled = shouldEnableActions
        findPreference<Preference>("disable_particles_sheet")?.isEnabled = shouldEnableActions
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val weatherController: SwitchPreference? = findPreference("show_weather_info")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DataStore.showWeatherInfo = true
                weatherController?.isChecked = true
            } else {
                DataStore.showWeatherInfo = false
                weatherController?.isChecked = false
            }
        }
    }

    private fun updateAllCategoryStyles(styleValue: String?, group: PreferenceGroup) {
        val newLayout = when (styleValue) {
            "style1" -> R.layout.uwu_preference_category_1
            "style2" -> R.layout.uwu_preference_category_2
            "style3" -> R.layout.uwu_preference_category_3
            "style4" -> R.layout.uwu_preference_category_4
            "style5" -> R.layout.uwu_preference_category_5
            "style6" -> R.layout.uwu_preference_category_6
            "style7" -> R.layout.uwu_preference_category_7
            "style8" -> R.layout.uwu_preference_category_8
            "style9" -> R.layout.uwu_preference_category_9
            "style10" -> R.layout.uwu_preference_category_10
            else -> R.layout.uwu_preference_category_1
        }

        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (preference is PreferenceCategory) {
                preference.layoutResource = newLayout
            }
            if (preference is PreferenceGroup) {
                updateAllCategoryStyles(styleValue, preference)
            }
        }
    }

    private fun startCropActivity(sourceUri: Uri) {
        val destinationFileName = "cropped_banner_temp.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = dp2pxf(120)
        val targetHeightPx = dp2pxf(170)
        
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1080, 1080)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(false)
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropBannerImage.launch(uCrop.getIntent(requireContext()))
    }
    
    private fun startCropBlurActivity(sourceUri: Uri) {
        val destinationFileName = "cropped_banner_temp.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val targetHeightPx = dp2pxf(170)
        
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1920, 1080)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(false)
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropBannerBlurImage.launch(uCrop.getIntent(requireContext()))
    }

    private fun startCropProfileActivity(sourceUri: Uri) {
        val destinationFileName = "cropped_profile_banner_temp.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(true) 
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropProfileBannerImage.launch(uCrop.getIntent(requireContext()))
    }

    private fun startCropSheetActivity(sourceUri: Uri) {
        val destinationFileName = "cropped_sheet_banner_temp.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val targetHeightPx = dp2pxf(150)
        
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1920, 1080)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(false)
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropSheetBannerImage.launch(uCrop.getIntent(requireContext()))
    }
    
    private fun startCropPreferenceActivity(sourceUri: Uri) {
        val destinationFileName = "cropped_preference_banner_temp.jpg"
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))
        
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val targetHeightPx = dp2pxf(300)
        
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(1080, 1080)

        try {
            val options = UCrop.Options()
            options.setDimmedLayerColor(Color.parseColor("#CCFFFFFF"))
            options.setCircleDimmedLayer(false)
            options.setShowCropGrid(true)
            options.setFreeStyleCropEnabled(false)
            uCrop.withOptions(options)
        } catch (e: Exception) {
            Logs.e("Failed to set UCrop theme", e)
        }
        cropPreferenceBannerImage.launch(uCrop.getIntent(requireContext()))
    }

    private fun deleteOldFile(uriString: String?) {
        if (!uriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    if (file.exists()) file.delete()
                } else {
                    try {
                        requireContext().contentResolver.delete(uri, null, null)
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                Logs.w("Failed to delete old banner: $uriString")
            }
        }
    }
    
    private fun isGif(uri: Uri): Boolean {
        return try {
            val mimeType = requireContext().contentResolver.getType(uri)
            mimeType?.contains("gif", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun processGifSelection(uri: Uri, prefix: String, prefKey: String, successMessageRes: Int) {
        try {
            val oldUriString = DataStore.configurationStore.getString(prefKey, null)
            deleteOldFile(oldUriString)

            val savedUri = saveToCache(uri, prefix, ".gif")
            DataStore.configurationStore.putString(prefKey, savedUri.toString())
            snackbar(successMessageRes).show()

        } catch (e: Exception) {
            Logs.e("Failed to save GIF banner", e)
            snackbar("Failed to save: ${e.message}").show()
        }
    }

    @Throws(IOException::class)
    private fun saveToCache(sourceCacheUri: Uri, fileNamePrefix: String, extension: String = ".jpg"): Uri {
        val context = requireContext()
        val timeStamp = System.currentTimeMillis()
        val fileName = "${fileNamePrefix}$timeStamp$extension"
        
        val destFile = File(context.cacheDir, fileName)

        context.contentResolver.openInputStream(sourceCacheUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        try {
            if (sourceCacheUri.scheme == "file") {
                val tempFile = File(sourceCacheUri.path!!)
                if (tempFile.exists() && tempFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                    tempFile.delete()
                }
            }
        } catch (ignored: Exception) {}

        return Uri.fromFile(destFile)
    }
}
