package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.SelectedProfileBannerController
import com.miku.ray.util.WindowBlurUtils

class SelectedBannerDimSliderDialog @JvmOverloads constructor(
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
        val activity = context.findActivity() ?: return

        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.SELECTED_BANNER_DIM_MIN,
            AppConfig.SELECTED_BANNER_DIM_MAX
        )

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_selected_banner_dim_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_selected_banner_dim)
        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.selected_banner_dim_title)
        .setIcon(RemixR.drawable.rmx_design_contrast_2_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val newDim = slider.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_DIM, newDim)
            summary = context.getString(R.string.selected_banner_dim_summary_value, newDim)
            SelectedProfileBannerController.notifyChanged(activity)
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val default = AppConfig.SELECTED_BANNER_DIM_DEFAULT
            slider.value = default.toFloat()

            MmkvManager.encodeSettings(AppConfig.PREF_SELECTED_BANNER_DIM, default)
            summary = context.getString(R.string.selected_banner_dim_summary_value, default)
            SelectedProfileBannerController.notifyChanged(activity)

            dialog.dismiss()
        }

        updateSummary()
    }

    private fun updateSummary() {
        val d = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SELECTED_BANNER_DIM,
            AppConfig.SELECTED_BANNER_DIM_DEFAULT
        )
        summary = context.getString(R.string.selected_banner_dim_summary_value, d)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
