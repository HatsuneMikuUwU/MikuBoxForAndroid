package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.content.Context
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

class BlurBottomIntensityDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun save(radius: Float, alpha: Int) {
        MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_RADIUS, radius)
        MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_ALPHA, alpha)
        SettingsChangeManager.notifyUiCustomizationChanged()
    }

    override fun onClick() {
        val originalRadius = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BLUR_BOTTOM_RADIUS,
            AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        )
        val originalAlpha = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ALPHA,
            AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA
        )

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_blur_bottom_intensity, null)
        val sliderRadius = dialogView.findViewById<Slider>(R.id.slider_blur_bottom_radius)
        val sliderAlpha = dialogView.findViewById<Slider>(R.id.slider_blur_bottom_alpha)

        sliderRadius.value = originalRadius.coerceIn(0f, 25f)
        sliderAlpha.value = originalAlpha.toFloat().coerceIn(0f, 100f)

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pref_blur_bottom_intensity)
        .setIcon(RemixR.drawable.rmx_blur_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) save(value, sliderAlpha.value.toInt())
        }
        sliderAlpha.addOnChangeListener { _, value, fromUser ->
            if (fromUser) save(sliderRadius.value, value.toInt())
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val radius = sliderRadius.value
            val alpha = sliderAlpha.value.toInt()
            save(radius, alpha)
            updateSummary(radius, alpha)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            save(originalRadius, originalAlpha)
            updateSummary(originalRadius, originalAlpha)
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val defaultRadius = AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
            val defaultAlpha = AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA

            sliderRadius.value = defaultRadius.toFloat()
            sliderAlpha.value = defaultAlpha.toFloat()
            save(defaultRadius, defaultAlpha)
            updateSummary(defaultRadius, defaultAlpha)

            dialog.dismiss()
        }
    }

    fun updateSummary(radius: Float, alpha: Int) {
        summary = context.getString(R.string.summary_blur_bottom_intensity_value, radius, alpha)
    }
}
