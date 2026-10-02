package com.miku.ray.util

import android.content.Context
import android.content.res.Configuration
import com.miku.ray.AppConfig

object FontSizeController {

    fun wrapWithFontScale(base: Context, fontScale: Float): Context {
        if (fontScale <= 0f || fontScale == AppConfig.FONT_SIZE_DEFAULT) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.fontScale = fontScale
        return base.createConfigurationContext(configuration)
    }

    fun applyFontScale(context: Context, fontScale: Float) {
        if (fontScale <= 0f) return
        val configuration = Configuration(context.resources.configuration)
        configuration.fontScale = fontScale
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
