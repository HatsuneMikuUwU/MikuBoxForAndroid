package io.nekohasekai.sagernet.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr

/**
 * Ported from MikuRay's section header icon. Upstream also supports
 * character-art variants (miku/teto/neru); those drawable assets are
 * MikuRay-specific and are not part of this port, so this always renders the
 * gradient badge style.
 */
class UwuHeaderIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var sectionIconRes: Int = 0

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.UwuHeaderIconView)
        sectionIconRes = ta.getResourceId(R.styleable.UwuHeaderIconView_sectionIcon, 0)
        ta.recycle()
    }

    private val styleChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyStyle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStyle()
        ContextCompat.registerReceiver(
            context,
            styleChangeReceiver,
            IntentFilter(Action.CATEGORY_STYLE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(styleChangeReceiver)
        } catch (_: Exception) {
        }
    }

    fun applyStyle() {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics,
        ).toInt()
        layoutParams = layoutParams?.also {
            it.width = sizePx
            it.height = sizePx
        }
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics,
        ).toInt()
        setPadding(pad, pad, pad, pad)
        background = buildGradientBackground()

        val iconRes = if (sectionIconRes != 0) sectionIconRes else R.drawable.ic_sparkles_24dp
        val iconSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics,
        ).toInt()
        val iconDrawable = ContextCompat.getDrawable(context, iconRes)
            ?.mutate()
            ?.also { it.setBounds(0, 0, iconSizePx, iconSizePx) }

        scaleType = ScaleType.CENTER
        setImageDrawable(iconDrawable)
        imageTintList = ColorStateList.valueOf(context.getColorAttr(R.attr.colorOnPrimary))
    }

    private fun buildGradientBackground(): GradientDrawable {
        val colorStart = context.getColorAttr(R.attr.colorPrimary)
        val colorEnd = context.getColorAttr(R.attr.colorTertiary)

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd),
        ).apply {
            shape = GradientDrawable.OVAL
        }
    }
}
