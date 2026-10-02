package com.miku.ray.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager

// Preserve MikuRay's exact HCT palette; the Material dependency is pinned in libs.versions.toml.
@android.annotation.SuppressLint("RestrictedApi")
object ThemeManager {

    fun applyTheme(activity: Activity) {
        val isDynamic   = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        val useCustom   = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        val customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        val isTrueBlack = isDarkMode(activity) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        val isDynamicBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
        val bannerColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_BANNER_COLOR, 0)

        var themeApplied = false

        if (isDynamicBanner && bannerColor != 0) {
            val builder = DynamicColorsOptions.Builder()
            .setContentBasedSource(bannerColor)

            if (isTrueBlack) {
                builder.setThemeOverlay(R.style.ThemeOverlay_App_TrueBlack)
            }

            DynamicColors.applyToActivityIfAvailable(activity, builder.build())
            themeApplied = true
        }

        if (!themeApplied) {
            when {
                isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    DynamicColors.applyToActivityIfAvailable(activity)
                }
                useCustom && customColor != 0 -> {
                    applyCustomColorTheme(activity, customColor)
                }
                else -> {
                    val key = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
                    activity.setTheme(getThemeStyleRes(key))
                }
            }
        }

        if (isTrueBlack && !themeApplied) {
            activity.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack, true)
        }
    }

    @StyleRes
    fun getThemeStyleRes(key: String): Int {
        return when (key) {
            "1"  -> R.style.AppTheme_Red
            "2"  -> R.style.AppTheme_Pink
            "3"  -> R.style.AppTheme_Purple
            "4"  -> R.style.AppTheme_DeepPurple
            "5"  -> R.style.AppTheme_Indigo
            "6"  -> R.style.AppTheme_Blue
            "7"  -> R.style.AppTheme_Cyan
            "8"  -> R.style.AppTheme_Teal
            "9"  -> R.style.AppTheme_Green
            "10" -> R.style.AppTheme_LightGreen
            "11" -> R.style.AppTheme_Lime
            "12" -> R.style.AppTheme_Yellow
            "13" -> R.style.AppTheme_Amber
            "14" -> R.style.AppTheme_Orange
            "15" -> R.style.AppTheme_Brown
            "16" -> R.style.AppTheme_BlueGrey
            else -> R.style.AppTheme_Teal
        }
    }

    fun applyCustomColorTheme(activity: Activity, @ColorInt seedColor: Int, isTrueBlack: Boolean = false) {
        val optionsBuilder = DynamicColorsOptions.Builder()
        .setContentBasedSource(seedColor)

        if (isTrueBlack) {
            optionsBuilder.setThemeOverlay(R.style.ThemeOverlay_App_TrueBlack)
        }

        DynamicColors.applyToActivityIfAvailable(activity, optionsBuilder.build())
    }

    fun getDynamicScheme(activity: Activity, @ColorInt seedColor: Int): SchemeTonalSpot {
        val hct = Hct.fromInt(seedColor)
        return SchemeTonalSpot(hct, isDarkMode(activity), 0.0)
    }

    fun isDarkMode(activity: Activity): Boolean {
        val uiMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    const val AUTO_DAY_START_HOUR = 6
    const val AUTO_DAY_END_HOUR = 18

    fun isAutoDayTime(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in AUTO_DAY_START_HOUR until AUTO_DAY_END_HOUR
    }

    fun setAndSaveTheme(activity: Activity, key: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_APP_THEME, key)
        SettingsChangeManager.requestRecreate()
    }

    fun saveCustomColor(activity: Activity, @ColorInt color: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, true)
        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_COLOR, color)
        SettingsChangeManager.requestRecreate()
    }

    fun clearCustomColor(activity: Activity) {
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_COLOR, 0)
        SettingsChangeManager.requestRecreate()
    }
}

fun Context.getColorAttr(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(resId, typedValue, true)
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        typedValue.data
    }
}

fun Context.getColorAttr(attrName: String): Int {
    val resId = resources.getIdentifier(attrName, "attr", packageName)
    return getColorAttr(resId)
}
