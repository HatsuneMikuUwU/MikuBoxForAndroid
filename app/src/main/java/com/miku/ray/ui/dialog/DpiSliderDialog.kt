package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.util.WindowBlurUtils
import kotlin.math.roundToInt

class DpiSliderDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onClick() {
        context.findActivity() ?: return

        val systemDpi = Resources.getSystem().displayMetrics.densityDpi

        val savedDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val currentDpi = if (savedDpi > 0) savedDpi else systemDpi
        val currentPercent = (currentDpi * 100f / systemDpi / 5f).roundToInt() * 5

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_dpi_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_dpi)

        slider.value = currentPercent.toFloat().coerceIn(75f, 125f)
        slider.setLabelFormatter { value -> "${value.toInt()}%" }

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pref_custom_dpi)
        .setIcon(RemixR.drawable.rmx_smartphone_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val percent = slider.value.toInt()
            val dpi = (percent / 100f * systemDpi).roundToInt()
            val valueToSave = if (percent == 100) 0 else dpi

            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, valueToSave)
            summary = "$percent%"

            SettingsChangeManager.requestRecreate()
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            slider.value = 100f

            MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_DPI, 0)
            summary = "100%"

            dialog.dismiss()
            SettingsChangeManager.requestRecreate()
        }
    }
}
