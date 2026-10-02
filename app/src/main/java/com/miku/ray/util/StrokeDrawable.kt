package com.miku.ray.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

class StrokeDrawable : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }

    private val topStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val bottomStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val drawBounds = RectF()
    private var drawableAlpha: Int = 255

    var backgroundColor: Int = Color.TRANSPARENT
    set(value) {
        field = value
        fillPaint.color = value
        invalidateSelf()
    }

    var strokeColorTop: Int = Color.TRANSPARENT
    set(value) {
        field = value
        updatePaintColors()
    }

    var strokeColorBottom: Int = Color.TRANSPARENT
    set(value) {
        field = value
        updatePaintColors()
    }

    var strokeWidthTop: Float = 1f
    set(value) {
        field = value.coerceAtLeast(0f)
        topStrokePaint.strokeWidth = field
        invalidateSelf()
    }

    var strokeWidthBottom: Float = 1f
    set(value) {
        field = value.coerceAtLeast(0f)
        bottomStrokePaint.strokeWidth = field
        invalidateSelf()
    }

    var cornerRadius: Float = 0f
    set(value) {
        field = value.coerceAtLeast(0f)
        invalidateSelf()
    }

    var padding: Int = 0
    set(value) {
        field = value.coerceAtLeast(0)
        invalidateSelf()
    }

    var nonRound: Boolean = true
    set(value) {
        field = value
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val maxRadius = min(bounds.width(), bounds.height()) / 2f
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        val drawRadius: Float

        if (nonRound) {
            left = bounds.left.toFloat()
            top = bounds.top.toFloat()
            right = bounds.right.toFloat()
            bottom = bounds.bottom.toFloat()
            drawRadius = min(cornerRadius, maxRadius)
        } else {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            drawRadius = maxRadius - padding
            left = cx - drawRadius
            top = cy - drawRadius
            right = cx + drawRadius
            bottom = cy + drawRadius
        }

        if (Color.alpha(fillPaint.color) > 0) {
            drawBounds.set(left, top, right, bottom)
            canvas.drawRoundRect(drawBounds, drawRadius, drawRadius, fillPaint)
        }

        if (Color.alpha(topStrokePaint.color) > 0 && strokeWidthTop > 0f) {
            drawStroke(canvas, left, top, right, bottom, drawRadius, strokeWidthTop, isTop = true, paint = topStrokePaint)
        }

        if (Color.alpha(bottomStrokePaint.color) > 0 && strokeWidthBottom > 0f) {
            drawStroke(canvas, left, top, right, bottom, drawRadius, strokeWidthBottom, isTop = false, paint = bottomStrokePaint)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
        strokeWidth: Float,
        isTop: Boolean,
        paint: Paint
    ) {
        val strokeHalf = strokeWidth / 2f
        val clipTop = if (isTop) top else max(bottom - radius * 2f, top)
        val clipBottom = if (isTop) min(top + radius * 2f, bottom) else bottom
        val strokeOffsetY = if (isTop) strokeHalf else -strokeHalf

        canvas.save()
        if (canvas.clipRect(left - strokeHalf, clipTop, right + strokeHalf, clipBottom)) {
            canvas.drawRoundRect(
                left - strokeHalf,
                top + strokeOffsetY,
                right + strokeHalf,
                bottom + strokeOffsetY,
                radius,
                radius,
                paint
            )
        }
        canvas.restore()
    }

    private fun updatePaintColors() {
        topStrokePaint.color = withAlpha(strokeColorTop, drawableAlpha)
        bottomStrokePaint.color = withAlpha(strokeColorBottom, drawableAlpha)
        invalidateSelf()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val calculatedAlpha = (Color.alpha(color) * alpha / 255f).toInt().coerceIn(0, 255)
        return Color.argb(
            calculatedAlpha,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        updatePaintColors()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        topStrokePaint.colorFilter = colorFilter
        bottomStrokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
