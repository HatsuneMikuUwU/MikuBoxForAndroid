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
import com.miku.ray.util.WindowBlurUtils

class SheetBannerDimSliderDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onClick() {
        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SHEET_BANNER_DIM,
            AppConfig.SHEET_BANNER_DIM_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.SHEET_BANNER_DIM_MIN,
            AppConfig.SHEET_BANNER_DIM_MAX
        )

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_sheet_banner_dim_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_sheet_banner_dim)
        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.sheet_banner_dim_title)
        .setIcon(RemixR.drawable.rmx_design_contrast_2_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val newDim = slider.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_SHEET_BANNER_DIM, newDim)
            summary = context.getString(R.string.sheet_banner_dim_summary_value, newDim)
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val default = AppConfig.SHEET_BANNER_DIM_DEFAULT
            slider.value = default.toFloat()

            MmkvManager.encodeSettings(AppConfig.PREF_SHEET_BANNER_DIM, default)
            summary = context.getString(R.string.sheet_banner_dim_summary_value, default)

            dialog.dismiss()
        }

        updateSummary()
    }

    private fun updateSummary() {
        val d = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SHEET_BANNER_DIM,
            AppConfig.SHEET_BANNER_DIM_DEFAULT
        )
        summary = context.getString(R.string.sheet_banner_dim_summary_value, d)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
