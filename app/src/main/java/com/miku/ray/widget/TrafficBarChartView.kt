package com.miku.ray.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.miku.ray.util.getColorAttr
import java.text.SimpleDateFormat
import java.util.Locale

class TrafficBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class DayEntry(val dateKey: String, val uplink: Long, val downlink: Long)

    private var entries: List<DayEntry> = emptyList()

    private val uploadColor = context.getColorAttr("colorTertiary")
    private val downloadColor = context.getColorAttr("colorPrimary")
    private val trackColor = context.getColorAttr("colorSurfaceContainerHighest")
    private val labelColor = context.getColorAttr("colorOnSurfaceVariant")

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        textSize = spToPx(11f)
    }
    private val segmentPath = Path()

    var trackCornerRadius = dpToPx(6f)
    var uploadTopRadius = dpToPx(6f)
    var uploadBottomRadius = 0f
    var downloadTopRadius = 0f
    var downloadBottomRadius = dpToPx(6f)

    private val barSpacing = dpToPx(6f)
    private val labelGap = dpToPx(6f)
    private val minBarHeight = dpToPx(3f)

    private val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateParseFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun setEntries(newEntries: List<DayEntry>) {
        entries = newEntries
        requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val days = entries
        if (days.isEmpty()) return

        val labelHeight = labelPaint.fontMetrics.let { it.bottom - it.top }
        val chartBottom = height - labelHeight - labelGap
        val chartTop = paddingTop.toFloat()
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val maxTotal = days.maxOf { it.uplink + it.downlink }.coerceAtLeast(1L)

        val totalSpacing = barSpacing * (days.size + 1)
        val barWidth = ((width - paddingLeft - paddingRight - totalSpacing) / days.size).coerceAtLeast(1f)

        var x = paddingLeft + barSpacing
        days.forEach { entry ->
            val total = entry.uplink + entry.downlink
            val barHeight = if (total <= 0L) minBarHeight else {
                (chartHeight * total / maxTotal.toFloat()).coerceAtLeast(minBarHeight)
            }

            val trackRect = RectF(x, chartTop, x + barWidth, chartBottom)
            canvas.drawRoundRect(trackRect, trackCornerRadius, trackCornerRadius, trackPaint)

            if (total > 0L) {
                val barTop = chartBottom - barHeight
                val uploadHeight = if (total == 0L) 0f else barHeight * entry.uplink / total.toFloat()
                val downloadHeight = barHeight - uploadHeight
                val downloadTop = chartBottom - downloadHeight

                if (downloadHeight > 0f) {
                    barPaint.color = downloadColor
                    val topR = if (uploadHeight > 0f) downloadTopRadius else downloadBottomRadius
                    drawSegment(
                        canvas,
                        RectF(x, downloadTop, x + barWidth, chartBottom),
                        topRadius = topR,
                        bottomRadius = downloadBottomRadius,
                    )
                }

                if (uploadHeight > 0f) {
                    barPaint.color = uploadColor
                    val bottomOverlap = if (downloadHeight > 0f) 1f else 0f
                    val bottomR = if (downloadHeight > 0f) uploadBottomRadius else uploadTopRadius
                    drawSegment(
                        canvas,
                        RectF(x, barTop, x + barWidth, downloadTop + bottomOverlap),
                        topRadius = uploadTopRadius,
                        bottomRadius = bottomR,
                    )
                }
            }

            val label = runCatching {
                val date = dateParseFormat.parse(entry.dateKey)
                if (date != null) {
                    weekdayFormat.format(date).take(2).replaceFirstChar { it.uppercase() }
                } else ""
            }.getOrDefault("")
            canvas.drawText(label, x + barWidth / 2f, height.toFloat() - labelPaint.fontMetrics.bottom, labelPaint)

            x += barWidth + barSpacing
        }
    }

    private fun drawSegment(canvas: Canvas, rect: RectF, topRadius: Float, bottomRadius: Float) {
        segmentPath.reset()
        segmentPath.addRoundRect(
            rect,
            floatArrayOf(
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius,
            ),
            Path.Direction.CW,
        )
        canvas.drawPath(segmentPath, barPaint)
    }

    private fun dpToPx(dp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}
