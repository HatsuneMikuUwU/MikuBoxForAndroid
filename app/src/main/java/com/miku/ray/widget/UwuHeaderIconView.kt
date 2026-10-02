package com.miku.ray.widget

import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.util.getColorAttr
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UwuHeaderIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var sectionIconRes: Int = 0

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.UwuHeaderIconView)
        sectionIconRes = ta.getResourceId(R.styleable.UwuHeaderIconView_sectionIcon, 0)
        ta.recycle()
    }

    private var viewScope: CoroutineScope? = null
    private var styleChangeJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStyle()
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        viewScope = scope
        styleChangeJob = scope.launch {
            SettingsChangeManager.uiCustomizationChanged.collect {
                applyStyle()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        styleChangeJob?.cancel()
        styleChangeJob = null
        viewScope = null
    }

    fun applyStyle() {
        val style = MmkvManager.decodeSettingsString(AppConfig.PREF_CATEGORY_STYLE, "gradient")
        if (style == "gradient") {
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics
            ).toInt()
            layoutParams = layoutParams?.also {
                it.width = sizePx
                it.height = sizePx
            }
            val pad = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics
            ).toInt()
            setPadding(pad, pad, pad, pad)
            background = buildGradientBackground()
            val iconRes = if (sectionIconRes != 0) sectionIconRes else RemixR.drawable.rmx_sparkling_line
            val iconSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics
            ).toInt()
            val iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)
            ?.mutate()
            ?.also { it.setBounds(0, 0, iconSizePx, iconSizePx) }
            scaleType = ScaleType.CENTER
            setImageDrawable(iconDrawable)
            imageTintList = android.content.res.ColorStateList.valueOf(
                context.getColorAttr("colorOnPrimary")
            )
        } else {
            setPadding(0, 0, 0, 0)
            background = null
            imageTintList = null
            setImageResource(drawableForStyle(style))
        }
    }

    private fun buildGradientBackground(): GradientDrawable {
        val colorStart = context.getColorAttr("colorPrimary")
        val colorEnd = context.getColorAttr("colorTertiary")

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply {
            shape = GradientDrawable.OVAL
        }
    }

    companion object {
        fun drawableForStyle(style: String?): Int = when (style) {
            "miku2"  -> R.drawable.uwu_icon_miku_2
            "teto"   -> R.drawable.uwu_icon_teto
            "teto2"  -> R.drawable.uwu_icon_teto_2
            "neru"   -> R.drawable.uwu_icon_neru
            "basic" -> R.drawable.uwu_icon_basic
            "cherry_pop" -> R.drawable.uwu_icon_cherry_pop
            "rabbit_hole" -> R.drawable.uwu_icon_rabbit_hole
            "mesmerizer" -> R.drawable.uwu_icon_mesmerizer
            "sakura" -> R.drawable.uwu_icon_sakura
            "magical_mirai_2024" -> R.drawable.uwu_icon_magical_mirai_2024
            "deep_sea_girl" -> R.drawable.uwu_icon_deep_sea_girl
            "snow_miku_2025" -> R.drawable.uwu_icon_snow_miku_2025
            "symphony_2022" -> R.drawable.uwu_icon_symphony_2022
            "racing_miku_2025" -> R.drawable.uwu_icon_racing_miku_2025
            "cinnamiku" -> R.drawable.uwu_icon_cinnamiku
            "retry_now" -> R.drawable.uwu_icon_retry_now
            else     -> R.drawable.uwu_icon_miku
        }
    }
}
