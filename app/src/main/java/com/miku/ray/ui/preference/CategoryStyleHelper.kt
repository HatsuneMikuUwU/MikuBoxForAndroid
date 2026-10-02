package com.miku.ray.ui.preference

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

object CategoryStyleHelper {

    fun layoutForStyle(styleValue: String?): Int = when (styleValue) {
        "miku"  -> R.layout.uwu_preference_category_miku_1
        "miku2"  -> R.layout.uwu_preference_category_miku_2
        "teto"  -> R.layout.uwu_preference_category_teto_1
        "teto2"  -> R.layout.uwu_preference_category_teto_2
        "neru"  -> R.layout.uwu_preference_category_neru
        "gradient" -> R.layout.uwu_preference_category_gradient
        "basic" -> R.layout.uwu_preference_category_basic
        "cherry_pop" -> R.layout.uwu_preference_category_cherry_pop
        "rabbit_hole" -> R.layout.uwu_preference_category_rabbit_hole
        "mesmerizer" -> R.layout.uwu_preference_category_mesmerizer
        "sakura" -> R.layout.uwu_preference_category_sakura
        "magical_mirai_2024" -> R.layout.uwu_preference_category_magical_mirai_2024
        "deep_sea_girl" -> R.layout.uwu_preference_category_deep_sea_girl
        "snow_miku_2025" -> R.layout.uwu_preference_category_snow_miku_2025
        "symphony_2022" -> R.layout.uwu_preference_category_symphony_2022
        "racing_miku_2025" -> R.layout.uwu_preference_category_racing_miku_2025
        "cinnamiku" -> R.layout.uwu_preference_category_cinnamiku
        "retry_now" -> R.layout.uwu_preference_category_retry_now
        else      -> R.layout.uwu_preference_category_gradient
    }

    fun applyToGroup(styleValue: String?, group: PreferenceGroup) {
        val layout = layoutForStyle(styleValue)
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) pref.layoutResource = layout
            if (pref is PreferenceGroup) applyToGroup(styleValue, pref)
        }
    }

    fun applyToFragment(fragment: PreferenceFragmentCompat) {
        val saved = MmkvManager.decodeSettingsString(AppConfig.PREF_CATEGORY_STYLE, "gradient")
        fragment.preferenceScreen?.let { applyToGroup(saved, it) }
    }
}
