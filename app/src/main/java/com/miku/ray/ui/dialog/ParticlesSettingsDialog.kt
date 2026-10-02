package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.miku.ray.util.WindowBlurUtils
import java.util.Locale

class ParticlesSettingsDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private data class SliderParam(
        val prefKey: String,
        val labelRes: Int,
        val min: Float,
        val max: Float,
        val step: Float,
        val default: Float,
        val decimalPlaces: Int,
        val unitSuffix: String,
        val labelViewId: Int,
        val sliderViewId: Int
    )

    private val params = listOf(
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_FRAME_DELAY,
            labelRes = R.string.particles_slider_frame_delay_label,
            min = AppConfig.PARTICLES_FRAME_DELAY_MIN,
            max = AppConfig.PARTICLES_FRAME_DELAY_MAX,
            step = 1f,
            default = AppConfig.PARTICLES_FRAME_DELAY_DEFAULT,
            decimalPlaces = 0,
            unitSuffix = " ms",
            labelViewId = R.id.text_label_frame_delay,
            sliderViewId = R.id.slider_frame_delay
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_LINE_LENGTH,
            labelRes = R.string.particles_slider_line_length_label,
            min = AppConfig.PARTICLES_LINE_LENGTH_MIN,
            max = AppConfig.PARTICLES_LINE_LENGTH_MAX,
            step = 1f,
            default = AppConfig.PARTICLES_LINE_LENGTH_DEFAULT,
            decimalPlaces = 0,
            unitSuffix = " dp",
            labelViewId = R.id.text_label_line_length,
            sliderViewId = R.id.slider_line_length
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_LINE_THICKNESS,
            labelRes = R.string.particles_slider_line_thickness_label,
            min = AppConfig.PARTICLES_LINE_THICKNESS_MIN,
            max = AppConfig.PARTICLES_LINE_THICKNESS_MAX,
            step = 0.5f,
            default = AppConfig.PARTICLES_LINE_THICKNESS_DEFAULT,
            decimalPlaces = 1,
            unitSuffix = " dp",
            labelViewId = R.id.text_label_line_thickness,
            sliderViewId = R.id.slider_line_thickness
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_RADIUS_MAX,
            labelRes = R.string.particles_slider_radius_max_label,
            min = AppConfig.PARTICLES_RADIUS_MAX_MIN,
            max = AppConfig.PARTICLES_RADIUS_MAX_MAX,
            step = 0.5f,
            default = AppConfig.PARTICLES_RADIUS_MAX_DEFAULT,
            decimalPlaces = 1,
            unitSuffix = " dp",
            labelViewId = R.id.text_label_radius_max,
            sliderViewId = R.id.slider_radius_max
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_RADIUS_MIN,
            labelRes = R.string.particles_slider_radius_min_label,
            min = AppConfig.PARTICLES_RADIUS_MIN_MIN,
            max = AppConfig.PARTICLES_RADIUS_MIN_MAX,
            step = 0.5f,
            default = AppConfig.PARTICLES_RADIUS_MIN_DEFAULT,
            decimalPlaces = 1,
            unitSuffix = " dp",
            labelViewId = R.id.text_label_radius_min,
            sliderViewId = R.id.slider_radius_min
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_DENSITY,
            labelRes = R.string.particles_slider_density_label,
            min = AppConfig.PARTICLES_DENSITY_MIN,
            max = AppConfig.PARTICLES_DENSITY_MAX,
            step = 5f,
            default = AppConfig.PARTICLES_DENSITY_DEFAULT,
            decimalPlaces = 0,
            unitSuffix = "",
            labelViewId = R.id.text_label_density,
            sliderViewId = R.id.slider_density
        ),
        SliderParam(
            prefKey = AppConfig.PREF_PARTICLES_SPEED_FACTOR,
            labelRes = R.string.particles_slider_speed_factor_label,
            min = AppConfig.PARTICLES_SPEED_FACTOR_MIN,
            max = AppConfig.PARTICLES_SPEED_FACTOR_MAX,
            step = 0.1f,
            default = AppConfig.PARTICLES_SPEED_FACTOR_DEFAULT,
            decimalPlaces = 1,
            unitSuffix = "x",
            labelViewId = R.id.text_label_speed_factor,
            sliderViewId = R.id.slider_speed_factor
        )
    )

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun format(param: SliderParam, value: Float): String {
        return if (param.decimalPlaces > 0) {
            String.format(Locale.getDefault(), "%.${param.decimalPlaces}f%s", value, param.unitSuffix)
        } else {
            "${value.toInt()}${param.unitSuffix}"
        }
    }

    private fun labelText(param: SliderParam, value: Float): String {
        return "${context.getString(param.labelRes)}: ${format(param, value)}"
    }

    private fun currentValue(param: SliderParam): Float {
        return MmkvManager.decodeSettingsFloat(param.prefKey, param.default)
        .coerceIn(param.min, param.max)
    }

    override fun onClick() {
        val activity = context.findActivity() ?: return

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_particles_settings, null)

        val sliders = params.map { param ->
            val label = dialogView.findViewById<TextView>(param.labelViewId)
            val slider = dialogView.findViewById<Slider>(param.sliderViewId)
            val current = currentValue(param)

            slider.valueFrom = param.min
            slider.valueTo = param.max
            slider.stepSize = param.step
            slider.value = current
            label.text = labelText(param, current)

            slider.addOnChangeListener { _, value, _ ->
                label.text = labelText(param, value)
            }
            param to slider
        }

        val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setIcon(RemixR.drawable.rmx_sparkling_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            sliders.forEach { (param, slider) ->
                MmkvManager.encodeSettings(param.prefKey, slider.value)
            }
        }
        .setNeutralButton(R.string.reset, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            sliders.forEach { (param, slider) ->
                slider.value = param.default
                dialogView.findViewById<TextView>(param.labelViewId).text =
                labelText(param, param.default)
                MmkvManager.encodeSettings(param.prefKey, param.default)
            }

            dialog.dismiss()
        }
    }
}
