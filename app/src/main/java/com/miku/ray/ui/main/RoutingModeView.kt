package com.miku.ray.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.miku.ray.util.getColorAttr
import com.miku.ray.util.getActivity
import com.miku.ray.util.showBlur
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.MikuRouting
import com.miku.ray.R

/** Slide first, resize second. Cancelling a transition always starts from its visible frame. */
class RoutingModeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : com.miku.ray.blurview.BlurView(context, attrs) {
    private val modes = listOf("rule", "global", "direct")
    private val track = FrameLayout(context)
    private val indicator = View(context)
    private val labels = mutableListOf<TextView>()
    private val exit = TextView(context)
    private var state = MikuRouting.State("rule", null, emptyList())
    var onSelectionChanged: (() -> Unit)? = null
    var selectionVersion = 0L
        private set
    private var shownMode: String? = null
    private var animation: ValueAnimator? = null
    private var generation = 0
    private var picker: androidx.appcompat.app.AlertDialog? = null
    private var primary = color(androidx.appcompat.R.attr.colorPrimary)
    private var textColor = color(com.google.android.material.R.attr.colorOnSurface)
    private fun color(attr: Int) = context.getColorAttr(attr)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(fill: Int, radius: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat() }

    init {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(column, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = shape(context.getColorAttr("colorCard"), 28)
        elevation = dp(3).toFloat()
        clipToOutline = true
        column.addView(track, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        indicator.background = shape(primary, 23)
        track.addView(indicator, FrameLayout.LayoutParams(0, LayoutParams.MATCH_PARENT))
        val row = LinearLayout(context)
        track.addView(row, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listOf(R.string.mihomo_mode_rule, R.string.mihomo_mode_global, R.string.mihomo_mode_direct).forEachIndexed { index, title ->
            val label = TextView(context).apply {
                setText(title); gravity = Gravity.CENTER; textSize = 14f
                setTextColor(textColor); isClickable = true; isFocusable = true
                // N15: these tabs are created in code, so without an id they
                // only ever appear in screen-reader trees and uiautomator
                // dumps as anonymous text — give them stable ones.
                id = when (index) {
                    0 -> R.id.routing_mode_tab_rule
                    1 -> R.id.routing_mode_tab_global
                    else -> R.id.routing_mode_tab_direct
                }
                setOnClickListener { chooseMode(modes[index]) }
            }
            labels += label
            row.addView(label, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        exit.apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(18), 0, dp(18), 0)
            textSize = 14f; setTextColor(textColor)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            isClickable = true; isFocusable = true
            setOnClickListener { showExits() }
            visibility = GONE
        }
        column.addView(exit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0))
        track.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL && r > l) {
                cancelAnimation()
                indicator.layoutParams = indicator.layoutParams.apply { width = (r - l) / 3 }
                indicator.translationX = targetX(state.mode)
                setExpanded(state.mode == "global", false)
            }
        }
    }

    /** Use the same palette as the home cards and selected subscription tab. */
    fun refreshTheme() {
        primary = color(androidx.appcompat.R.attr.colorPrimary)
        textColor = color(com.google.android.material.R.attr.colorOnSurface)
        background = shape(context.getColorAttr("colorCard"), 28)
        indicator.background = shape(primary, 23)
        exit.setTextColor(textColor)
        labels.forEach { label ->
            label.setTextColor(if (label.isSelected) color(com.google.android.material.R.attr.colorOnPrimary) else textColor)
        }
    }

    fun render(value: MikuRouting.State) {
        state = value
        exit.text = value.exit ?: context.getString(R.string.mihomo_select_exit)
        exit.contentDescription = context.getString(R.string.mihomo_global_exit, exit.text)
        val first = shownMode == null
        if (shownMode != value.mode) transition(value.mode, !first)
    }

    private fun chooseMode(mode: String) {
        if (state.mode == mode) {
            if (mode == "global") showExits()
            return
        }
        if (MikuRouting.impl?.mode(mode) != true) { failure(); return }
        selectionVersion++
        onSelectionChanged?.invoke()
        render(state.copy(mode = mode))
    }

    private fun targetX(mode: String): Float {
        val position = modes.indexOf(mode).coerceAtLeast(0)
        val visual = if (layoutDirection == LAYOUT_DIRECTION_RTL) 2 - position else position
        return visual * track.width / 3f
    }

    private fun cancelAnimation() { generation++; animation?.cancel(); animation = null }

