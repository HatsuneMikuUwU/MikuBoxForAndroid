package com.miku.ray.ui.preference

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class CustomBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var onImageClick: (() -> Unit)? = null
    var onImageLongClick: (() -> Unit)? = null
    private val defaultBannerTag = "DEFAULT_THEME_BANNER"

    init {
        layoutResource = R.layout.uwu_banner_theme
    }

    fun refresh() {
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        (holder.findViewById(R.id.uwu_name_title_summary) as? TextView)?.text = com.miku.ray.util.AppNameHelper.getDisplayName(context)
        (holder.findViewById(R.id.uwu_version_name_summary) as? TextView)?.text = context.getString(R.string.uwu_version_name)
        (holder.findViewById(R.id.uwu_version_code_summary) as? TextView)?.text = context.getString(R.string.uwu_version_code)
        (holder.findViewById(R.id.uwu_package_name_summary) as? TextView)?.text = context.getString(R.string.uwu_package_name)
        (holder.findViewById(R.id.uwu_build_date_summary) as? TextView)?.text = context.getString(R.string.uwu_build_date)

        val imageView = holder.findViewById(R.id.img_banner_preference) as? ImageView
        if (imageView != null) {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
            val targetTag = uriString?.takeUnless { it.isBlank() } ?: defaultBannerTag
            if (imageView.tag != targetTag) {
                Glide.with(context.applicationContext).clear(imageView)
                if (targetTag == defaultBannerTag) {
                    imageView.setImageResource(R.drawable.uwu_banner_theme)
                } else {
                    Glide.with(imageView)
                    .load(Uri.parse(targetTag))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_theme)
                    .into(imageView)
                }
                imageView.tag = targetTag
            }
        }

        val imageClickTarget = holder.findViewById(R.id.theme_banner_image_card)
        imageClickTarget?.setOnClickListener {
            onImageClick?.invoke()
        }
        imageClickTarget?.setOnLongClickListener {
            onImageLongClick?.invoke()
            true
        }

        val clickTarget = holder.findViewById(R.id.onClick)
        clickTarget?.setOnClickListener {
            onPreferenceClickListener?.onPreferenceClick(this)
        }
    }
}
