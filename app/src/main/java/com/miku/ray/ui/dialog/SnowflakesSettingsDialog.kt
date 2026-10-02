package com.miku.ray.ui.dialog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.util.WindowBlurUtils
import java.util.Locale

class SnowflakesSettingsDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)
    private fun formatPercent(value: Float): String = "${(value * 100f).toInt()}%"

    private fun values(): FloatArray = floatArrayOf(
        MmkvManager.decodeSettingsFloat(AppConfig.PREF_SNOWFLAKES_SPEED, AppConfig.SNOWFLAKES_SPEED_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_SPEED_MIN, AppConfig.SNOWFLAKES_SPEED_MAX),
        MmkvManager.decodeSettingsInt(AppConfig.PREF_SNOWFLAKES_COUNT, AppConfig.SNOWFLAKES_COUNT_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_COUNT_MIN, AppConfig.SNOWFLAKES_COUNT_MAX).toFloat(),
        MmkvManager.decodeSettingsFloat(AppConfig.PREF_SNOWFLAKES_SIZE, AppConfig.SNOWFLAKES_SIZE_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_SIZE_MIN, AppConfig.SNOWFLAKES_SIZE_MAX),
        MmkvManager.decodeSettingsFloat(AppConfig.PREF_SNOWFLAKES_OPACITY, AppConfig.SNOWFLAKES_OPACITY_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_OPACITY_MIN, AppConfig.SNOWFLAKES_OPACITY_MAX),
        MmkvManager.decodeSettingsFloat(AppConfig.PREF_SNOWFLAKES_WIND, AppConfig.SNOWFLAKES_WIND_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_WIND_MIN, AppConfig.SNOWFLAKES_WIND_MAX),
        MmkvManager.decodeSettingsFloat(AppConfig.PREF_SNOWFLAKES_LIFE, AppConfig.SNOWFLAKES_LIFE_DEFAULT)
        .coerceIn(AppConfig.SNOWFLAKES_LIFE_MIN, AppConfig.SNOWFLAKES_LIFE_MAX)
    )

    private fun updateSummary() {
        val v = values()
        summary = context.getString(
            R.string.snowflakes_settings_summary_value,
            format(v[0]), v[1].toInt(), format(v[2]), formatPercent(v[3]), format(v[4]), format(v[5])
        )
    }

    override fun onClick() {
        val current = values()
        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_snowflakes_settings, null)
        val speedSlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_speed)
        val countSlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_count)
        val sizeSlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_size)
        val opacitySlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_opacity)
        val windSlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_wind)
        val lifeSlider = dialogView.findViewById<Slider>(R.id.slider_snowflakes_life)
        val speedValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_speed_value)
        val countValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_count_value)
        val sizeValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_size_value)
        val opacityValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_opacity_value)
        val windValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_wind_value)
        val lifeValue = dialogView.findViewById<TextView>(R.id.text_snowflakes_life_value)

        speedSlider.value = current[0]
        countSlider.value = current[1]
        sizeSlider.value = current[2]
        opacitySlider.value = current[3]
        windSlider.value = current[4]
        lifeSlider.value = current[5]

        fun refreshValues() {
            speedValue.text = context.getString(R.string.snowflakes_speed_value, format(speedSlider.value))
            countValue.text = context.getString(R.string.snowflakes_count_value, countSlider.value.toInt())
            sizeValue.text = context.getString(R.string.snowflakes_size_value, format(sizeSlider.value))
            opacityValue.text = context.getString(R.string.snowflakes_opacity_value, formatPercent(opacitySlider.value))
            windValue.text = context.getString(R.string.snowflakes_wind_value, format(windSlider.value))
            lifeValue.text = context.getString(R.string.snowflakes_life_value, format(lifeSlider.value))
        }
        refreshValues()
        listOf(speedSlider, countSlider, sizeSlider, opacitySlider, windSlider, lifeSlider).forEach { slider ->
            slider.addOnChangeListener { _, _, _ -> refreshValues() }
        }

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.snowflakes_settings_title)
        .setIcon(RemixR.drawable.rmx_sparkling_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_SPEED, speedSlider.value)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_COUNT, countSlider.value.toInt())
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_SIZE, sizeSlider.value)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_OPACITY, opacitySlider.value)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_WIND, windSlider.value)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_LIFE, lifeSlider.value)
            updateSummary()
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_SPEED, AppConfig.SNOWFLAKES_SPEED_DEFAULT)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_COUNT, AppConfig.SNOWFLAKES_COUNT_DEFAULT)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_SIZE, AppConfig.SNOWFLAKES_SIZE_DEFAULT)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_OPACITY, AppConfig.SNOWFLAKES_OPACITY_DEFAULT)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_WIND, AppConfig.SNOWFLAKES_WIND_DEFAULT)
            MmkvManager.encodeSettings(AppConfig.PREF_SNOWFLAKES_LIFE, AppConfig.SNOWFLAKES_LIFE_DEFAULT)
            updateSummary()
            dialog.dismiss()
        }
    }

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }
}
