package com.miku.ray.util

import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager

object SearchBarChipMode {
    const val DISABLED = "disabled"
    const val WEATHER = "weather"
    const val TOTAL_TRAFFIC = "total_traffic"
    const val DUAL_SWIPE = "dual_swipe"

    private const val LEGACY_WEATHER_KEY = "pref_show_weather_chip"
    private const val LEGACY_TOTAL_TRAFFIC_KEY = "pref_show_total_traffic_chip"
    private val VALID_VALUES = setOf(DISABLED, WEATHER, TOTAL_TRAFFIC, DUAL_SWIPE)

    fun current(): String {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_SEARCH_BAR_CHIP)
        if (stored != null && stored in VALID_VALUES) return stored

        val migrated = when {
            MmkvManager.decodeSettingsBool(LEGACY_WEATHER_KEY, false) -> WEATHER
            MmkvManager.decodeSettingsBool(LEGACY_TOTAL_TRAFFIC_KEY, false) -> TOTAL_TRAFFIC
            else -> DISABLED
        }
        MmkvManager.encodeSettings(AppConfig.PREF_SEARCH_BAR_CHIP, migrated)
        return migrated
    }

    fun save(value: String): String {
        val normalized = if (value in VALID_VALUES) value else DISABLED
        MmkvManager.encodeSettings(AppConfig.PREF_SEARCH_BAR_CHIP, normalized)
        return normalized
    }

    fun currentDualSelection(): String {
        val stored = MmkvManager.decodeSettingsString(
            AppConfig.PREF_SEARCH_BAR_CHIP_DUAL_SELECTION,
            WEATHER
        )
        return if (stored == TOTAL_TRAFFIC) TOTAL_TRAFFIC else WEATHER
    }

    fun saveDualSelection(value: String): String {
        val normalized = if (value == TOTAL_TRAFFIC) TOTAL_TRAFFIC else WEATHER
        MmkvManager.encodeSettings(AppConfig.PREF_SEARCH_BAR_CHIP_DUAL_SELECTION, normalized)
        return normalized
    }
}
