package io.nekohasekai.sagernet.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.getColorAttr

/**
 * Ported from MikuRay. Renders a solid-color icon badge clipped to the user's
 * chosen SVG shape (defaults to the soft "cookie" blob), used for the icon
 * boxes in the main menu bottom sheet and other card lists.
 */
class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ShaderImageView(context, attrs, defStyleAttr) {

    private var currentShapeKey: String? = DEFAULT_SHAPE

    private var customBgColor: Int? = null

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Action.ICON_SHAPE_CHANGED) {
                applyShape(DataStore.iconShape.ifEmpty { DEFAULT_SHAPE })
            }
        }
    }

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(resolveShapeId())
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.DynamicShapeImageView,
                defStyleAttr,
                0,
            )

            if (typedArray.hasValue(R.styleable.DynamicShapeImageView_shapeBackgroundColor)) {
                customBgColor = typedArray.getColor(
                    R.styleable.DynamicShapeImageView_shapeBackgroundColor,
                    0,
                )
            }

            typedArray.recycle()
        }

        scaleType = ScaleType.CENTER_CROP
        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = customBgColor ?: context.getColorAttr(R.attr.colorPrimary)

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
            applyShape(DataStore.iconShape.ifEmpty { DEFAULT_SHAPE })

            val filter = IntentFilter(Action.ICON_SHAPE_CHANGED)
            ContextCompat.registerReceiver(
                context, shapeChangeReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            try {
                context.unregisterReceiver(shapeChangeReceiver)
            } catch (_: Exception) {
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        if (hasWindowFocus && !isInEditMode) {
            applyShape(DataStore.iconShape.ifEmpty { DEFAULT_SHAPE })
        }
    }

    private fun applyShape(shapeKey: String) {
        if (currentShapeKey != shapeKey) {
            currentShapeKey = shapeKey
            reloadShape()
            invalidate()
        }
    }

    private fun resolveShapeId(): Int = shapeIdFor(currentShapeKey ?: DEFAULT_SHAPE)

    companion object {
        const val DEFAULT_SHAPE = "uwu_shape_cookie"

        fun shapeIdFor(shapeKey: String): Int = when (shapeKey) {
            "uwu_shape_clover" -> R.raw.uwu_shape_clover
            "uwu_shape_circle" -> R.raw.uwu_shape_circle
            "uwu_shape_diamond" -> R.raw.uwu_shape_diamond
            "uwu_shape_pentagon" -> R.raw.uwu_shape_pentagon
            "uwu_shape_hexagon" -> R.raw.uwu_shape_hexagon
            "uwu_shape_octagon" -> R.raw.uwu_shape_octagon
            "uwu_shape_rounded_square" -> R.raw.uwu_shape_rounded_square
            "uwu_shape_squircle" -> R.raw.uwu_shape_squircle
            "uwu_shape_heart" -> R.raw.uwu_shape_heart
            "uwu_shape_hive" -> R.raw.uwu_shape_hive
            "uwu_shape_pill" -> R.raw.uwu_shape_pill
            "uwu_shape_scallop" -> R.raw.uwu_shape_scallop
            else -> R.raw.uwu_shape_cookie
        }
    }
}
