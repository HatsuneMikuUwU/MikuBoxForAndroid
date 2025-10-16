package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.needRestart
import io.nekohasekai.sagernet.ktx.remove
import io.nekohasekai.sagernet.utils.Theme
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.EditConfigPreference
import moe.matsuri.nb4a.ui.LongClickListPreference
import moe.matsuri.nb4a.ui.MTUPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference
    private lateinit var globalCustomConfig: EditConfigPreference
    private lateinit var dynamicSwitch: SwitchPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    private val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)

        val appTheme = findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        appTheme.setOnPreferenceChangeListener { _, newTheme ->
            if (DataStore.serviceState.started) {
                SagerNet.reloadService()
            }
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

        // Set initial state based on DataStore
        dynamicSwitch.isChecked = DataStore.appTheme == Theme.DYNAMIC

        // Listener when user changes state
        dynamicSwitch.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val isDynamic = newValue as Boolean
                DataStore.appTheme = if (isDynamic) Theme.DYNAMIC else Theme.TEAL // fallback
                Theme.apply(requireContext().applicationContext)
                requireActivity().recreate() // Refresh UI for theme to apply immediately
                true
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

    if (appLanguage != null) {
        val locale = when (val value = AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
            "in" -> "id" // handle old Android "in" locale code
            else -> value
        }

        appLanguage.summary = getLanguageDisplayName(locale)
        appLanguage.value = if (locale in resources.getStringArray(R.array.language_value)) locale else ""

        appLanguage.setOnPreferenceChangeListener { _, newValue ->
            val newLocale = newValue as String
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
            appLanguage.summary = getLanguageDisplayName(newLocale)
            appLanguage.value = newLocale
            true
        }
    }

        val mixedPort = findPreference<EditTextPreference>(Key.MIXED_PORT)!!
        mixedPort.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        if (Build.VERSION.SDK_INT < 28) {
            findPreference<Preference>(Key.METERED_NETWORK)?.remove()
        }

        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        val profileTrafficStatistics = findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        val speedInterval = findPreference<SimpleMenuPreference>(Key.SPEED_INTERVAL)!!
        profileTrafficStatistics.isEnabled = speedInterval.value != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            profileTrafficStatistics.isEnabled = newValue.toString() != "0"
            needReload()
            true
        }
        val hideFromRecentApps = findPreference<SwitchPreference>("hide_from_recent_apps")!!
        val enableClashAPI = findPreference<SwitchPreference>(Key.ENABLE_CLASH_API)!!
        enableClashAPI.setOnPreferenceChangeListener { _, newValue ->
            (activity as? MainActivity)?.refreshNavMenu(newValue as Boolean)
            needReload()
            true
        }

        val rulesProvider = findPreference<SimpleMenuPreference>(Key.RULES_PROVIDER)!!
        val rulesGeositeUrl = findPreference<EditTextPreference>("rules_geosite_url")!!
        val rulesGeoipUrl = findPreference<EditTextPreference>("rules_geoip_url")!!
        rulesGeositeUrl.isVisible = DataStore.rulesProvider == 5
        rulesGeoipUrl.isVisible = DataStore.rulesProvider == 5
        rulesProvider.setOnPreferenceChangeListener { _, newValue ->
            val provider = (newValue as String).toInt()
            rulesGeositeUrl.isVisible = provider == 5
            rulesGeoipUrl.isVisible = provider == 5
            true
        }
        
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        serviceMode.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            if (DataStore.serviceState.started) SagerNet.stopService()
            true
        }

        globalCustomConfig = findPreference(Key.GLOBAL_CUSTOM_CONFIG)!!
        globalCustomConfig.useConfigStore(Key.GLOBAL_CUSTOM_CONFIG)

        findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!.let { logLevel ->
            logLevel.dialogLayoutResource = R.layout.layout_loglevel_help
            logLevel.setOnPreferenceChangeListener { _, _ ->
                needRestart()
                true
            }
            logLevel.setOnLongClickListener {
                context?.let { ctx ->
                    val view = EditText(ctx).apply {
                        inputType = EditorInfo.TYPE_CLASS_NUMBER
                        val size = DataStore.logBufSize.takeIf { it > 0 } ?: 50
                        setText(size.toString())
                    }
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Log buffer size (kb)")
                        .setView(view)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            DataStore.logBufSize = view.text.toString().toIntOrNull() ?: 50
                            if (DataStore.logBufSize <= 0) DataStore.logBufSize = 50
                            needRestart()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                true
            }
        }

        // Clear Cache
        val clearCache = findPreference<Preference>("clear_cache")!!
        clearCache.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.clear_cache)
                setMessage(R.string.clear_cache_confirm)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    clearAppCache()
                }
                setNegativeButton(android.R.string.cancel, null)
            }.show()
            true
        }
        
        // Assign reload listeners
        mixedPort.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.APPEND_HTTP_PROXY)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.BYPASS_LAN)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE)!!.onPreferenceChangeListener = reloadListener
        findPreference<MTUPreference>(Key.MTU)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.ENABLE_FAKEDNS)!!.onPreferenceChangeListener = reloadListener
        findPreference<EditTextPreference>(Key.REMOTE_DNS)!!.onPreferenceChangeListener = reloadListener
        findPreference<EditTextPreference>(Key.DIRECT_DNS)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.IPV6_MODE)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.ALLOW_ACCESS)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.RESOLVE_DESTINATION)!!.onPreferenceChangeListener = reloadListener
        findPreference<SimpleMenuPreference>(Key.TUN_IMPLEMENTATION)!!.onPreferenceChangeListener = reloadListener
        findPreference<SwitchPreference>(Key.ACQUIRE_WAKE_LOCK)!!.onPreferenceChangeListener = reloadListener
        findPreference<SimpleMenuPreference>("fab_style")!!.setOnPreferenceChangeListener { _, _ ->
            requireActivity().apply {
                this.finish()
                startActivity(intent)
            }
            true
        }

        // Fixed placement — keep this inside the function
        globalCustomConfig.onPreferenceChangeListener = reloadListener
    }

    override fun onResume() {
        super.onResume()
        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
        if (::globalCustomConfig.isInitialized) {
            globalCustomConfig.notifyChanged()
        }
    }
    
    private fun clearAppCache() {
        try {
            val cacheDir = SagerNet.application.cacheDir
            clearDirFiles(cacheDir, skipFiles = setOf("neko.log"))

            val parentDir = cacheDir.parentFile
            val relativeCache = File(parentDir, "cache")
            if (relativeCache.exists() && relativeCache.isDirectory) {
                clearDirFiles(relativeCache)
            }

            Toast.makeText(requireContext(), R.string.clear_cache_success, Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                needReload()
            }, 500)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.clear_cache_failed, e.message), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun clearDirFiles(dir: File, skipFiles: Set<String> = emptySet()): Boolean {
        if (dir.isDirectory) {
            val children = dir.list() ?: return true
            for (child in children) {
                val childFile = File(dir, child)
                if (child == "neko.log") {
                    try {
                        childFile.writeText("")
                        continue
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (child in skipFiles) continue
                if (childFile.isDirectory) {
                    clearDirFiles(childFile, skipFiles)
                } else {
                    childFile.delete()
                }
            }
            return true
        }
        return false
    }
}

