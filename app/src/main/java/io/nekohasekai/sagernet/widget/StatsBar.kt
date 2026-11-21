package io.nekohasekai.sagernet.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.R as MaterialR
import com.neko.blurview.BlurView
import com.neko.blurview.StrokeDrawable
import com.neko.marquee.text.AutoMarqueeTextView
import com.neko.widget.BlobView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class StatsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BlurView(context, attrs, defStyleAttr) {

    private var statusText: AutoMarqueeTextView? = null
    private var ipStatusText: AutoMarqueeTextView? = null
    private var txText: TextView? = null
    private var rxText: TextView? = null
    private var speedRow: View? = null
    private var fabButton: ServiceButton? = null
    private var blobView: BlobView? = null

    private var blurRootView: ViewGroup? = null
    private var cardClickAction: (() -> Unit)? = null
    private var isHidden = false

    var allowShow = true

    private fun ensureViews() {
        if (statusText == null) statusText = findViewById(R.id.status)
        if (ipStatusText == null) ipStatusText = findViewById(R.id.ip_status)
        if (txText == null) txText = findViewById(R.id.tx)
        if (rxText == null) rxText = findViewById(R.id.rx)
        if (speedRow == null) speedRow = findViewById(R.id.speed_row)
        if (fabButton == null) fabButton = findViewById(R.id.fab)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        ensureViews()
    }

    override fun setOnClickListener(l: OnClickListener?) {
        ensureViews()
        super.setOnClickListener(l)
    }

    fun applyMikuStyle(rootView: ViewGroup, onCardClick: () -> Unit) {
        ensureViews()
        blurRootView = rootView
        cardClickAction = onCardClick

        val density = resources.displayMetrics.density
        val radiusPx = 28f * density

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true

        if (DataStore.blurBottomStatus) {
            applyBlurOn(rootView, radiusPx, density, onCardClick)
        } else {
            applyBlurOff(radiusPx, onCardClick)
        }
    }

    fun refreshMikuStyle() {
        val root = blurRootView ?: return
        val click = cardClickAction ?: return
        applyMikuStyle(root, click)
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun alphaPercentToInt(percent: Int): Int =
        (percent.coerceIn(0, 100) / 100f * 255f).toInt().coerceIn(0, 255)

    private fun applyBlurOn(
        rootView: ViewGroup,
        radiusPx: Float,
        density: Float,
        onClick: () -> Unit
    ) {
        val blurRadius = DataStore.blurBottomRadius.toFloat().coerceIn(0f, 25f)
        val alphaPercent = DataStore.blurBottomAlpha.coerceIn(0, 100)
        val isDark = isDarkMode()

        val baseColor = context.getColorAttr(MaterialR.attr.colorSurfaceContainer)
        val glassFillColor = withAlpha(baseColor, alphaPercentToInt(alphaPercent))

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

        val decorBg = (context as? Activity)?.window?.decorView?.background
        setupWith(rootView)
            .setFrameClearDrawable(decorBg)
            .setBlurAutoUpdate(true)

        background = glassDrawable
        foreground = LayerDrawable(arrayOf(strokeDrawable, glowDrawable))
        if (blurRadius > 0f) {
            setBlurRadius(blurRadius)
        }
        setBlurEnabled(blurRadius > 0f)
        setOverlayColor(glassFillColor)

        applyBounceTouchAnimation(glowDrawable, onClick)
        attachBlobAnimation()
        updateChildColors(isBlurOn = true)
    }

    private fun applyBlurOff(radiusPx: Float, onClick: () -> Unit) {
        detachBlobAnimation()

        val solidDrawable = GradientDrawable().apply {
            setColor(context.getColorAttr(androidx.appcompat.R.attr.colorPrimary))
            cornerRadius = radiusPx
        }

        setBlurAutoUpdate(false)
        setBlurEnabled(false)
        setOverlayColor(Color.TRANSPARENT)
        background = solidDrawable
        foreground = null
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        if (!isHidden) translationY = 0f

        applyBounceTouchAnimation(null, onClick)
        updateChildColors(isBlurOn = false)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applyBounceTouchAnimation(
        glowDrawable: GradientDrawable?,
        onClick: () -> Unit
    ) {
        isClickable = true
        setOnClickListener { onClick() }

        var touchStartX = 0f
        var touchStartY = 0f

        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    v.isPressed = true
                    glowDrawable?.alpha = 255
                    v.animate().cancel()
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate().cancel()
                    val deltaX = event.rawX - touchStartX
                    val deltaY = event.rawY - touchStartY
                    val stretchX = 1.04f + (abs(deltaX) / v.width.toFloat().coerceAtLeast(1f) * 0.12f).coerceAtMost(0.06f)
                    val stretchY = 1.04f + (abs(deltaY) / v.height.toFloat().coerceAtLeast(1f) * 0.12f).coerceAtMost(0.06f)
                    v.scaleX = stretchX
                    v.scaleY = stretchY
                    v.translationX = deltaX * 0.14f
                    if (!isHidden) v.translationY = deltaY * 0.14f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    glowDrawable?.alpha = 0
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .translationY(if (isHidden) height.toFloat() + 120f else 0f)
                        .setDuration(360)
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

    private fun attachBlobAnimation() {
        detachBlobAnimation()
        if (!DataStore.blurBottomBlobAnim) return

        val blobColors = intArrayOf(
            context.getColorAttr(androidx.appcompat.R.attr.colorPrimary),
            context.getColorAttr(MaterialR.attr.colorSecondary),
            context.getColorAttr(MaterialR.attr.colorTertiary),
            context.getColorAttr(androidx.appcompat.R.attr.colorError)
        )

        val view = BlobView(context)
        addView(
            view,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        view.start(blobColors)
        blobView = view
    }

    private fun detachBlobAnimation() {
        blobView?.let { view ->
            view.stop()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        blobView = null
    }

    private fun updateChildColors(isBlurOn: Boolean) {
        ensureViews()
        val secondaryColor = context.getColorAttr(
            if (isBlurOn) MaterialR.attr.colorOnSurfaceVariant else MaterialR.attr.colorOnPrimary
        )
        val primaryTextColor = context.getColorAttr(
            if (isBlurOn) MaterialR.attr.colorOnSurface else MaterialR.attr.colorOnPrimary
        )

        txText?.setTextColor(secondaryColor)
        rxText?.setTextColor(secondaryColor)
        ipStatusText?.apply {
            setTextColor(secondaryColor)
            alpha = if (isBlurOn) 1f else 0.85f
        }
        statusText?.setTextColor(primaryTextColor)

        fabButton?.apply {
            val contentColor = ColorStateList.valueOf(
                context.getColorAttr(
                    if (isBlurOn) MaterialR.attr.colorOnPrimary else androidx.appcompat.R.attr.colorPrimary
                )
            )
            backgroundTintList = ColorStateList.valueOf(
                context.getColorAttr(
                    if (isBlurOn) androidx.appcompat.R.attr.colorPrimary else MaterialR.attr.colorOnPrimary
                )
            )
            iconTint = contentColor
            setTextColor(contentColor)
        }
    }

    fun performShow() {
        if (!allowShow) return
        isHidden = false
        visibility = View.VISIBLE
        animate().cancel()
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220)
            .start()
    }

    fun performHide() {
        isHidden = true
        animate().cancel()
        val slideDistance = (height.takeIf { it > 0 } ?: 240).toFloat() + 64f
        animate()
            .translationY(slideDistance)
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                if (isHidden) visibility = View.GONE
            }
            .start()
    }

    private fun setStatus(text: CharSequence) {
        ensureViews()
        statusText?.text = text
        TooltipCompat.setTooltipText(this, text)
    }

    fun changeState(state: BaseService.State) {
        ensureViews()
        val activity = context as? LifecycleOwner ?: return
        activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(80L)
            when (state) {
                BaseService.State.Connected -> {
                    if (allowShow) performShow()
                    speedRow?.visibility = View.VISIBLE
                    setStatus(context.getText(R.string.vpn_connected))
                    ipStatusText?.visibility = View.GONE
                    ipStatusText?.text = ""
                }

                else -> {
                    if (allowShow) performShow()
                    speedRow?.visibility = View.GONE
                    updateSpeed(0, 0)
                    ipStatusText?.visibility = View.GONE
                    ipStatusText?.text = ""
                    setStatus(
                        context.getText(
                            when (state) {
                                BaseService.State.Connecting -> R.string.connecting
                                BaseService.State.Stopping -> R.string.stopping
                                else -> R.string.not_connected
                            }
                        )
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateSpeed(txRate: Long, rxRate: Long) {
        ensureViews()
        txText?.text = "▲ ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, txRate)
            )
        }"
        rxText?.text = "▼ ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, rxRate)
            )
        }"
    }

    private fun getFlagEmoji(countryCode: String): String {
        return try {
            if (countryCode.length != 2) {
                "❓"
            } else {
                val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
                val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
                String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
            }
        } catch (e: Exception) {
            "❓"
        }
    }

    private suspend fun getPublicIpInfo(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://speed.cloudflare.com/cdn-cgi/trace")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                )

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                var ip = ""
                var countryCode = ""

                response.lines().forEach { line ->
                    if (line.startsWith("ip=")) {
                        ip = line.substringAfter("ip=")
                    } else if (line.startsWith("loc=")) {
                        countryCode = line.substringAfter("loc=")
                    }
                }

                if (ip.isNotEmpty()) {
                    val flag = getFlagEmoji(countryCode)
                    "IP: $ip ($flag $countryCode)"
                } else {
                    context.getString(R.string.ip_info_failed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                context.getString(R.string.ip_info_failed)
            }
        }
    }

    fun testConnection() {
        ensureViews()
        val activity = context as? MainActivity ?: return
        isEnabled = false
        setStatus(app.getText(R.string.connection_test_testing))

        ipStatusText?.visibility = View.GONE
        ipStatusText?.text = ""

        runOnDefaultDispatcher {
            try {
                val elapsed = activity.urlTest()
                val latencyResult = app.getString(
                    if (DataStore.connectionTestURL.startsWith("https://")) {
                        R.string.connection_test_available
                    } else {
                        R.string.connection_test_available_http
                    }, elapsed
                )

                onMainDispatcher {
                    isEnabled = true
                    setStatus(latencyResult)

                    if (DataStore.connectionTestWithIp) {
                        activity.lifecycleScope.launch {
                            val ipInfo = getPublicIpInfo()

                            if (DataStore.showIpInTwoLine) {
                                ipStatusText?.text = ipInfo
                                ipStatusText?.visibility = View.VISIBLE
                            } else {
                                setStatus("$latencyResult • $ipInfo")
                                ipStatusText?.visibility = View.GONE
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e.toString())
                onMainDispatcher {
                    isEnabled = true
                    setStatus(app.getText(R.string.connection_test_testing))
                    ipStatusText?.visibility = View.GONE
                    ipStatusText?.text = ""
                    activity.snackbar(
                        app.getString(
                            R.string.connection_test_error, e.readableMessage
                        )
                    ).show()
                }
            }
        }
    }
}
