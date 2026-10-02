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

class BannerHeightSliderDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onClick() {
        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.HOME_BANNER_HEIGHT_MIN,
            AppConfig.HOME_BANNER_HEIGHT_MAX
        )

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_banner_height_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_banner_height)
        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pref_home_banner_height_title)
        .setIcon(RemixR.drawable.rmx_design_ruler_2_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val newHeight = slider.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_HOME_BANNER_HEIGHT, newHeight)
            summary = context.getString(
                R.string.pref_home_banner_height_summary_value, newHeight
            )
            SettingsChangeManager.notifyUiCustomizationChanged()
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val default = AppConfig.HOME_BANNER_HEIGHT_DEFAULT
            slider.value = default.toFloat()

            MmkvManager.encodeSettings(AppConfig.PREF_HOME_BANNER_HEIGHT, default)
            summary = context.getString(R.string.pref_home_banner_height_summary_value, default)
            SettingsChangeManager.notifyUiCustomizationChanged()

            dialog.dismiss()
        }

        updateSummary()
    }

    private fun updateSummary() {
        val h = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HOME_BANNER_HEIGHT,
            AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        )
        summary = context.getString(R.string.pref_home_banner_height_summary_value, h)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
