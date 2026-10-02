package com.miku.ray.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.miku.ray.marquee.text.AutoMarqueeTextView

/**
 * Vertical conveyor roll for value changes: the outgoing line travels upward
 * and dissolves while the incoming line rides up from below into place. Both
 * faces share one easing, so the swap reads as a single continuous roll.
 * A retarget mid-roll snapshots the frame as it is on screen, so rapid
 * successive changes chain smoothly instead of jumping.
 */
class RollingIpTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AutoMarqueeTextView(context, attrs) {
    private var previous: Bitmap? = null
    private var animator: ValueAnimator? = null
    private var progress = 1f
    private var target: String? = null
    private val fadePaint = Paint()

    fun showValue(value: String, animate: Boolean) {
        if (value == target) return
        val hadText = !text.isNullOrBlank()
        val snapshot = if (animate && hadText && isAttachedToWindow && isLaidOut && width > 0 && height > 0) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        } else {
            null
        }
        releaseAnimation()
        target = value
        text = value
        if (snapshot == null) return
        previous = snapshot
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { releaseAnimation(); invalidate() }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val old = previous
        if (old == null || progress >= 1f) { super.onDraw(canvas); return }
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)

        // Outgoing line rolls up and dissolves quickly so the swap reads as one
        // motion instead of two texts competing for the same line.
        fadePaint.alpha = ((1f - progress * 1.6f).coerceIn(0f, 1f) * 255).toInt()
        canvas.save()
        canvas.translate(0f, -height * progress)
        canvas.drawBitmap(old, 0f, 0f, fadePaint)
        canvas.restore()

        // Incoming line rides up from below, fading in through the middle of
        // the roll and settling as the outgoing line clears the top edge.
        val alpha = ((progress - 0.15f) / 0.7f).coerceIn(0f, 1f)
        canvas.save()
        canvas.translate(0f, height * (1f - progress))
        if (alpha < 1f) {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt())
            super.onDraw(canvas)
            canvas.restore()
        } else {
            super.onDraw(canvas)
        }
        canvas.restore()

        canvas.restoreToCount(checkpoint)
    }

    private fun releaseAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        previous?.recycle()
        previous = null
        progress = 1f
    }

    override fun onDetachedFromWindow() { releaseAnimation(); super.onDetachedFromWindow() }
}
