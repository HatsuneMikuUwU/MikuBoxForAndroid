package com.miku.ray.widget.kenburnsview

import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import java.util.Random
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

object MathUtils {
    fun truncate(f: Float, decimalPlaces: Int): Float {
        val decimalShift = 10.0.pow(decimalPlaces.toDouble()).toFloat()
        return round(f * decimalShift) / decimalShift
    }

    fun haveSameAspectRatio(r1: RectF, r2: RectF): Boolean {
        val srcRectRatio = truncate(getRectRatio(r1), 3)
        val dstRectRatio = truncate(getRectRatio(r2), 3)
        return abs(srcRectRatio - dstRectRatio) <= 0.01f
    }

    fun getRectRatio(rect: RectF): Float {
        return rect.width() / rect.height()
    }
}

class IncompatibleRatioException : RuntimeException("Can't perform Ken Burns effect on rects with distinct aspect ratios!")

class Transition(
    val sourceRect: RectF,
    val destinyRect: RectF,
    val duration: Long,
    val interpolator: Interpolator
) {
    private val mCurrentRect = RectF()
    private val mWidthDiff: Float
    private val mHeightDiff: Float
    private val mCenterXDiff: Float
    private val mCenterYDiff: Float

    init {
        if (!MathUtils.haveSameAspectRatio(sourceRect, destinyRect)) {
            throw IncompatibleRatioException()
        }
        mWidthDiff = destinyRect.width() - sourceRect.width()
        mHeightDiff = destinyRect.height() - sourceRect.height()
        mCenterXDiff = destinyRect.centerX() - sourceRect.centerX()
        mCenterYDiff = destinyRect.centerY() - sourceRect.centerY()
    }

    fun getInterpolatedRect(elapsedTime: Long): RectF {
        val elapsedTimeFraction = elapsedTime / duration.toFloat()
        val interpolationProgress = min(elapsedTimeFraction, 1f)
        val interpolation = interpolator.getInterpolation(interpolationProgress)
        val currentWidth = sourceRect.width() + interpolation * mWidthDiff
        val currentHeight = sourceRect.height() + interpolation * mHeightDiff
        val currentCenterX = sourceRect.centerX() + interpolation * mCenterXDiff
        val currentCenterY = sourceRect.centerY() + interpolation * mCenterYDiff
        val left = currentCenterX - currentWidth / 2
        val top = currentCenterY - currentHeight / 2
        val right = left + currentWidth
        val bottom = top + currentHeight
        mCurrentRect.set(left, top, right, bottom)
        return mCurrentRect
    }
}

interface TransitionGenerator {
    fun generateNextTransition(drawableBounds: RectF, viewport: RectF): Transition
}

class RandomTransitionGenerator @JvmOverloads constructor(
    private var mTransitionDuration: Long = DEFAULT_TRANSITION_DURATION.toLong(),
    private var mTransitionInterpolator: Interpolator = AccelerateDecelerateInterpolator()
) : TransitionGenerator {

    private val mRandom = Random(System.currentTimeMillis())
    private var mLastGenTrans: Transition? = null
    private var mLastDrawableBounds: RectF? = null

    override fun generateNextTransition(drawableBounds: RectF, viewport: RectF): Transition {
        val firstTransition = mLastGenTrans == null
        var drawableBoundsChanged = true
        var viewportRatioChanged = true
        var srcRect: RectF? = null
        var dstRect: RectF? = null

        if (!firstTransition) {
            dstRect = mLastGenTrans!!.destinyRect
            drawableBoundsChanged = drawableBounds != mLastDrawableBounds
            viewportRatioChanged = !MathUtils.haveSameAspectRatio(dstRect, viewport)
        }

        if (dstRect == null || drawableBoundsChanged || viewportRatioChanged) {
            srcRect = generateRandomRect(drawableBounds, viewport)
        } else {
            srcRect = dstRect
        }
        dstRect = generateRandomRect(drawableBounds, viewport)

        mLastGenTrans = Transition(srcRect, dstRect, mTransitionDuration, mTransitionInterpolator)
        mLastDrawableBounds = RectF(drawableBounds)

        return mLastGenTrans!!
    }

    private fun generateRandomRect(drawableBounds: RectF, viewportRect: RectF): RectF {
        val drawableRatio = MathUtils.getRectRatio(drawableBounds)
        val viewportRectRatio = MathUtils.getRectRatio(viewportRect)
        val maxCrop: RectF

        if (drawableRatio > viewportRectRatio) {
            val r = drawableBounds.height() / viewportRect.height() * viewportRect.width()
            val b = drawableBounds.height()
            maxCrop = RectF(0f, 0f, r, b)
        } else {
            val r = drawableBounds.width()
            val b = drawableBounds.width() / viewportRect.width() * viewportRect.height()
            maxCrop = RectF(0f, 0f, r, b)
        }

        val randomFloat = MathUtils.truncate(mRandom.nextFloat(), 2)
        val factor = MIN_RECT_FACTOR + (1 - MIN_RECT_FACTOR) * randomFloat

        val width = factor * maxCrop.width()
        val height = factor * maxCrop.height()
        val widthDiff = (drawableBounds.width() - width).toInt()
        val heightDiff = (drawableBounds.height() - height).toInt()

        val left = if (widthDiff > 0) mRandom.nextInt(widthDiff).toFloat() else 0f
        val top = if (heightDiff > 0) mRandom.nextInt(heightDiff).toFloat() else 0f

        return RectF(left, top, left + width, top + height)
    }

    fun setTransitionDuration(transitionDuration: Long) {
        mTransitionDuration = transitionDuration
    }

    fun setTransitionInterpolator(interpolator: Interpolator) {
        mTransitionInterpolator = interpolator
    }

    companion object {
        const val DEFAULT_TRANSITION_DURATION = 10000
        private const val MIN_RECT_FACTOR = 0.75f
    }
}
