package com.neko.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.PreferenceDataStore
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener

class ProfileBannerImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr), OnPreferenceDataStoreChangeListener {

    private val KEY_URI = "profile_banner_uri"

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(R.raw.uwu_shape_cookie)
    }

    init {
        scaleType = ScaleType.CENTER_CROP
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.registerChangeListener(this)
            loadImage()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.unregisterChangeListener(this)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key == KEY_URI) {
            post { loadImage() }
        }
    }

    private fun loadImage() {
        val savedUriString = DataStore.configurationStore.getString(KEY_URI, null)

        if (savedUriString.isNullOrEmpty()) {
            loadDefault()
            return
        }

        val savedUri = Uri.parse(savedUriString)

        Glide.with(this)
            .asBitmap()
            .load(savedUri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.drawable.uwu_banner_profile)
            .error(R.drawable.uwu_banner_profile)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    setImageBitmap(resource)
                }
            })
    }

    private fun loadDefault() {
        Glide.with(this)
            .asBitmap()
            .load(R.drawable.uwu_banner_profile)
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    setImageBitmap(resource)
                }
            })
    }
}