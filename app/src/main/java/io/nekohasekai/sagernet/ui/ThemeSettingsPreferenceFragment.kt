package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.DPIController
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.DpiEditTextPreference
import java.util.*

class ThemeSettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var dynamicSwitch: SwitchPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.theme_preferences)

        // App Theme
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

        // Night Theme
        val nightTheme = findPreference<SimpleMenuPreference>(Key.NIGHT_THEME)!!
        nightTheme.setOnPreferenceChangeListener { _, newTheme ->
            Theme.currentNightMode = (newTheme as String).toInt()
            Theme.applyNightTheme()
            true
        }

        // Dynamic Theme
        dynamicSwitch = findPreference("dynamic_theme_switch")!!
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

        // Bold Font
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

        // True Black
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

        // Sound Connect
        val soundConnectSwitch = findPreference<SwitchPreference>("sound_connect")
        soundConnectSwitch?.apply {
            isChecked = DataStore.soundOnConnect
            setOnPreferenceChangeListener { _, newValue ->
                DataStore.soundOnConnect = newValue as Boolean
                true
            }
        }

        // DPI Preference
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

        // Language
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

        // FAB Style
        findPreference<SimpleMenuPreference>("fab_style")!!.setOnPreferenceChangeListener { _, _ ->
            requireActivity().apply {
                finish()
                startActivity(intent)
            }
            true
        }
        
     // Banner home show or hide
     val layoutController: SwitchPreference? = findPreference("show_banner_layout")
          layoutController?.apply {
              isChecked = DataStore.showBannerLayout
              setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                  val show = newValue as Boolean
                  DataStore.showBannerLayout = show
                  true
             }
        }
          
        // Splash Screen controller
        val splashController: SwitchPreference? = findPreference("show_splash_screen")
        splashController?.apply {
            isChecked = DataStore.showSplashScreen
            setOnPreferenceChangeListener { _: Preference, newValue: Any ->
                val showSplash = newValue as Boolean
                DataStore.showSplashScreen = showSplash
                true
            }
        }

     // IP test controller bottom bar
     val ipTestController: SwitchPreference? = findPreference("connection_test_with_ip")
        ipTestController?.apply {
            isChecked = DataStore.connectionTestWithIp
            setOnPreferenceChangeListener { _, newValue ->
                val isEnabled = newValue as Boolean
                DataStore.connectionTestWithIp = isEnabled
                true
            }
        }
    }
}