    private fun transition(mode: String, animate: Boolean) {
        cancelAnimation()
        shownMode = mode
        labels.forEachIndexed { i, label ->
            label.isSelected = modes[i] == mode
            label.setTypeface(null, if (label.isSelected) Typeface.BOLD else Typeface.NORMAL)
            label.setTextColor(if (label.isSelected) color(com.google.android.material.R.attr.colorOnPrimary) else textColor)
            androidx.core.view.ViewCompat.setStateDescription(label, if (label.isSelected) context.getString(R.string.mihomo_mode_selected) else null)
        }
        if (!animate || track.width == 0) {
            indicator.translationX = targetX(mode)
            setExpanded(mode == "global", false)
            return
        }
        val token = generation
        animation = ValueAnimator.ofFloat(indicator.translationX, targetX(mode)).apply {
            duration = 230
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { indicator.translationX = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (token == generation) setExpanded(mode == "global", true)
                }
            })
            start()
        }
    }

    private fun setExpanded(expanded: Boolean, animate: Boolean) {
        // N13: the expanded exit row is the panel's tap target; 28dp fell
        // short of the 48dp minimum touch-target size.
        val expandedHeight = dp(48)
        val end = if (expanded) expandedHeight else 0
        val start = exit.layoutParams.height
        exit.isEnabled = expanded
        if (expanded || start > 0) exit.visibility = VISIBLE
        fun frame(height: Int) {
            exit.layoutParams = exit.layoutParams.apply { this.height = height }
            exit.alpha = (height.toFloat() / expandedHeight).coerceIn(0f, 1f)
        }
        if (!animate || start == end) {
            frame(end); exit.visibility = if (expanded) VISIBLE else GONE
            return
        }
        val token = generation
        animation = ValueAnimator.ofInt(start, end).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { frame(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (token == generation && !expanded) exit.visibility = GONE
                }
            })
            start()
        }
    }

    private fun showExits() {
        val current = MikuRouting.impl?.state() ?: return
        render(current)
        if (current.options.isEmpty()) { failure(); return }
        picker?.dismiss()
        // Resolve from the host page: the dialog overlay can supply different
        // default list/checkmark colours, especially for custom/dynamic themes.
        val host = context.getActivity() ?: context
        val foreground = host.getColorAttr(com.google.android.material.R.attr.colorOnSurface)
        val dialogBackground = com.google.android.material.shape.MaterialShapeDrawable(
            com.google.android.material.shape.ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(28).toFloat()).build(),
        ).apply { fillColor = ColorStateList.valueOf(host.getColorAttr("colorCard")) }
        val panel = ExitPickerPanel(
            host,
            current,
            ExitRegion.measuredRegions(),
        ) { option ->
            if (MikuRouting.impl?.exit(option.name) == true) {
                selectionVersion++
                onSelectionChanged?.invoke()
                MikuRouting.impl?.state()?.let(::render)
                picker?.dismiss()
            } else failure()
        }
        picker = MaterialAlertDialogBuilder(host)
            .setBackground(dialogBackground)
            .setBackgroundInsetTop(0)
            .setBackgroundInsetBottom(0)
            .setTitle(R.string.mihomo_choose_exit)
            .setView(panel)
            .setNegativeButton(android.R.string.cancel, null).showBlur().also { dialog ->
                // Measure the complete dialog with a height ceiling. A fixed
                // window height leaves empty space below short lists because
                // AlertDialog's panels wrap their content independently.
                val bars = androidx.core.view.ViewCompat.getRootWindowInsets(this)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                val usableHeight = resources.displayMetrics.heightPixels -
                    (bars?.top ?: 0) - (bars?.bottom ?: 0)
                dialog.findViewById<View>(androidx.appcompat.R.id.parentPanel)?.let { panel ->
                    val parent = panel.parent as ViewGroup
                    val index = parent.indexOfChild(panel)
                    val params = panel.layoutParams
                    val maxHeight = (usableHeight * 0.8f).toInt()
                    val boundedContent = object : FrameLayout(host) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                                maxHeight
                            } else minOf(maxHeight, MeasureSpec.getSize(heightMeasureSpec))
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
                        }
                    }
                    parent.removeView(panel)
                    boundedContent.addView(panel, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    parent.addView(boundedContent, index, params)
                }
                dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(foreground)
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                    setTextColor(host.getColorAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    backgroundTintList = ColorStateList.valueOf(host.getColorAttr(com.google.android.material.R.attr.colorSecondaryContainer))
                }
            }
    }

    private fun failure() = Toast.makeText(context, R.string.mihomo_mode_failed, Toast.LENGTH_SHORT).show()
    override fun onDetachedFromWindow() {
        cancelAnimation(); picker?.dismiss(); picker = null
        super.onDetachedFromWindow()
    }
}
