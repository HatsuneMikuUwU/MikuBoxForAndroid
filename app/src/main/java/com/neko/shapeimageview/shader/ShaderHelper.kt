package com.neko.shapeimageview.shader

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.DisplayMetrics
import io.nekohasekai.sagernet.R
import kotlin.math.min
import kotlin.math.roundToInt

abstract class ShaderHelper {

    companion object {
        private const val ALPHA_MAX = 255
    }

    protected var viewWidth: Int = 0
    protected var viewHeight: Int = 0

    var borderColor: Int = Color.BLACK
        set(value) {
            field = value
            borderPaint.color = value
        }

    var borderWidth: Int = 0
        set(value) {
            field = value
            borderPaint.strokeWidth = value.toFloat()
        }

    var borderAlpha: Float = 1f
        set(value) {
            field = value
            borderPaint.alpha = (value * ALPHA_MAX).toInt()
        }

    var isSquare: Boolean = false

    protected val borderPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    protected val imagePaint: Paint = Paint().apply {
        isAntiAlias = true
    }

    protected var shader: BitmapShader? = null
    protected var drawable: Drawable? = null
    protected val matrix: Matrix = Matrix()

    abstract fun draw(canvas: Canvas, imagePaint: Paint, borderPaint: Paint)
    abstract fun reset()
    
    abstract fun calculate(
        bitmapWidth: Int,
        bitmapHeight: Int,
        width: Float,
        height: Float,
        scale: Float,
        translateX: Float,
        translateY: Float
    )

    protected fun dpToPx(displayMetrics: DisplayMetrics, dp: Int): Int {
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }

    open fun init(context: Context, attrs: AttributeSet?, defStyle: Int) {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShaderImageView, defStyle, 0)
            borderColor = typedArray.getColor(R.styleable.ShaderImageView_siBorderColor, borderColor)
            borderWidth = typedArray.getDimensionPixelSize(R.styleable.ShaderImageView_siBorderWidth, borderWidth)
            borderAlpha = typedArray.getFloat(R.styleable.ShaderImageView_siBorderAlpha, borderAlpha)
            isSquare = typedArray.getBoolean(R.styleable.ShaderImageView_siSquare, isSquare)
            typedArray.recycle()
        }

        borderPaint.color = borderColor
        borderPaint.alpha = (borderAlpha * ALPHA_MAX).toInt()
        borderPaint.strokeWidth = borderWidth.toFloat()
    }

    fun onDraw(canvas: Canvas): Boolean {
        if (shader == null) {
            createShader()
        }
        if (shader != null && viewWidth > 0 && viewHeight > 0) {
            draw(canvas, imagePaint, borderPaint)
            return true
        }
        return false
    }

    fun onSizeChanged(width: Int, height: Int) {
        if (viewWidth == width && viewHeight == height) return
        
        viewWidth = width
        viewHeight = height
        
        if (isSquare) {
            viewHeight = min(width, height)
            viewWidth = viewHeight
        }
        
        if (shader != null) {
            calculateDrawableSizes()
        }
    }

    fun calculateDrawableSizes(): Bitmap? {
        val bitmap = getBitmap()
        if (bitmap != null) {
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height

            if (bitmapWidth > 0 && bitmapHeight > 0) {
                val width = (viewWidth - 2f * borderWidth).roundToInt().toFloat()
                val height = (viewHeight - 2f * borderWidth).roundToInt().toFloat()

                val scale: Float
                var translateX = 0f
                var translateY = 0f

                if (bitmapWidth * height > width * bitmapHeight) {
                    scale = height / bitmapHeight
                    translateX = ((width / scale - bitmapWidth) / 2f).roundToInt().toFloat()
                } else {
                    scale = width / bitmapWidth.toFloat()
                    translateY = ((height / scale - bitmapHeight) / 2f).roundToInt().toFloat()
                }

                matrix.setScale(scale, scale)
                matrix.preTranslate(translateX, translateY)
                matrix.postTranslate(borderWidth.toFloat(), borderWidth.toFloat())

                calculate(bitmapWidth, bitmapHeight, width, height, scale, translateX, translateY)

                return bitmap
            }
        }

        reset()
        return null
    }

    fun onImageDrawableReset(drawable: Drawable?) {
        this.drawable = drawable
        shader = null
        imagePaint.shader = null
    }

    protected fun createShader() {
        val bitmap = calculateDrawableSizes()
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            imagePaint.shader = shader
        }
    }

    protected fun getBitmap(): Bitmap? {
        return (drawable as? BitmapDrawable)?.bitmap
    }
}
