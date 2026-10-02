package com.miku.ray.extension

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.miku.ray.AngApplication
import com.miku.ray.R
import com.miku.ray.enums.EConfigType
import com.miku.ray.util.getColorAttr
import com.miku.ray.toasty.Toasty
import com.miku.ray.toasty.ToastyUtils
import java.io.Serializable
import java.lang.ref.WeakReference
import java.net.URI
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

val Context.v2RayApplication: AngApplication?
get() = applicationContext as? AngApplication

object ForegroundActivityTracker : Application.ActivityLifecycleCallbacks {

    private var resumedActivity: WeakReference<Activity>? = null

    val currentActivity: Activity?
    get() = resumedActivity?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) {
            resumedActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

fun Context.vibrateOnError() {
    try {
        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200L)
            }
        }
    } catch (e: Exception) {
    }
}

fun Context.toast(message: Int) {
    Toasty.normal(this, message).show()
}

fun Context.toast(message: CharSequence) {
    Toasty.normal(this, message).show()
}

fun Context.toastSuccess(message: Int) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastSuccess(message: CharSequence) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: Int) {
    vibrateOnError()
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: CharSequence) {
    vibrateOnError()
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastInfo(message: Int) {
    Toasty.info(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastInfo(message: CharSequence) {
    Toasty.info(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastWarning(message: Int) {
    Toasty.warning(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastWarning(message: CharSequence) {
    Toasty.warning(this, message, Toast.LENGTH_SHORT, true).show()
}

private fun showSnackbar(
    context: Context,
    title: CharSequence,
    message: CharSequence,
    @DrawableRes iconRes: Int,
    backgroundColorAttr: String?,
    textColorAttr: String?,
    duration: Int
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post {
            showSnackbar(context, title, message, iconRes, backgroundColorAttr, textColorAttr, duration)
        }
        return
    }

    val activity = context as? Activity ?: ForegroundActivityTracker.currentActivity
    val parent = activity?.findViewById<View>(android.R.id.content)

    val fallbackMessage = if (title.isNotNullEmpty()) "$title: $message" else message

    fun showToastyFallback() {
        val toastDuration = Toast.LENGTH_LONG
        when (iconRes) {
            RemixR.drawable.rmx_checkbox_circle_line -> Toasty.success(context, fallbackMessage, toastDuration, true).show()
            RemixR.drawable.rmx_error_warning_line -> Toasty.error(context, fallbackMessage, toastDuration, true).show()
            RemixR.drawable.rmx_information_line -> Toasty.custom(context, fallbackMessage, ToastyUtils.getDrawable(context, iconRes),
                ToastyUtils.getColorAttr(context, "colorTertiary", 0),
                ToastyUtils.getColorAttr(context, "colorOnTertiary", 0),
                toastDuration, true, true).show()
            else -> Toasty.normal(context, fallbackMessage, toastDuration).show()
        }
    }

    if (activity == null || parent == null) {
        showToastyFallback()
        return
    }

    try {
        val snackbar = Snackbar.make(parent, "", Snackbar.LENGTH_INDEFINITE)
        val snackbarLayout = snackbar.view as ViewGroup
        snackbarLayout.contentDescription = fallbackMessage

        snackbarLayout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?.visibility = View.INVISIBLE

        val contentView = LayoutInflater.from(activity)
        .inflate(R.layout.layout_snackbar_custom, snackbarLayout, false)

        val resolvedTextColor = if (textColorAttr != null) {
            activity.getColorAttr(textColorAttr)
        } else {
            activity.getColorAttr("colorOnSurfaceInverse")
        }

        contentView.findViewById<ImageView>(R.id.iv_snackbar_icon)?.apply {
            setImageResource(iconRes)
            DrawableCompat.setTint(drawable.mutate(), resolvedTextColor)
        }
        contentView.findViewById<TextView>(R.id.tv_snackbar_title)?.apply {
            if (title.isNotNullEmpty()) {
                text = title
                visibility = View.VISIBLE
                setTextColor(resolvedTextColor)
            } else {
                visibility = View.GONE
            }
        }
        contentView.findViewById<TextView>(R.id.tv_snackbar_message)?.apply {
            text = message
            setTextColor(resolvedTextColor)
        }

        snackbarLayout.addView(contentView, 0)

        (snackbarLayout.parent as? ViewGroup)?.bringChildToFront(snackbarLayout)

        val layoutParams = snackbarLayout.layoutParams
        when (layoutParams) {
            is CoordinatorLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
            is FrameLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
        }
        snackbarLayout.layoutParams = layoutParams

        ViewCompat.setOnApplyWindowInsetsListener(snackbarLayout) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            val margin5dp = (5f * view.context.resources.displayMetrics.density).toInt()
            val margin16dp = (16f * view.context.resources.displayMetrics.density).toInt()

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarTop + margin5dp
                bottomMargin = margin5dp
                leftMargin = margin16dp
                rightMargin = margin16dp
            }
            insets
        }

        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE

        snackbarLayout.doOnPreDraw { view ->
            view.translationY = -view.height.toFloat()
            view.animate()
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        }

        fun slideRightThenDismiss() {
            if (!snackbarLayout.isAttachedToWindow) {
                snackbar.dismiss()
                return
            }
            snackbarLayout.animate()
            .translationX(snackbarLayout.width.toFloat())
            .setDuration(300L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { snackbar.dismiss() }
            .start()
        }

        val autoDismissDelayMs = when (duration) {
            Snackbar.LENGTH_INDEFINITE -> null
            Snackbar.LENGTH_SHORT -> 1500L
            else -> 2750L
        }
        autoDismissDelayMs?.let {
            Handler(Looper.getMainLooper()).postDelayed(::slideRightThenDismiss, it)
        }

        val cornerRadiusPx = 28f * activity.resources.displayMetrics.density

        val backgroundColor = if (backgroundColorAttr != null) {
            activity.getColorAttr(backgroundColorAttr)
        } else {
            activity.getColorAttr("colorSurfaceInverse")
        }

        snackbarLayout.backgroundTintList = null
        snackbarLayout.backgroundTintMode = null

        snackbarLayout.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder().setAllCornerSizes(cornerRadiusPx).build()
        ).apply {
            fillColor = ColorStateList.valueOf(backgroundColor)
            elevation = snackbarLayout.elevation
        }

        snackbar.show()

    } catch (e: Exception) {
        e.printStackTrace()
        showToastyFallback()
    }
}

fun Context.snackbarDefault(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), RemixR.drawable.rmx_information_line,
        "colorTertiary",
        "colorOnTertiary",
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarDefault(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, RemixR.drawable.rmx_information_line,
        "colorTertiary",
        "colorOnTertiary",
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarSuccess(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), RemixR.drawable.rmx_checkbox_circle_line,
        "colorPrimary",
        "colorOnPrimary",
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarSuccess(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, RemixR.drawable.rmx_checkbox_circle_line,
        "colorPrimary",
        "colorOnPrimary",
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarError(message: Int, title: CharSequence = "") {
    vibrateOnError()
    showSnackbar(
        this, title, getString(message), RemixR.drawable.rmx_error_warning_line,
        "colorError",
        "colorOnError",
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarError(message: CharSequence, title: CharSequence = "") {
    vibrateOnError()
    showSnackbar(
        this, title, message, RemixR.drawable.rmx_error_warning_line,
        "colorError",
        "colorOnError",
        Snackbar.LENGTH_LONG
    )
}

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}

val URI.idnHost: String
get() = host?.replace("[", "")?.replace("]", "").orEmpty()

fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

fun String.toLongEx(): Long = toLongOrNull() ?: 0

fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        callback()
        if (onetime) context.unregisterReceiver(this)
    }
}.apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
    } else {
        registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
        })
    }
}

inline fun <reified T : Serializable> Bundle.serializable(key: String): T? {
    classLoader = T::class.java.classLoader
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(key) as? T
    }
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? {
    setExtrasClassLoader(T::class.java.classLoader)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }
}

fun CharSequence?.isNotNullEmpty(): Boolean = !this.isNullOrBlank()

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}

fun String.matchesPattern(regex: Regex?, keyword: String?, ignoreCase: Boolean = true): Boolean {
    if (keyword.isNullOrEmpty()) {
        return true
    }
    return regex?.containsMatchIn(this)
    ?: this.contains(keyword, ignoreCase = ignoreCase)
}

fun EConfigType.isGroupType(): Boolean {
    return this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

fun EConfigType.isComplexType(): Boolean {
    return this == EConfigType.CUSTOM || this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

suspend fun delay(millis: Int) {
    kotlinx.coroutines.delay(millis.toLong().milliseconds)
}

suspend fun delay(millis: Long) {
    kotlinx.coroutines.delay(millis.milliseconds)
}

fun View.applyEdgeToEdgeListInsets() {
    if (this is ViewGroup) clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(bottom = maxOf(systemBars.bottom, displayCutout.bottom, ime.bottom))
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

fun androidx.preference.PreferenceFragmentCompat.applyEdgeToEdgeListInsets() {
    listView?.applyEdgeToEdgeListInsets()
}
