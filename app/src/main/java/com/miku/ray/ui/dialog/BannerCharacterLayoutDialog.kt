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
import com.miku.ray.ui.preference.BannerSettingsPreference
import com.miku.ray.util.WindowBlurUtils
import java.util.Locale

class BannerCharacterLayoutDialog @JvmOverloads constructor(
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
        val labelViewId: Int,
        val sliderViewId: Int
    )

    private val params = listOf(
        SliderParam(
            prefKey = AppConfig.PREF_BANNER_CHARACTER_WIDTH,
            labelRes = R.string.banner_character_slider_width_label,
            min = AppConfig.BANNER_CHARACTER_WIDTH_MIN,
            max = AppConfig.BANNER_CHARACTER_WIDTH_MAX,
            step = 1f,
            default = AppConfig.BANNER_CHARACTER_WIDTH_DEFAULT,
            labelViewId = R.id.text_label_banner_width,
            sliderViewId = R.id.slider_banner_width
        ),
        SliderParam(
            prefKey = AppConfig.PREF_BANNER_CHARACTER_HEIGHT,
            labelRes = R.string.banner_character_slider_height_label,
            min = AppConfig.BANNER_CHARACTER_HEIGHT_MIN,
            max = AppConfig.BANNER_CHARACTER_HEIGHT_MAX,
            step = 1f,
            default = AppConfig.BANNER_CHARACTER_HEIGHT_DEFAULT,
            labelViewId = R.id.text_label_banner_height,
            sliderViewId = R.id.slider_banner_height
        ),
        SliderParam(
            prefKey = AppConfig.PREF_BANNER_CHARACTER_MARGIN_TOP,
            labelRes = R.string.banner_character_slider_margin_top_label,
            min = AppConfig.BANNER_CHARACTER_MARGIN_TOP_MIN,
            max = AppConfig.BANNER_CHARACTER_MARGIN_TOP_MAX,
            step = 1f,
            default = AppConfig.BANNER_CHARACTER_MARGIN_TOP_DEFAULT,
            labelViewId = R.id.text_label_banner_margin_top,
            sliderViewId = R.id.slider_banner_margin_top
        ),
        SliderParam(
            prefKey = AppConfig.PREF_BANNER_CHARACTER_MARGIN_BOTTOM,
            labelRes = R.string.banner_character_slider_margin_bottom_label,
            min = AppConfig.BANNER_CHARACTER_MARGIN_BOTTOM_MIN,
            max = AppConfig.BANNER_CHARACTER_MARGIN_BOTTOM_MAX,
            step = 1f,
            default = AppConfig.BANNER_CHARACTER_MARGIN_BOTTOM_DEFAULT,
            labelViewId = R.id.text_label_banner_margin_bottom,
            sliderViewId = R.id.slider_banner_margin_bottom
        ),
        SliderParam(
            prefKey = AppConfig.PREF_BANNER_CHARACTER_MARGIN_END,
            labelRes = R.string.banner_character_slider_margin_end_label,
            min = AppConfig.BANNER_CHARACTER_MARGIN_END_MIN,
            max = AppConfig.BANNER_CHARACTER_MARGIN_END_MAX,
            step = 1f,
            default = AppConfig.BANNER_CHARACTER_MARGIN_END_DEFAULT,
            labelViewId = R.id.text_label_banner_margin_end,
            sliderViewId = R.id.slider_banner_margin_end
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

    private fun format(value: Float): String {
        return String.format(Locale.getDefault(), "%.0fdp", value)
    }

    private fun labelText(param: SliderParam, value: Float): String {
        return "${context.getString(param.labelRes)}: ${format(value)}"
    }

    private fun currentValue(param: SliderParam): Float {
        return MmkvManager.decodeSettingsFloat(param.prefKey, param.default)
        .coerceIn(param.min, param.max)
    }

    private fun refreshBannerPreview() {
        preferenceManager
        ?.findPreference<BannerSettingsPreference>("pref_banner_settings_card")
        ?.refreshBanner()
    }

    override fun onClick() {
        context.findActivity() ?: return

        val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_banner_character_layout_slider, null)

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
        .setIcon(RemixR.drawable.rmx_arrows_expand_diagonal_line)
        .setView(dialogView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            sliders.forEach { (param, slider) ->
                MmkvManager.encodeSettings(param.prefKey, slider.value)
            }
            refreshBannerPreview()
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
            refreshBannerPreview()

            dialog.dismiss()
        }
    }
}
