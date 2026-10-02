package com.miku.ray.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import com.miku.ray.shapeimageview.ShaderImageView
import com.miku.ray.shapeimageview.shader.ShaderHelper
import com.miku.ray.shapeimageview.shader.SvgShader
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.util.getColorAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr) {

    private enum class ShapeTarget { ICON, ARROW }

    private var shapeTarget: ShapeTarget = ShapeTarget.ICON

    private val prefKey: String
    get() = if (shapeTarget == ShapeTarget.ARROW) AppConfig.PREF_ARROW_SHAPE else AppConfig.PREF_ICON_SHAPE

    private val defaultShapeKey: String
    get() = if (shapeTarget == ShapeTarget.ARROW) AppConfig.PREF_ARROW_SHAPE_DEFAULT else AppConfig.PREF_ICON_SHAPE_DEFAULT

    private var currentShapeKey: String? = null

    private var customBgColor: Int? = null

    private var viewScope: CoroutineScope? = null
    private var shapeChangeJob: Job? = null

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(resolveShapeId())
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.DynamicShapeImageView,
                defStyleAttr,
                0
            )

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_shapeBackgroundColor)) {
                customBgColor = typedArray.getColor(
                    R.styleable.DynamicShapeImageView_shapeBackgroundColor,
                    0
                )
            }

            shapeTarget = if (typedArray.getInt(R.styleable.DynamicShapeImageView_shapeTarget, 0) == 1) {
                ShapeTarget.ARROW
            } else {
                ShapeTarget.ICON
            }

            typedArray.recycle()
        }

        currentShapeKey = defaultShapeKey

        scaleType = ScaleType.CENTER_CROP

        reloadShape()

        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = customBgColor ?: context.getColorAttr("colorPrimary")

            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)

            setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
            applyShape(savedKey)

            val scope = CoroutineScope(Dispatchers.Main.immediate)
            viewScope = scope
            shapeChangeJob = scope.launch {
                SettingsChangeManager.uiCustomizationChanged.collect {
                    val newKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
                    applyShape(newKey)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            shapeChangeJob?.cancel()
            shapeChangeJob = null
            viewScope = null
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        if (hasWindowFocus && !isInEditMode) {
            val savedKey = MmkvManager.decodeSettingsString(prefKey) ?: defaultShapeKey
            applyShape(savedKey)
        }
    }

    private fun applyShape(shapeKey: String) {
        if (currentShapeKey != shapeKey) {
            currentShapeKey = shapeKey
            reloadShape()
            invalidate()
        }
    }

    private fun resolveShapeId(): Int = when (currentShapeKey ?: defaultShapeKey) {
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
        else                       -> if (shapeTarget == ShapeTarget.ICON) R.raw.uwu_shape_cookie_9 else R.raw.uwu_shape_circle
    }
}
