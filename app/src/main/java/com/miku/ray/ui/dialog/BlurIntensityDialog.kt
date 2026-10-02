package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.WindowBlurUtils

class BlurIntensityDialog @JvmOverloads constructor(
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
        val originalRadius = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_RADIUS, AppConfig.DEFAULT_BLUR_RADIUS)
        val originalRounds = MmkvManager.decodeSettingsInt(AppConfig.PREF_BLUR_ROUNDS, AppConfig.DEFAULT_BLUR_ROUNDS)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_radius)
        val sliderRounds = dialogView.findViewById<Slider>(R.id.slider_blur_rounds)
        val roundsLabel = dialogView.findViewById<View>(R.id.label_blur_rounds)
        val roundsWarning = dialogView.findViewById<View>(R.id.blur_rounds_warning)
        val isUsingSystemBlur = WindowBlurUtils.isSystemBlurAvailable(context)
        if (isUsingSystemBlur) {
            roundsLabel.visibility = View.GONE
            sliderRounds.visibility = View.GONE
            roundsWarning.visibility = View.GONE
        }

        sliderRadius.value = originalRadius.toFloat().coerceIn(2f, 100f)
        sliderRounds.value = originalRounds.toFloat().coerceIn(1f, 15f)

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pref_blur_intensity)
        .setIcon(RemixR.drawable.rmx_blur_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                WindowBlurUtils.updateWindowBlur(dialog.window, value, sliderRounds.value.toInt())
            }
        }
        if (!isUsingSystemBlur) {
            sliderRounds.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    WindowBlurUtils.updateWindowBlur(dialog.window, sliderRadius.value, value.toInt())
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value.toInt()
            val rounds = sliderRounds.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, radius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, rounds)
            updateSummary(radius, rounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            WindowBlurUtils.updateWindowBlur(dialog.window, originalRadius.toFloat(), originalRounds)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, originalRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, originalRounds)
            updateSummary(originalRadius, originalRounds)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val defaultRadius = AppConfig.DEFAULT_BLUR_RADIUS
            val defaultRounds = AppConfig.DEFAULT_BLUR_ROUNDS

            sliderRadius.value = defaultRadius.toFloat()
            sliderRounds.value = defaultRounds.toFloat()
            WindowBlurUtils.updateWindowBlur(dialog.window, defaultRadius.toFloat(), defaultRounds)

            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_RADIUS, defaultRadius)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_ROUNDS, defaultRounds)
            updateSummary(defaultRadius, defaultRounds)

            dialog.dismiss()
        }
    }

    fun updateSummary(radius: Int, rounds: Int) {
        summary = if (WindowBlurUtils.isSystemBlurAvailable(context)) {
            context.getString(R.string.summary_blur_intensity_system_value, radius)
        } else {
            context.getString(R.string.summary_blur_intensity_value, radius, rounds)
        }
    }
}
