package com.miku.ray.toasty

import com.miku.ray.remixicon.R as RemixR
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.miku.ray.R

object Toasty {

    private var tintIcon = true
    private var allowQueue = true
    private var toastGravity = -1
    private var xOffset = -1
    private var yOffset = -1
    private var isRTL = false

    private var lastToast: Toast? = null

    const val LENGTH_SHORT = Toast.LENGTH_SHORT
    const val LENGTH_LONG = Toast.LENGTH_LONG

    @JvmStatic @CheckResult
    fun normal(context: Context, @StringRes message: Int): Toast =
    normal(context, context.getString(message), Toast.LENGTH_SHORT, null, false)

    @JvmStatic @CheckResult
    fun normal(context: Context, message: CharSequence): Toast =
    normal(context, message, Toast.LENGTH_SHORT, null, false)

    @JvmStatic @CheckResult
    fun normal(context: Context, @StringRes message: Int, icon: Drawable?): Toast =
    normal(context, context.getString(message), Toast.LENGTH_SHORT, icon, true)

    @JvmStatic @CheckResult
    fun normal(context: Context, message: CharSequence, icon: Drawable?): Toast =
    normal(context, message, Toast.LENGTH_SHORT, icon, true)

    @JvmStatic @CheckResult
    fun normal(context: Context, @StringRes message: Int, duration: Int): Toast =
    normal(context, context.getString(message), duration, null, false)

    @JvmStatic @CheckResult
    fun normal(context: Context, message: CharSequence, duration: Int): Toast =
    normal(context, message, duration, null, false)

    @JvmStatic @CheckResult
    fun normal(context: Context, @StringRes message: Int, duration: Int, icon: Drawable?): Toast =
    normal(context, context.getString(message), duration, icon, true)

    @JvmStatic @CheckResult
    fun normal(context: Context, message: CharSequence, duration: Int, icon: Drawable?): Toast =
    normal(context, message, duration, icon, true)

    @JvmStatic @CheckResult
    fun normal(context: Context, @StringRes message: Int, duration: Int, icon: Drawable?, withIcon: Boolean): Toast =
    normal(context, context.getString(message), duration, icon, withIcon)

    @JvmStatic @CheckResult
    fun normal(context: Context, message: CharSequence, duration: Int, icon: Drawable?, withIcon: Boolean): Toast =
    custom(context, message, icon,
        ToastyUtils.getColorAttr(context, "colorTertiary", 0),
        ToastyUtils.getColorAttr(context, "colorOnTertiary", 0),
        duration, withIcon, true)

