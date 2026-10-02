package com.miku.ray.helper

import androidx.preference.PreferenceDataStore
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil

class MmkvPreferenceDataStore(private val triggersServiceRestart: Boolean = true) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return MmkvManager.decodeSettingsString(key, defaultValue)
    }

    override fun putInt(key: String, value: Int) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return MmkvManager.decodeSettingsInt(key, defaultValue)
    }

    override fun putLong(key: String, value: Long) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return MmkvManager.decodeSettingsLong(key, defaultValue)
    }

    override fun putFloat(key: String, value: Float) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return MmkvManager.decodeSettingsFloat(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        MmkvManager.encodeSettings(key, value)
        notifySettingChanged(key)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return MmkvManager.decodeSettingsBool(key, defaultValue)
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (values == null) {
            MmkvManager.encodeSettings(key, null as String?)
        } else {
            MmkvManager.encodeSettings(key, values)
        }
        notifySettingChanged(key)
    }

    override fun getStringSet(key: String, defaultValues: MutableSet<String>?): MutableSet<String>? {
        return MmkvManager.decodeSettingsStringSet(key) ?: defaultValues
    }

    private fun notifySettingChanged(key: String) {
        if (key == AppConfig.PREF_LOGLEVEL) {
            LogUtil.refreshLogLevel()
        }

        if (key == AppConfig.PREF_UI_MODE_NIGHT) {
            SettingsManager.setNightMode()
        }

        if (key == AppConfig.PREF_TRAFFIC_ENABLED ||
            key == AppConfig.PREF_DISABLE_SENSOR_TEXT ||
            key == AppConfig.PREF_NETWORK_SECURITY_ENABLED ||
            key == AppConfig.PREF_DOUBLE_COLUMN_DISPLAY
        ) {
            SettingsChangeManager.makeRefreshDisplayPrefs()
        }

        if (triggersServiceRestart) {
            SettingsChangeManager.makeRestartService()
        }
        SettingsChangeManager.makeSetupGroupTab()
    }
}
