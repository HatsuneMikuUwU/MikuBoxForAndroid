package com.miku.ray.util

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

object AppNameHelper {

    private val NAME_RES: Map<String, Int> = mapOf(
        AppConfig.APP_NAME_DEFAULT to R.string.app_name,
        AppConfig.APP_NAME_MIKU1 to R.string.app_name_variant_miku1,
        AppConfig.APP_NAME_MIKU2 to R.string.app_name_variant_miku2,
        AppConfig.APP_NAME_MIKU3 to R.string.app_name_variant_miku3,
        AppConfig.APP_NAME_MIKU4 to R.string.app_name_variant_miku4,
        AppConfig.APP_NAME_MIKU5 to R.string.app_name_variant_miku5,
        AppConfig.APP_NAME_MIKU6 to R.string.app_name_variant_miku6,
        AppConfig.APP_NAME_MIKU7 to R.string.app_name_variant_miku7,
        AppConfig.APP_NAME_MIKU8 to R.string.app_name_variant_miku8,
        AppConfig.APP_NAME_MIKU9 to R.string.app_name_variant_miku9,
        AppConfig.APP_NAME_MIKU10 to R.string.app_name_variant_miku10,
        AppConfig.APP_NAME_MIKU11 to R.string.app_name_variant_miku11,
        AppConfig.APP_NAME_MIKU12 to R.string.app_name_variant_miku12,
    )

    fun getDisplayName(context: Context): String {
        val variant = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_APP_NAME)
        ?: AppConfig.APP_NAME_DEFAULT
        return getDisplayName(context, variant)
    }

    fun getDisplayName(context: Context, variant: String): String {
        val resId = NAME_RES[variant] ?: R.string.app_name
        return context.getString(resId)
    }
}
