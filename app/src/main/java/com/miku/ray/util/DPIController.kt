package com.miku.ray.util

import android.content.Context
import android.content.res.Configuration

object DPIController {

    fun wrapWithDpi(base: Context, dpiValue: Int): Context {
        if (dpiValue <= 0) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.densityDpi = dpiValue
        return base.createConfigurationContext(configuration)
    }

    fun applyDpi(context: Context, dpiValue: Int) {
        if (dpiValue <= 0) return
        val configuration = Configuration(context.resources.configuration)
        configuration.densityDpi = dpiValue
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
