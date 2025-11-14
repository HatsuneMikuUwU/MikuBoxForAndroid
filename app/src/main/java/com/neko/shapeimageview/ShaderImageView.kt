package com.neko.shapeimageview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import com.neko.shapeimageview.shader.ShaderHelper

abstract class ShaderImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ImageView(context, attrs, defStyle) {

    companion object {
        private const val DEBUG = false
    }

    private var _pathHelper: ShaderHelper? = null

    protected val pathHelper: ShaderHelper
        get() {
            if (_pathHelper == null) {
                _pathHelper = createImageViewHelper()
            }
            return _pathHelper!!
        }

    init {
        pathHelper.init(context, attrs, defStyle)
        
        pathHelper.onImageDrawableReset(drawable)
    }

    protected abstract fun createImageViewHelper(): ShaderHelper

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pathHelper.isSquare) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        _pathHelper?.onImageDrawableReset(drawable)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        _pathHelper?.onImageDrawableReset(getDrawable())
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        _pathHelper?.onImageDrawableReset(drawable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pathHelper.onSizeChanged(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (DEBUG) {
            canvas.drawRGB(10, 200, 200)
        }

        if (!pathHelper.onDraw(canvas)) {
            super.onDraw(canvas)
        }
    }

    fun setBorderColor(borderColor: Int) {
        pathHelper.borderColor = borderColor
        invalidate()
    }

    var borderWidth: Int
        get() = pathHelper.borderWidth
        set(value) {
            pathHelper.borderWidth = value
            invalidate()
        }

    var borderAlpha: Float
        get() = pathHelper.borderAlpha
        set(value) {
            pathHelper.borderAlpha = value
            invalidate()
        }

    var isSquare: Boolean
        get() = pathHelper.isSquare
        set(value) {
            pathHelper.isSquare = value
            invalidate()
        }
}
