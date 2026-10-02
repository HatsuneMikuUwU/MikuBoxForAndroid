package com.miku.ray.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class BannerSettingsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.uwu_banner_settings
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.setIsRecyclable(false)

        val imageView = holder.findViewById(R.id.iv_banner_settings_character) as? ImageView
        imageView?.let { view ->
            val value = MmkvManager.decodeSettingsString(
                AppConfig.PREF_BANNER_SETTINGS_CHARACTER,
                AppConfig.PREF_BANNER_SETTINGS_CHARACTER_DEFAULT
            )
            if (value == CUSTOM_VALUE) {
                val uri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI)
                Glide.with(view)
                    .load(uri?.takeIf { it.isNotBlank() })
                    .placeholder(drawableFor(AppConfig.PREF_BANNER_SETTINGS_CHARACTER_DEFAULT))
                    .error(drawableFor(AppConfig.PREF_BANNER_SETTINGS_CHARACTER_DEFAULT))
                    .into(view)
            } else {
                Glide.with(view).clear(view)
                view.setImageResource(resolveDrawableRes())
            }
        }
        imageView?.let { applyLayoutParams(it) }
    }

    fun refreshBanner() {
        notifyChanged()
    }

    private fun resolveDrawableRes(): Int {
        val value = MmkvManager.decodeSettingsString(
            AppConfig.PREF_BANNER_SETTINGS_CHARACTER,
            AppConfig.PREF_BANNER_SETTINGS_CHARACTER_DEFAULT
        )
        return drawableFor(value)
    }

    private fun applyLayoutParams(imageView: ImageView) {
        val density = imageView.resources.displayMetrics.density

        val widthDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BANNER_CHARACTER_WIDTH, AppConfig.BANNER_CHARACTER_WIDTH_DEFAULT
        )
        val heightDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BANNER_CHARACTER_HEIGHT, AppConfig.BANNER_CHARACTER_HEIGHT_DEFAULT
        )
        val marginTopDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_TOP, AppConfig.BANNER_CHARACTER_MARGIN_TOP_DEFAULT
        )
        val marginBottomDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_BOTTOM, AppConfig.BANNER_CHARACTER_MARGIN_BOTTOM_DEFAULT
        )
        val marginEndDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_BANNER_CHARACTER_MARGIN_END, AppConfig.BANNER_CHARACTER_MARGIN_END_DEFAULT
        )

        val params = (imageView.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)

        params.width = (widthDp * density).toInt()
        params.height = (heightDp * density).toInt()
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        params.topMargin = (marginTopDp * density).toInt()
        params.bottomMargin = (marginBottomDp * density).toInt()
        params.marginEnd = (marginEndDp * density).toInt()

        imageView.layoutParams = params
    }

    companion object {
        const val CUSTOM_VALUE = "custom"

        fun drawableFor(value: String?): Int = when (value) {
            "uwu_banner_miku_v2" -> R.drawable.uwu_banner_miku_v2
            "uwu_banner_miku_v2_chinese" -> R.drawable.uwu_banner_miku_v2_chinese
            "uwu_banner_miku_v2_append" -> R.drawable.uwu_banner_miku_v2_append
            "uwu_banner_miku_v3" -> R.drawable.uwu_banner_miku_v3
            "uwu_banner_miku_v3_english" -> R.drawable.uwu_banner_miku_v3_english
            "uwu_banner_miku_v4" -> R.drawable.uwu_banner_miku_v4
            "uwu_banner_miku_super_pack" -> R.drawable.uwu_banner_miku_super_pack
            "uwu_banner_miku_v6" -> R.drawable.uwu_banner_miku_v6
            else -> R.drawable.uwu_banner_miku_v6
        }
    }
}