    @JvmStatic @CheckResult
    fun warning(context: Context, @StringRes message: Int): Toast =
    warning(context, context.getString(message), Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun warning(context: Context, message: CharSequence): Toast =
    warning(context, message, Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun warning(context: Context, @StringRes message: Int, duration: Int): Toast =
    warning(context, context.getString(message), duration, true)

    @JvmStatic @CheckResult
    fun warning(context: Context, message: CharSequence, duration: Int): Toast =
    warning(context, message, duration, true)

    @JvmStatic @CheckResult
    fun warning(context: Context, @StringRes message: Int, duration: Int, withIcon: Boolean): Toast =
    warning(context, context.getString(message), duration, withIcon)

    @JvmStatic @CheckResult
    fun warning(context: Context, message: CharSequence, duration: Int, withIcon: Boolean): Toast =
    custom(context, message, ToastyUtils.getDrawable(context, RemixR.drawable.rmx_error_warning_line),
        ToastyUtils.getColorAttr(context, "colorTertiary", 0),
        ToastyUtils.getColorAttr(context, "colorOnTertiary", 0),
        duration, withIcon, true)

    @JvmStatic @CheckResult
    fun info(context: Context, @StringRes message: Int): Toast =
    info(context, context.getString(message), Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun info(context: Context, message: CharSequence): Toast =
    info(context, message, Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun info(context: Context, @StringRes message: Int, duration: Int): Toast =
    info(context, context.getString(message), duration, true)

    @JvmStatic @CheckResult
    fun info(context: Context, message: CharSequence, duration: Int): Toast =
    info(context, message, duration, true)

    @JvmStatic @CheckResult
    fun info(context: Context, @StringRes message: Int, duration: Int, withIcon: Boolean): Toast =
    info(context, context.getString(message), duration, withIcon)

    @JvmStatic @CheckResult
    fun info(context: Context, message: CharSequence, duration: Int, withIcon: Boolean): Toast =
    custom(context, message, ToastyUtils.getDrawable(context, RemixR.drawable.rmx_information_line),
        ToastyUtils.getColorAttr(context, "colorSurfaceInverse", 0),
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
        duration, withIcon, true)

    @JvmStatic @CheckResult
    fun success(context: Context, @StringRes message: Int): Toast =
    success(context, context.getString(message), Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun success(context: Context, message: CharSequence): Toast =
    success(context, message, Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun success(context: Context, @StringRes message: Int, duration: Int): Toast =
    success(context, context.getString(message), duration, true)

    @JvmStatic @CheckResult
    fun success(context: Context, message: CharSequence, duration: Int): Toast =
    success(context, message, duration, true)

    @JvmStatic @CheckResult
    fun success(context: Context, @StringRes message: Int, duration: Int, withIcon: Boolean): Toast =
    success(context, context.getString(message), duration, withIcon)

    @JvmStatic @CheckResult
    fun success(context: Context, message: CharSequence, duration: Int, withIcon: Boolean): Toast =
    custom(context, message, ToastyUtils.getDrawable(context, RemixR.drawable.rmx_checkbox_circle_line),
        ToastyUtils.getColorAttr(context, "colorPrimary", 0),
        ToastyUtils.getColorAttr(context, "colorOnPrimary", 0),
        duration, withIcon, true)

    @JvmStatic @CheckResult
    fun error(context: Context, @StringRes message: Int): Toast =
    error(context, context.getString(message), Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun error(context: Context, message: CharSequence): Toast =
    error(context, message, Toast.LENGTH_SHORT, true)

    @JvmStatic @CheckResult
    fun error(context: Context, @StringRes message: Int, duration: Int): Toast =
    error(context, context.getString(message), duration, true)

    @JvmStatic @CheckResult
    fun error(context: Context, message: CharSequence, duration: Int): Toast =
    error(context, message, duration, true)

    @JvmStatic @CheckResult
    fun error(context: Context, @StringRes message: Int, duration: Int, withIcon: Boolean): Toast =
    error(context, context.getString(message), duration, withIcon)

    @JvmStatic @CheckResult
    fun error(context: Context, message: CharSequence, duration: Int, withIcon: Boolean): Toast =
    custom(context, message, ToastyUtils.getDrawable(context, RemixR.drawable.rmx_error_warning_line),
        ToastyUtils.getColorAttr(context, "colorError", 0),
        ToastyUtils.getColorAttr(context, "colorOnError", 0),
        duration, withIcon, true)

    @JvmStatic @CheckResult
    fun custom(context: Context, @StringRes message: Int, icon: Drawable?, duration: Int, withIcon: Boolean): Toast =
    custom(context, context.getString(message), icon, -1,
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
        duration, withIcon, false)

    @JvmStatic @CheckResult
    fun custom(context: Context, message: CharSequence, icon: Drawable?, duration: Int, withIcon: Boolean): Toast =
    custom(context, message, icon, -1,
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
        duration, withIcon, false)

    @JvmStatic @CheckResult
    fun custom(context: Context, @StringRes message: Int, @DrawableRes iconRes: Int, @ColorRes tintColorRes: Int, duration: Int, withIcon: Boolean, shouldTint: Boolean): Toast =
    custom(context, context.getString(message), ToastyUtils.getDrawable(context, iconRes),
        ToastyUtils.getColor(context, tintColorRes),
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
        duration, withIcon, shouldTint)

    @JvmStatic @CheckResult
    fun custom(context: Context, message: CharSequence, @DrawableRes iconRes: Int, @ColorRes tintColorRes: Int, duration: Int, withIcon: Boolean, shouldTint: Boolean): Toast =
    custom(context, message, ToastyUtils.getDrawable(context, iconRes),
        ToastyUtils.getColor(context, tintColorRes),
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0),
        duration, withIcon, shouldTint)

    @JvmStatic @CheckResult
    fun custom(context: Context, @StringRes message: Int, icon: Drawable?, @ColorRes tintColorRes: Int, duration: Int, withIcon: Boolean, shouldTint: Boolean): Toast =
    custom(context, context.getString(message), icon, ToastyUtils.getColor(context, tintColorRes),
        ToastyUtils.getColorAttr(context, "colorOnSurfaceInverse", 0), duration, withIcon, shouldTint)

    @JvmStatic @CheckResult
    fun custom(context: Context, @StringRes message: Int, icon: Drawable?, @ColorRes tintColorRes: Int, @ColorRes textColorRes: Int, duration: Int, withIcon: Boolean, shouldTint: Boolean): Toast =
    custom(context, context.getString(message), icon, ToastyUtils.getColor(context, tintColorRes),
        ToastyUtils.getColor(context, textColorRes), duration, withIcon, shouldTint)

    @SuppressLint("ShowToast", "InflateParams", "DiscouragedApi")
    @Suppress("DEPRECATION")
    @JvmStatic @CheckResult
    fun custom(context: Context, message: CharSequence, icon: Drawable?, @ColorInt tintColor: Int, @ColorInt textColor: Int, duration: Int, withIcon: Boolean, shouldTint: Boolean): Toast {
        val currentToast = Toast.makeText(context, "", duration)
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val toastLayout = inflater.inflate(R.layout.toast_layout, null)

        val toastRoot = toastLayout.findViewById<LinearLayout>(R.id.toast_root)
        val toastIcon = toastLayout.findViewById<ImageView>(R.id.toast_icon)
        val toastTextView = toastLayout.findViewById<TextView>(R.id.toast_text)

        var drawableFrame: Drawable? = null

        if (shouldTint) {
            try {
                drawableFrame = ToastyUtils.tint9PatchDrawableFrame(context, tintColor)
            } catch (e: ClassCastException) {
                drawableFrame = ToastyUtils.getDrawable(context, R.drawable.uwu_bg_sin)?.apply {
                    mutate().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                }
            }
        } else {
            drawableFrame = ToastyUtils.getDrawable(context, R.drawable.uwu_bg_sin)
        }

        ToastyUtils.setBackground(toastLayout, drawableFrame)

        if (withIcon) {
            requireNotNull(icon) { "Avoid passing 'icon' as null if 'withIcon' is set to true" }
            if (isRTL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                toastRoot.layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            ToastyUtils.setBackground(toastIcon, if (tintIcon) ToastyUtils.tintIcon(icon, textColor) else icon)
        } else {
            toastIcon.visibility = View.GONE
        }

        toastTextView.text = message
        toastTextView.setTextColor(textColor)

        currentToast.view = toastLayout

        if (!allowQueue) {
            lastToast?.cancel()
            lastToast = currentToast
        }

        if (toastGravity == -1) {
            val marginPx = (120f * context.resources.displayMetrics.density).toInt()
            currentToast.setGravity(Gravity.BOTTOM, 0, marginPx)
        } else {
            currentToast.setGravity(
                toastGravity,
                if (xOffset == -1) currentToast.xOffset else xOffset,
                if (yOffset == -1) currentToast.yOffset else yOffset
            )
        }

        return currentToast
    }

    class Config private constructor() {
        private var tintIcon = Toasty.tintIcon
        private var allowQueue = true
        private var toastGravity = Toasty.toastGravity
        private var xOffset = Toasty.xOffset
        private var yOffset = Toasty.yOffset
        private var isRTL = false

        companion object {
            @JvmStatic @CheckResult
            fun getInstance(): Config = Config()

            @JvmStatic
            fun reset() {
                Toasty.tintIcon = true
                Toasty.allowQueue = true
                Toasty.toastGravity = -1
                Toasty.xOffset = -1
                Toasty.yOffset = -1
                Toasty.isRTL = false
            }
        }

        @CheckResult
        fun tintIcon(tintIcon: Boolean): Config {
            this.tintIcon = tintIcon
            return this
        }

        @CheckResult
        fun allowQueue(allowQueue: Boolean): Config {
            this.allowQueue = allowQueue
            return this
        }

        @CheckResult
        fun setGravity(gravity: Int, xOffset: Int, yOffset: Int): Config {
            this.toastGravity = gravity
            this.xOffset = xOffset
            this.yOffset = yOffset
            return this
        }

        @CheckResult
        fun setGravity(gravity: Int): Config {
            this.toastGravity = gravity
            return this
        }

        @CheckResult
        @Deprecated("No longer used or needed")
        fun supportDarkTheme(supportDarkTheme: Boolean): Config = this

        fun setRTL(isRTL: Boolean): Config {
            this.isRTL = isRTL
            return this
        }

        fun apply() {
            Toasty.tintIcon = tintIcon
            Toasty.allowQueue = allowQueue
            Toasty.toastGravity = toastGravity
            Toasty.xOffset = xOffset
            Toasty.yOffset = yOffset
            Toasty.isRTL = isRTL
        }
    }
}
