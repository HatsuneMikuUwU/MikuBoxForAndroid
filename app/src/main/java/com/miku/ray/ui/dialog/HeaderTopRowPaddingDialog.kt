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

class HeaderTopRowPaddingDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onClick() {
        val saved = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HEADER_TOP_ROW_PADDING,
            AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
        )
        val current = saved.coerceIn(
            AppConfig.HEADER_TOP_ROW_PADDING_MIN,
            AppConfig.HEADER_TOP_ROW_PADDING_MAX
        )

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_header_top_row_padding_slider, null)
        val slider = dialogView.findViewById<Slider>(R.id.slider_header_top_row_padding)
        slider.value = current.toFloat()

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pref_header_top_row_padding_title)
        .setIcon(RemixR.drawable.rmx_design_pencil_ruler_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val newPadding = slider.value.toInt()
            MmkvManager.encodeSettings(AppConfig.PREF_HEADER_TOP_ROW_PADDING, newPadding)
            summary = context.getString(
                R.string.pref_header_top_row_padding_summary_value, newPadding
            )
            SettingsChangeManager.notifyUiCustomizationChanged()
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val default = AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
            slider.value = default.toFloat()

            MmkvManager.encodeSettings(AppConfig.PREF_HEADER_TOP_ROW_PADDING, default)
            summary = context.getString(R.string.pref_header_top_row_padding_summary_value, default)
            SettingsChangeManager.notifyUiCustomizationChanged()

            dialog.dismiss()
        }

        updateSummary()
    }

    private fun updateSummary() {
        val p = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_HEADER_TOP_ROW_PADDING,
            AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
        )
        summary = context.getString(R.string.pref_header_top_row_padding_summary_value, p)
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
