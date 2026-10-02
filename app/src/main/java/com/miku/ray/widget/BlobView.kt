package com.miku.ray.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.random.Random

class BlobView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var blobColors: IntArray = intArrayOf(Color.WHITE)
    private var blobCount = 4
    private var xPos = FloatArray(0)
    private var yPos = FloatArray(0)
    private var radiusFactor = FloatArray(0)
    private var dx = FloatArray(0)
    private var dy = FloatArray(0)

    private var animator: ValueAnimator? = null

    fun start(
        colors: IntArray,
        blobCount: Int = 4,
        blobSpeed: Float = 0.6f,
        motionDurationMs: Long = 9000L
    ) {
        stop()

        blobColors = if (colors.isEmpty()) intArrayOf(Color.WHITE) else colors
        this.blobCount = blobCount.coerceAtLeast(1)

        val random = Random(System.nanoTime())
        xPos = FloatArray(this.blobCount) { random.nextFloat() }
        yPos = FloatArray(this.blobCount) { random.nextFloat() }
        radiusFactor = FloatArray(this.blobCount) { 0.35f + random.nextFloat() * 0.25f }
        dx = FloatArray(this.blobCount) { (random.nextFloat() - 0.5f) * blobSpeed }
        dy = FloatArray(this.blobCount) { (random.nextFloat() - 0.5f) * blobSpeed }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = motionDurationMs.coerceAtLeast(1L)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                stepMotion()
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    private fun stepMotion() {
        for (i in 0 until blobCount) {
            xPos[i] += dx[i] * 0.01f
            yPos[i] += dy[i] * 0.01f

            if (xPos[i] < 0f || xPos[i] > 1f) dx[i] *= -1f
            if (yPos[i] < 0f || yPos[i] > 1f) dy[i] *= -1f
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || xPos.isEmpty()) return

        for (i in xPos.indices) {
            val color = blobColors[i % blobColors.size]
            val cx = xPos[i] * w
            val cy = yPos[i] * h
            val r = min(w, h) * radiusFactor[i]
            if (r <= 0f) continue

            paint.shader = RadialGradient(
                cx, cy, r,
                adjustAlpha(color, 0.55f),
                adjustAlpha(color, 0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
