package com.miku.ray.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.Target
import com.miku.ray.shapeimageview.ShaderImageView
import com.miku.ray.shapeimageview.shader.ShaderHelper
import com.miku.ray.shapeimageview.shader.SvgShader
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileBannerImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private val TAG_PROFILE_DEFAULT = "DEFAULT_BANNER_PROFILE"

    private var currentShapeKey: String = AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT

    private var viewScope: CoroutineScope? = null
    private var shapeChangeJob: Job? = null

    override fun createImageViewHelper(): ShaderHelper {
        currentShapeKey = resolveShapeKey()
        return SvgShader(resolveShapeId(currentShapeKey))
    }

    init {
        scaleType = ScaleType.CENTER_CROP
        setLayerType(View.LAYER_TYPE_NONE, null)
        elevation = 0f
        outlineProvider = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            viewScope = scope
            shapeChangeJob = scope.launch {
                SettingsChangeManager.uiCustomizationChanged.collect {
                    checkAndUpdateShape()
                    loadImage()
                }
            }
            checkAndUpdateShape()
            loadImage()
        }
    }

    override fun onDetachedFromWindow() {
        Glide.with(context.applicationContext).clear(this)
        setImageDrawable(null)
        tag = null
        if (!isInEditMode) {
            shapeChangeJob?.cancel()
            shapeChangeJob = null
            viewScope = null
        }
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !isInEditMode) {
            checkAndUpdateShape()
            loadImage()
        }
    }

    private fun resolveShapeKey(): String =
    MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_SHAPE)
    ?: AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT

    private fun resolveShapeId(key: String): Int = when (key) {
        "uwu_shape_arch"           -> R.raw.uwu_shape_arch
        "uwu_shape_arrow"          -> R.raw.uwu_shape_arrow
        "uwu_shape_boom"           -> R.raw.uwu_shape_boom
        "uwu_shape_bun"            -> R.raw.uwu_shape_bun
        "uwu_shape_burst"          -> R.raw.uwu_shape_burst
        "uwu_shape_circle"         -> R.raw.uwu_shape_circle
        "uwu_shape_clover_4"       -> R.raw.uwu_shape_clover_4
        "uwu_shape_clover_8"       -> R.raw.uwu_shape_clover_8
        "uwu_shape_cookie_4"       -> R.raw.uwu_shape_cookie_4
        "uwu_shape_cookie_6"       -> R.raw.uwu_shape_cookie_6
        "uwu_shape_cookie_7"       -> R.raw.uwu_shape_cookie_7
        "uwu_shape_cookie_9"       -> R.raw.uwu_shape_cookie_9
        "uwu_shape_cookie_12"      -> R.raw.uwu_shape_cookie_12
        "uwu_shape_diamond"        -> R.raw.uwu_shape_diamond
        "uwu_shape_fan"            -> R.raw.uwu_shape_fan
        "uwu_shape_flower"         -> R.raw.uwu_shape_flower
        "uwu_shape_gem"            -> R.raw.uwu_shape_gem
        "uwu_shape_ghostish"       -> R.raw.uwu_shape_ghostish
        "uwu_shape_heart"          -> R.raw.uwu_shape_heart
        "uwu_shape_hexagon"        -> R.raw.uwu_shape_hexagon
        "uwu_shape_oval"           -> R.raw.uwu_shape_oval
        "uwu_shape_pentagon"       -> R.raw.uwu_shape_pentagon
        "uwu_shape_pill"           -> R.raw.uwu_shape_pill
        "uwu_shape_pixel_circle"   -> R.raw.uwu_shape_pixel_circle
        "uwu_shape_pixel_triangle" -> R.raw.uwu_shape_pixel_triangle
        "uwu_shape_puffy"          -> R.raw.uwu_shape_puffy
        "uwu_shape_puffy_diamond"  -> R.raw.uwu_shape_puffy_diamond
        "uwu_shape_semicircle"     -> R.raw.uwu_shape_semicircle
        "uwu_shape_slanted_square" -> R.raw.uwu_shape_slanted_square
        "uwu_shape_soft_boom"      -> R.raw.uwu_shape_soft_boom
        "uwu_shape_soft_burst"     -> R.raw.uwu_shape_soft_burst
        "uwu_shape_square"         -> R.raw.uwu_shape_square
        "uwu_shape_sunny"          -> R.raw.uwu_shape_sunny
        "uwu_shape_triangle"       -> R.raw.uwu_shape_triangle
        "uwu_shape_very_sunny"     -> R.raw.uwu_shape_very_sunny
        else                       -> R.raw.uwu_shape_cookie_9
    }

    private fun checkAndUpdateShape() {
        val newKey = resolveShapeKey()
        if (currentShapeKey != newKey) {
            currentShapeKey = newKey
            reloadShape()
            invalidate()
        }
    }

    private fun loadImage() {
        try {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_PROFILE_BANNER_URI)
            val targetTag = if (uriString.isNullOrEmpty()) TAG_PROFILE_DEFAULT else uriString

            if (this.tag != targetTag) {
                if (!uriString.isNullOrEmpty()) {
                    val savedUri = Uri.parse(uriString)
                    Glide.with(this).clear(this)
                    Glide.with(this)
                    .asBitmap()
                    .load(savedUri)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .dontAnimate()
                    .error(R.drawable.uwu_banner_profile)
                    .into(this)
                } else {
                    loadDefault()
                }
                this.tag = targetTag
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (this.tag != TAG_PROFILE_DEFAULT) {
                loadDefault()
                this.tag = TAG_PROFILE_DEFAULT
            }
        }
    }

    private fun loadDefault() {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.uwu_banner_profile)
    }
}
