package com.miku.ray.ui.preference.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.ui.perappproxy.PerAppProxyActivity
import com.miku.ray.ui.preference.CategoryStyleHelper

class VpnSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_vpn_settings), subtitle = getString(R.string.subtitle_vpn_settings))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, com.miku.ray.MikuSettings.impl?.vpnFragment() ?: VpnSettingsFragment())
            .commit()
        }
    }

    open class VpnSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore()
            addPreferencesFromResource(R.xml.pref_vpn_settings)
            CategoryStyleHelper.applyToFragment(this)
            fun bind(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val pref = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> bind(pref)
                        is EditTextPreference -> pref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        is ListPreference -> pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                    }
                }
            }
            bind(preferenceScreen)
            findPreference<EditTextPreference>(AppConfig.PREF_VPN_MTU)?.setOnPreferenceChangeListener { _, value ->
                val valid = (value as String).toIntOrNull() in 1280..9000
                if (!valid) Toast.makeText(context, R.string.mihomo_invalid_value, Toast.LENGTH_SHORT).show()
                valid
            }
            findPreference<Preference>(AppConfig.PREF_NAVIGATE_PER_APP_PROXY_SETTINGS)?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), PerAppProxyActivity::class.java))
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }
    }
}
