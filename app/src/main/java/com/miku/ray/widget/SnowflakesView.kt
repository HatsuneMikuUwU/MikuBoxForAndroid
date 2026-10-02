package com.miku.ray.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.miku.ray.util.getColorAttr
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class SnowflakesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Flake(
        var x: Float = 0f,
        var y: Float = 0f,
        var fallSpeed: Float = 0f,
        var drift: Float = 0f,
        var size: Float = 0f,
        var alpha: Float = 0f,
        var phase: Float = 0f,
        var windPhase: Float = 0f,
        var life: Float = 0f,
        var age: Float = 0f
    )

    private val random = Random.Default
    private val flakes = ArrayList<Flake>(MAX_FLAKES_LIMIT)
    private val freeFlakes = ArrayList<Flake>(MAX_FLAKES_LIMIT)
    private var maxFlakes = DEFAULT_MAX_FLAKES
    private var speedMultiplier = 1f
    private var sizeMultiplier = 1f
    private var opacityMultiplier = 0.60f
    private var windMultiplier = 1f
    private var lifeMultiplier = 4f
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var lastFrameTime = 0L
    private var windTimeMs = 0f
    @Volatile
    private var running = false
    @Volatile
    private var animationGeneration = 0L
    private var animationThread: HandlerThread? = null
    private var animationHandler: Handler? = null
    private var animationRunnable: Runnable? = null
    private val flakesLock = Any()
    private var lastColor = Color.WHITE

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        repeat(MAX_FLAKES_LIMIT) { freeFlakes += Flake() }
        refreshColor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshColor()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) {
            if (visibility == VISIBLE) startAnimation() else stopAnimation()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            synchronized(flakesLock) {
                if (flakes.isEmpty()) {
                    repeat(INITIAL_FLAKES.coerceAtMost(maxFlakes)) { spawnFlake(initial = true) }
                }
            }
        }
    }

    fun configure(
        speed: Float,
        count: Int,
        size: Float = 1f,
        opacity: Float = 0.60f,
        wind: Float = 1f,
        life: Float = 4f
    ) {
        speedMultiplier = speed.coerceIn(0.25f, 3f)
        maxFlakes = count.coerceIn(10, MAX_FLAKES_LIMIT)
        sizeMultiplier = size.coerceIn(0.5f, 2f)
        opacityMultiplier = opacity.coerceIn(0.1f, 1f)
        windMultiplier = wind.coerceIn(0f, 3f)
        lifeMultiplier = life.coerceIn(1f, 12f)
        synchronized(flakesLock) {
            while (flakes.size > maxFlakes) {
                recycle(flakes.removeAt(flakes.lastIndex))
            }
            if (width > 0 && height > 0) {
                while (flakes.size < min(INITIAL_FLAKES, maxFlakes)) spawnFlake(initial = true)
            }
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        synchronized(flakesLock) {
            flakes.forEach { drawFlake(canvas, it) }
        }
    }

    private fun startAnimation() {
        if (running) return

        val generation = animationGeneration + 1L
        animationGeneration = generation
        running = true
        lastFrameTime = SystemClock.uptimeMillis()

        val thread = HandlerThread("SnowflakesAnimation")
        thread.start()
        val handler = Handler(thread.looper)
        val runnable = object : Runnable {
            override fun run() {

                if (!running || generation != animationGeneration) return

                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrameTime == 0L) {
                    16L
                } else {
                    (now - lastFrameTime).coerceIn(1L, FRAME_TIME_LIMIT_MS)
                }
                lastFrameTime = now
                synchronized(flakesLock) {
                    updateFlakes(dt.toFloat())
                }
                postInvalidateOnAnimation()

                if (running && generation == animationGeneration) {
                    handler.postDelayed(this, FRAME_INTERVAL_MS)
                }
            }
        }

        animationThread = thread
        animationHandler = handler
        animationRunnable = runnable
        handler.post(runnable)
    }

    private fun stopAnimation() {
        running = false
        animationGeneration += 1L
        animationHandler?.removeCallbacksAndMessages(null)
        animationThread?.quitSafely()
        animationRunnable = null
        animationHandler = null
        animationThread = null
        lastFrameTime = 0L
        windTimeMs = 0f
    }
    private fun refreshColor() {
        lastColor = try {
            context.getColorAttr("colorOnSurface")
        } catch (_: Exception) {
            Color.WHITE
        }
        flakePaint.color = lastColor
    }

    private fun updateFlakes(dtMs: Float) {
        val dt = dtMs / 16f
        val density = resources.displayMetrics.density
        val bottom = height.toFloat() + 12f * density
        windTimeMs += dtMs
        val globalGust = (
            sin(windTimeMs * 0.00075f) * 0.65f +
            sin(windTimeMs * 0.00165f + 1.4f) * 0.35f
        ) * windMultiplier
        val iterator = flakes.iterator()
        while (iterator.hasNext()) {
            val flake = iterator.next()
            flake.age += dtMs
            flake.windPhase += 0.018f * dt
            val localGust = sin(flake.windPhase) * 0.35f * windMultiplier
            val windOffset = (globalGust + localGust) * density * dt * 0.55f
            flake.x += flake.drift * dt * windMultiplier + windOffset
            if (flake.x < -flake.size * 2f) flake.x = width + flake.size * 2f
            if (flake.x > width + flake.size * 2f) flake.x = -flake.size * 2f
            flake.y += flake.fallSpeed * dt * speedMultiplier
            flake.phase += 0.025f * dt
            val fadeOutStart = bottom - FADE_OUT_DISTANCE_DP * density
            val bottomAlpha = if (flake.y > fadeOutStart) {
                ((bottom - flake.y) / (bottom - fadeOutStart)).coerceIn(0f, 1f)
            } else {
                1f
            }
            val lifeFadeDuration = min(FADE_OUT_MS, flake.life * LIFE_FADE_RATIO).coerceAtLeast(MIN_LIFE_FADE_MS)
            val lifeFadeStart = flake.life - lifeFadeDuration
            val lifeAlpha = if (flake.age > lifeFadeStart) {
                ((flake.life - flake.age) / lifeFadeDuration).coerceIn(0f, 1f)
            } else {
                1f
            }
            val entryAlpha = (flake.age / FADE_IN_MS).coerceIn(0f, 1f)
            flake.alpha = min(entryAlpha, min(bottomAlpha, lifeAlpha))
            if (flake.y > bottom || flake.age >= flake.life) {
                iterator.remove()
                recycle(flake)
            }
        }

        while (flakes.size < maxFlakes && random.nextFloat() > 0.72f) {
            spawnFlake(initial = false)
        }
    }

    private fun spawnFlake(initial: Boolean) {
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val flake = if (freeFlakes.isNotEmpty()) freeFlakes.removeAt(freeFlakes.lastIndex) else Flake()
        flake.x = random.nextFloat() * width
        flake.y = if (initial) random.nextFloat() * height else -8f * density
        flake.fallSpeed = (0.7f + random.nextFloat() * 1.1f) * density
        flake.drift = (-0.25f + random.nextFloat() * 0.5f) * density
        flake.size = (1.4f + random.nextFloat() * 3.2f) * density * sizeMultiplier
        flake.phase = random.nextFloat() * 6.28f
        flake.windPhase = random.nextFloat() * 6.28f
        flake.age = if (initial) random.nextFloat() * 1400f else 0f
        flake.life = lifeMultiplier * (900f + random.nextFloat() * 200f)
        flake.alpha = 0f
        flakes += flake
    }

    private fun recycle(flake: Flake) {
        if (freeFlakes.size < MAX_FLAKES_LIMIT) freeFlakes += flake
    }

    private fun drawFlake(canvas: Canvas, flake: Flake) {
        flakePaint.alpha = (flake.alpha * 255f * opacityMultiplier).toInt().coerceIn(0, 255)
        flakePaint.strokeWidth = 0.22f
        val sway = sin(flake.phase) * flake.size * 0.35f
        canvas.save()
        canvas.translate(flake.x + sway, flake.y)
        canvas.scale(flake.size, flake.size)
        canvas.drawPath(UNIT_FLAKE_PATH, flakePaint)
        canvas.restore()
    }

    companion object {
        private const val MAX_FLAKES_LIMIT = 120
        private const val DEFAULT_MAX_FLAKES = 55
        private const val INITIAL_FLAKES = 28
        private const val FADE_IN_MS = 260f
        private const val FADE_OUT_MS = 1800f
        private const val MIN_LIFE_FADE_MS = 250f
        private const val LIFE_FADE_RATIO = 0.2f
        private const val FADE_OUT_DISTANCE_DP = 72f
        private const val FRAME_TIME_LIMIT_MS = 32L
        private const val FRAME_INTERVAL_MS = 17L
        private val ARM_COS = FloatArray(6) { arm -> cos(arm * Math.PI.toFloat() / 3f) }
        private val ARM_SIN = FloatArray(6) { arm -> sin(arm * Math.PI.toFloat() / 3f) }
        private val ARM_BRANCH_COS = FloatArray(6) { arm -> cos((arm + 1) * Math.PI.toFloat() / 3f) }
        private val ARM_BRANCH_SIN = FloatArray(6) { arm -> sin((arm + 1) * Math.PI.toFloat() / 3f) }
        private val ARM_OTHER_COS = FloatArray(6) { arm -> cos((arm - 1) * Math.PI.toFloat() / 3f) }
        private val ARM_OTHER_SIN = FloatArray(6) { arm -> sin((arm - 1) * Math.PI.toFloat() / 3f) }
        private val UNIT_FLAKE_PATH = android.graphics.Path().apply {
            for (arm in 0 until 6) {
                val endX = ARM_COS[arm]
                val endY = ARM_SIN[arm]
                moveTo(0f, 0f)
                lineTo(endX, endY)
                val branchX = endX * 0.58f
                val branchY = endY * 0.58f
                val branchLength = 0.28f
                moveTo(branchX, branchY)
                lineTo(
                    branchX + ARM_BRANCH_COS[arm] * branchLength,
                    branchY + ARM_BRANCH_SIN[arm] * branchLength
                )
                moveTo(branchX, branchY)
                lineTo(
                    branchX + ARM_OTHER_COS[arm] * branchLength,
                    branchY + ARM_OTHER_SIN[arm] * branchLength
                )
            }
        }
    }
}
