package io.nekohasekai.sagernet.ui

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import com.yalantis.ucrop.UCrop
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DPIController
import io.nekohasekai.sagernet.utils.Theme
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.DpiEditTextPreference
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ThemeSettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var dynamicSwitch: SwitchPreference

    private val pickBannerImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                startCropActivity(uri)
            }
        }

    private val cropBannerImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val cacheUri = UCrop.getOutput(result.data!!)
                if (cacheUri != null) {
                    try {
                        val oldUriString = DataStore.configurationStore.getString("custom_banner_uri", null)
                        if (!oldUriString.isNullOrEmpty()) {
                            try {
                                val oldUri = Uri.parse(oldUriString)
                                requireContext().contentResolver.delete(oldUri, null, null)
                            } catch (e: Exception) {
                                Logs.w("Failed to delete old custom banner", e)
                            }
                        }

                        val publicMediaUri = saveBannerToMediaStore(cacheUri)

                        DataStore.configurationStore.putString("custom_banner_uri", publicMediaUri.toString())

                        (activity as? MainActivity)?.snackbar(R.string.custom_banner_set)?.show()

                    } catch (e: Exception) {
                        Logs.e("Failed to save banner to MediaStore", e)
                        (activity as? MainActivity)?.snackbar("Failed to save: ${e.message}")?.show()
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
                        AlertDialog.Builder(requireContext())
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
                            .show()
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
                else -> Locale.forLanguageTag(code).displayName
            }
        }

        val appLanguage = findPreference<SimpleMenuPreference>(Key.APP_LANGUAGE)
        appLanguage?.apply {
            val locale = when (val value = AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                "in" -> "id"
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

        val bannerHeightPref = findPreference<EditTextPreference>("banner_height")
        bannerHeightPref?.apply {
            setOnBindEditTextListener { editText ->
                editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
            }
            val currentHeight = DataStore.bannerHeight
            text = currentHeight.toString()
            setOnPreferenceChangeListener { _, newValue ->
                val newHeightStr = newValue as String
                val newHeight = newHeightStr.toIntOrNull() ?: 100
                DataStore.bannerHeight = newHeight
                true
            }
        }
     
        val changeBannerPref = findPreference<Preference>("action_change_banner_image")
        changeBannerPref?.setOnPreferenceClickListener {
            pickBannerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
        val heightDp = DataStore.bannerHeight
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val targetHeightPx = dp2pxf(heightDp)
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(screenWidthPx, targetHeightPx)
            .withMaxResultSize(2560, 1440)

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

    @Throws(IOException::class)
    private fun saveBannerToMediaStore(sourceCacheUri: Uri): Uri {
        val resolver = requireContext().contentResolver
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH:mm", Locale.US).format(Date())
        val fileName = "uwu_custom_banner_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MikuBox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newImageUri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create new MediaStore record")

        try {
            resolver.openOutputStream(newImageUri).use { outputStream ->
                if (outputStream == null) throw IOException("Failed to get output stream")
                resolver.openInputStream(sourceCacheUri).use { inputStream ->
                    if (inputStream == null) throw IOException("Failed to get input stream from cache")
                    inputStream.copyTo(outputStream)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(newImageUri, values, null, null)
            return newImageUri
        } catch (e: Exception) {
            resolver.delete(newImageUri, null, null)
            throw e
        } finally {
            val cacheFile = File(sourceCacheUri.path!!)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
}
