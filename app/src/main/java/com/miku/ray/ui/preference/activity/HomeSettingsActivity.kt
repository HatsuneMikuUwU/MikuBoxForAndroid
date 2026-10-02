package com.miku.ray.ui.preference.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.ui.dialog.TabIconPickerDialog
import com.miku.ray.util.SearchBarChipMode
import com.miku.ray.util.TabIconPickerAdapter
import com.miku.ray.ui.weather.WeatherHelper

/** Home screen behaviour: list, search-bar chip, quick actions, toolbar. */
class HomeSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_home), subtitle = getString(R.string.subtitle_ui_home))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, HomeSettingsFragment())
            .commit()
        }
    }

    class HomeSettingsFragment : PreferenceFragmentCompat() {

        private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (SearchBarChipMode.current() in setOf(
                    SearchBarChipMode.WEATHER,
                    SearchBarChipMode.DUAL_SWIPE
            )) {
                WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
            }
        }

        private val appLanguage by lazy { findPreference<ListPreference>(AppConfig.PREF_LANGUAGE) }
        private val groupAllTabIcon by lazy { findPreference<Preference>(AppConfig.PREF_GROUP_ALL_TAB_ICON) }
        private val tabBadgeLimit by lazy { findPreference<ListPreference>(AppConfig.PREF_TAB_BADGE_LIMIT) }
        private val searchBarChip by lazy { findPreference<ListPreference>(AppConfig.PREF_SEARCH_BAR_CHIP) }
        private val weatherUnit by lazy { findPreference<ListPreference>(AppConfig.PREF_WEATHER_USE_CELSIUS) }
        private val weatherCustomLocation by lazy { findPreference<EditTextPreference>(AppConfig.PREF_WEATHER_CUSTOM_LOCATION) }
        private val searchChipGradient by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SEARCH_CHIP_GRADIENT) }
        private val toolbarCenterSubtitleMode by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE) }
        private val showRealtimeTrafficIp by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP) }
        private val showIspInfo by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_SHOW_ISP_INFO) }
        private val fabExtended by lazy { findPreference<SwitchPreferenceCompat>(AppConfig.PREF_FAB_EXTENDED) }

        private var tabIconPickerDialog: androidx.appcompat.app.AlertDialog? = null

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore(triggersServiceRestart = false)
            addPreferencesFromResource(R.xml.pref_ui_home)
            SearchBarChipMode.current()
            initPreferenceSummaries()

            setupLanguagePreference()

            toolbarCenterSubtitleMode?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE, newValue as Boolean)
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            fabExtended?.setOnPreferenceChangeListener { _, newValue ->
                MmkvManager.encodeSettings(AppConfig.PREF_FAB_EXTENDED, newValue as Boolean)
                SettingsChangeManager.notifyUiCustomizationChanged()
                true
            }

            searchBarChip?.apply {
                value = SearchBarChipMode.current()
                setOnPreferenceChangeListener { _, newValue ->
                    val mode = SearchBarChipMode.save(newValue.toString())
                    value = mode
                    val selectedIndex = findIndexOfValue(mode)
                    summary = if (selectedIndex >= 0) entries[selectedIndex] else mode
                    if (mode == SearchBarChipMode.WEATHER || mode == SearchBarChipMode.DUAL_SWIPE) {
                        val hasForegroundPermission = ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val shouldRequestLocation = mode == SearchBarChipMode.DUAL_SWIPE ||
                        (!hasForegroundPermission && !WeatherHelper.hasCustomLocation())
                        if (!hasForegroundPermission && shouldRequestLocation) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                        }
                    } else {
                        WeatherHelper.cancelBackgroundUpdates(requireContext())
                    }
                    updateChipPreferenceEnabledState()
                    when (mode) {
                        SearchBarChipMode.WEATHER -> requireContext().snackbarDefault(R.string.pref_search_bar_chip_info_weather, title = getString(R.string.title_alerter_info))
                        SearchBarChipMode.TOTAL_TRAFFIC -> requireContext().snackbarDefault(R.string.pref_search_bar_chip_info_traffic, title = getString(R.string.title_alerter_info))
                        SearchBarChipMode.DUAL_SWIPE -> requireContext().snackbarDefault(R.string.pref_search_bar_chip_info_dual, title = getString(R.string.title_alerter_info))
                    }
                    true
                }
            }

            weatherUnit?.setOnPreferenceChangeListener { pref, newValue ->
                val valueStr = newValue.toString()
                (pref as? ListPreference)?.let { lp ->
                    val idx = lp.findIndexOfValue(valueStr)
                    lp.summary = if (idx >= 0) lp.entries[idx] else valueStr
                }
                MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_USE_CELSIUS, valueStr)
                true
            }

            updateWeatherCustomLocationSummary(weatherCustomLocation?.text.orEmpty())
            weatherCustomLocation?.setOnPreferenceChangeListener { _, newValue ->
                val raw = (newValue as? String)?.trim().orEmpty()
                MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION, raw)
                WeatherHelper.clearCustomLocationCache()
                updateWeatherCustomLocationSummary(raw)
                if (SearchBarChipMode.current() in setOf(
                        SearchBarChipMode.WEATHER,
                        SearchBarChipMode.DUAL_SWIPE
                )) {
                    WeatherHelper.scheduleBackgroundUpdates(requireContext(), forceReschedule = true)
                }
                true
            }

            updateChipPreferenceEnabledState()

            updateShowIspInfoEnabledState()
            showRealtimeTrafficIp?.setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                MmkvManager.encodeSettings(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, checked)
                showIspInfo?.isEnabled = !checked
                showIspInfo?.summary = if (checked) {
                    getString(
                        R.string.summary_pref_disabled_realtime_traffic_ip,
                        getString(R.string.title_pref_show_realtime_traffic_ip)
                    )
                } else {
                    getString(R.string.summary_pref_show_isp_info)
                }
                true
            }

            updateGroupAllTabIconSummary()
            groupAllTabIcon?.setOnPreferenceClickListener {
                val currentIcon = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON)
                tabIconPickerDialog = TabIconPickerDialog(
                    context      = requireContext(),
                    currentIcon  = currentIcon,
                    onSelected   = { iconName ->
                        MmkvManager.encodeSettings(AppConfig.PREF_GROUP_ALL_TAB_ICON, iconName)
                        SettingsChangeManager.makeSetupGroupTab()
                        updateGroupAllTabIconSummary()
                    }
                ).show()
                true
            }

            tabBadgeLimit?.setOnPreferenceChangeListener { pref, newValue ->
                (pref as? ListPreference)?.let { lp ->
                    val index = lp.findIndexOfValue(newValue as? String)
                    if (index >= 0) {
                        lp.summary = lp.entries?.getOrNull(index)
                    }
                }
                SettingsChangeManager.makeSetupGroupTab()
                true
            }

            CategoryStyleHelper.applyToFragment(this)
        }

        override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        private fun setupLanguagePreference() {
            val languageValues = resources.getStringArray(R.array.language_select_value)
            val languageLabels = resources.getStringArray(R.array.language_select)

            fun labelFor(tag: String): CharSequence {
                val idx = languageValues.indexOf(tag)
                return if (idx >= 0) languageLabels[idx] else tag
            }

            val currentTag = when (val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                "id" -> "in"
                else -> tag
            }
            val resolvedTag = if (currentTag in languageValues) currentTag else ""

            appLanguage?.apply {
                value = resolvedTag
                summary = labelFor(resolvedTag)
                setOnPreferenceChangeListener { _, newValue ->
                    val newTag = newValue as String
                    AppCompatDelegate.setApplicationLocales(
                        if (newTag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(newTag)
                    )
                    requireContext().sendBroadcast(
                        Intent(AppConfig.BROADCAST_ACTION_TRAFFIC_WIDGET_REFRESH)
                        .setPackage(requireContext().packageName)
                    )
                    summary = labelFor(newTag)
                    value = newTag
                    true
                }
            }
        }

        private fun updateGroupAllTabIconSummary() {
            val iconName = MmkvManager.decodeSettingsString(AppConfig.PREF_GROUP_ALL_TAB_ICON)
            if (iconName.isNullOrEmpty()) {
                groupAllTabIcon?.summary = getString(R.string.sub_tab_icon_none)
                groupAllTabIcon?.setIcon(com.miku.ray.remixicon.R.drawable.rmx_apps_line)
            } else {
                groupAllTabIcon?.summary = TabIconPickerAdapter.labelFor(iconName)
                val resId = resources.getIdentifier(iconName, "drawable", requireContext().packageName)
                if (resId != 0) groupAllTabIcon?.setIcon(resId)
            }
        }

        private fun updateWeatherSubPrefsEnabled(weatherOn: Boolean) {
            weatherUnit?.isEnabled = weatherOn
            weatherCustomLocation?.isEnabled = weatherOn
        }

        private fun updateWeatherCustomLocationSummary(raw: String) {
            val pref = weatherCustomLocation ?: return
            pref.summary = if (raw.isNotBlank()) {
                raw
            } else {
                val entry = WeatherHelper.getCachedWeatherEntry()
                if (entry != null && (entry.latitude != 0.0 || entry.longitude != 0.0)) {
                    getString(
                        R.string.pref_weather_custom_location_summary_current_coords,
                        entry.latitude, entry.longitude
                    )
                } else {
                    getString(R.string.pref_weather_custom_location_summary_auto)
                }
            }
        }

        private fun updateChipPreferenceEnabledState() {
            val mode = SearchBarChipMode.current()
            searchBarChip?.value = mode
            searchChipGradient?.isEnabled = mode != SearchBarChipMode.DISABLED
            updateWeatherSubPrefsEnabled(
                mode == SearchBarChipMode.WEATHER || mode == SearchBarChipMode.DUAL_SWIPE
            )
        }

        private fun updateShowIspInfoEnabledState() {
            val realtimeTrafficOn = showRealtimeTrafficIp?.isChecked == true
            showIspInfo?.isEnabled = !realtimeTrafficOn
            showIspInfo?.summary = if (realtimeTrafficOn) {
                getString(
                    R.string.summary_pref_disabled_realtime_traffic_ip,
                    getString(R.string.title_pref_show_realtime_traffic_ip)
                )
            } else {
                getString(R.string.summary_pref_show_isp_info)
            }
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

        override fun onDestroyView() {
            tabIconPickerDialog?.dismiss()
            tabIconPickerDialog = null
            super.onDestroyView()
        }
    }
}
