package com.miku.ray.util

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager

object GoogleSansFlexManager {
    private const val GOOGLE_SANS_FLEX_EMPHASIZED = "variable-title-medium-emphasized"
    private const val GOOGLE_SANS_FLEX_MEDIUM = "variable-title-medium"

    private val supportsNativeGoogleSansFlex: Boolean by lazy {
        supportsFont(GOOGLE_SANS_FLEX_EMPHASIZED) && supportsFont(GOOGLE_SANS_FLEX_MEDIUM)
    }

    private fun supportsFont(name: String): Boolean =
    Typeface.create(name, Typeface.NORMAL) != Typeface.DEFAULT

    private fun shouldUseGoogleSansFlex(): Boolean {
        if (!supportsNativeGoogleSansFlex) return false
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) return false
        return when (MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT).orEmpty()) {
            "", "default", "google" -> true
            else -> false
        }
    }

    fun getBoldTypeface(): Typeface? =
    if (shouldUseGoogleSansFlex()) {
        Typeface.create(GOOGLE_SANS_FLEX_EMPHASIZED, Typeface.NORMAL)
    } else {
        null
    }

    fun applyToBoldText(root: View) {
        val emphasized = getBoldTypeface() ?: return

        fun apply(view: View) {
            if (view is TextView) {
                val style = view.typeface?.style ?: Typeface.NORMAL
                if (style and Typeface.BOLD != 0) {
                    val targetStyle = if (style and Typeface.ITALIC != 0) {
                        Typeface.ITALIC
                    } else {
                        Typeface.NORMAL
                    }
                    view.typeface = Typeface.create(emphasized, targetStyle)
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    apply(view.getChildAt(index))
                }
            }
        }

        apply(root)
    }
}
