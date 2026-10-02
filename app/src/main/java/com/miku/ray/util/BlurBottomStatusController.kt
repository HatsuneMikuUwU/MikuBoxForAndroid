package com.miku.ray.util

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.miku.ray.AppConfig
import com.miku.ray.blurview.BlurView
import com.miku.ray.widget.BlobView
import com.miku.ray.databinding.ActivityMainBinding
import com.miku.ray.handler.MmkvManager
import java.lang.ref.WeakReference
import kotlin.math.abs

object BlurBottomStatusController {

    private var blurViewReference: WeakReference<BlurView>? = null
    private var glassDrawableReference: WeakReference<GradientDrawable>? = null
    private var blobViewReference: WeakReference<BlobView>? = null
    private var glassFillBaseColor: Int = 0
    private var glassFillColor: Int = Color.TRANSPARENT

    private const val MIN_BLUR_RADIUS = 0f
    private const val MAX_BLUR_RADIUS = 25f

    private fun toBlurViewRadius(userRadius: Float): Float =
        userRadius.coerceIn(MIN_BLUR_RADIUS, MAX_BLUR_RADIUS)

    fun isEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_STATUS, false)

    private fun isBlobAnimEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_BLUR_BOTTOM_BLOB_ANIM, false)

    fun applyState(activity: AppCompatActivity, binding: ActivityMainBinding, onTestClick: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        val radiusPx = 28f * density

        binding.blurBottomStatus.apply {
            visibility = View.VISIBLE
            
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            clipToOutline = true
        }

        if (isEnabled()) {
            applyBlurOn(activity, binding, radiusPx, density, onTestClick)
        } else {
            applyBlurOff(activity, binding, radiusPx, onTestClick)
        }
    }

    private fun alphaPercentToInt(percent: Float): Int =
        (percent.coerceIn(0f, 100f) / 100f * 255f).toInt().coerceIn(0, 255)

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha, Color.red(color), Color.green(color), Color.blue(color)
    )

    @SuppressLint("ClickableViewAccessibility")
    private fun applyBounceTouchAnimation(
        view: View,
        glowDrawable: GradientDrawable? = null,
        onClick: () -> Unit
    ) {
        view.isClickable = true
        view.setOnClickListener { onClick() }

        var touchStartX = 0f
        var touchStartY = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    v.isPressed = true
                    glowDrawable?.alpha = 255
                    v.animate().cancel()
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().cancel()
                    val deltaX = event.rawX - touchStartX
                    val deltaY = event.rawY - touchStartY
                    val stretchX = 1.06f + (abs(deltaX) / v.width.toFloat() * 0.15f).coerceAtMost(0.09f)
                    val stretchY = 1.06f + (abs(deltaY) / v.height.toFloat() * 0.15f).coerceAtMost(0.09f)
                    v.scaleX = stretchX
                    v.scaleY = stretchY
                    v.translationX = deltaX * 0.18f
                    v.translationY = deltaY * 0.18f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    glowDrawable?.alpha = 0
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                        .setDuration(380)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            true
        }
    }

    private fun applyBlurOn(
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
        radiusPx: Float,
        density: Float,
        onTestClick: () -> Unit
    ) {
        val blurRadius = toBlurViewRadius(
            MmkvManager.decodeSettingsFloat(
                AppConfig.PREF_BLUR_BOTTOM_RADIUS, AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
            )
        )
        val alphaPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_BLUR_BOTTOM_ALPHA, AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA
        ).toFloat().coerceIn(0f, 100f)

        val isDark = ThemeManager.isDarkMode(activity)
        glassFillBaseColor = activity.getColorAttr("colorSurfaceContainer")
        glassFillColor = withAlpha(glassFillBaseColor, alphaPercentToInt(alphaPercent))

        val glassDrawable = GradientDrawable().apply {
            setColor(glassFillColor)
            cornerRadius = radiusPx
        }

        val strokeDrawable = StrokeDrawable().apply {
            cornerRadius = radiusPx
            strokeWidthTop = 1f * density
            strokeWidthBottom = 1f * density
            strokeColorTop = if (isDark) Color.argb(0x28, 255, 255, 255) else Color.WHITE
            strokeColorBottom = if (isDark) Color.argb(0x14, 255, 255, 255) else Color.WHITE
        }

        val glowColor = if (isDark) Color.argb(0x30, 255, 255, 255) else Color.argb(0x65, 255, 255, 255)
        val glowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 220f * density
            colors = intArrayOf(glowColor, Color.TRANSPARENT)
            cornerRadius = radiusPx
            alpha = 0
        }

        val combinedForeground = LayerDrawable(arrayOf(strokeDrawable, glowDrawable))

        binding.blurBottomStatus.apply {
            setupWith(binding.mainContent)
                .setFrameClearDrawable(activity.window.decorView.background)
                .setBlurAutoUpdate(true)
            background = glassDrawable
            clipToOutline = true
            foreground = combinedForeground
            if (blurRadius > MIN_BLUR_RADIUS) {
                setBlurRadius(blurRadius)
            }
            setBlurEnabled(blurRadius > MIN_BLUR_RADIUS)
            setOverlayColor(glassFillColor)
            applyBounceTouchAnimation(this, glowDrawable, onTestClick)
        }

        binding.routingMode.apply {
            setupWith(binding.mainContent)
                .setFrameClearDrawable(activity.window.decorView.background)
                .setBlurAutoUpdate(true)
            background = GradientDrawable().apply { setColor(glassFillColor); cornerRadius = radiusPx }
            clipToOutline = true
            if (blurRadius > MIN_BLUR_RADIUS) setBlurRadius(blurRadius)
            setBlurEnabled(blurRadius > MIN_BLUR_RADIUS)
            setOverlayColor(glassFillColor)
        }
        blurViewReference = WeakReference(binding.blurBottomStatus)
        glassDrawableReference = WeakReference(glassDrawable)

        attachBlobAnimation(activity, binding)
        updateChildViews(activity, binding, isBlurOn = true)
    }

    private fun applyBlurOff(
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
        radiusPx: Float,
        onTestClick: () -> Unit
    ) {
        blurViewReference?.clear()
        glassDrawableReference?.clear()
        detachBlobAnimation()

        val solidDrawable = GradientDrawable().apply {
            setColor(activity.getColorAttr("colorPrimary"))
            cornerRadius = radiusPx
        }

        binding.blurBottomStatus.apply {
            setBlurAutoUpdate(false)
            setBlurEnabled(false)
            setOverlayColor(Color.TRANSPARENT)
            background = solidDrawable
            clipToOutline = true
            foreground = null
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
            applyBounceTouchAnimation(this, null, onTestClick)
        }

        binding.routingMode.apply {
            setBlurAutoUpdate(false)
            setBlurEnabled(false)
            setOverlayColor(Color.TRANSPARENT)
            refreshTheme()
        }
        updateChildViews(activity, binding, isBlurOn = false)
    }

    private fun attachBlobAnimation(activity: AppCompatActivity, binding: ActivityMainBinding) {
        detachBlobAnimation()
        if (!isBlobAnimEnabled()) return

        val blobColors = intArrayOf(
            activity.getColorAttr("colorPrimary"),
            activity.getColorAttr("colorSecondary"),
            activity.getColorAttr("colorTertiary"),
            activity.getColorAttr("colorError")
        )

        val blobView = BlobView(activity)
        binding.blurBottomStatus.addView(
            blobView,
            0,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        blobView.start(blobColors)

        blobViewReference = WeakReference(blobView)
    }

    private fun detachBlobAnimation() {
        blobViewReference?.get()?.let { view ->
            view.stop()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        blobViewReference?.clear()
        blobViewReference = null
    }

    private fun updateChildViews(
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
        isBlurOn: Boolean
    ) {
        val ipStateColor = activity.getColorAttr(if (isBlurOn) "colorOnSurfaceVariant" else "colorOnPrimary")
        val testStateColor = activity.getColorAttr(if (isBlurOn) "colorOnSurface" else "colorOnPrimary")

        binding.tvIpState.apply {
            setTextColor(ipStateColor)
            alpha = if (isBlurOn) 1f else 0.8f
        }

        binding.tvTestState.setTextColor(testStateColor)
        binding.fab.apply {
            visibility = View.VISIBLE
            val fabContentColor = ColorStateList.valueOf(
                activity.getColorAttr(if (isBlurOn) "colorOnPrimary" else "colorPrimary")
            )
            backgroundTintList = ColorStateList.valueOf(
                activity.getColorAttr(if (isBlurOn) "colorPrimary" else "colorOnPrimary")
            )
            iconTint = fabContentColor
            setTextColor(fabContentColor)
        }
    }
}
