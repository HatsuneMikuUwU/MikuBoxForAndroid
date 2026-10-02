package com.miku.ray.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.ui.main.MainActivity
import com.miku.ray.util.MyContextWrapper
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.getColorAttr
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TrafficDetailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppConfig.BROADCAST_ACTION_TRAFFIC_WIDGET_REFRESH -> updateAll(context)
            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                val key = intent.getIntExtra("key", 0)
                if (key == AppConfig.MSG_TRAFFIC_UPDATED || key in SERVICE_STATE_MESSAGES) {
                    updateAll(context)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_traffic_detail)
        val locale = SettingsManager.getLocale()
        val localizedContext = MyContextWrapper.wrap(context, locale)
        val themedContext = buildThemedContext(localizedContext)
        val density = context.resources.displayMetrics.density

        val surfaceHighColor = themedContext.getColorAttr("colorSurfaceContainerHigh")
        val surfaceHighestColor = themedContext.getColorAttr("colorSurfaceContainerHighest")
        val tertiaryContainerColor = themedContext.getColorAttr("colorTertiaryContainer")
        val primaryContainerColor = themedContext.getColorAttr("colorPrimaryContainer")
        val onsurfaceHighColor = themedContext.getColorAttr("colorOnSurface")
        val onSurfaceVariantColor = themedContext.getColorAttr("colorOnSurfaceVariant")
        val onTertiaryContainerColor = themedContext.getColorAttr("colorOnTertiaryContainer")
        val onPrimaryContainerColor = themedContext.getColorAttr("colorOnPrimaryContainer")
        val tertiaryColor = themedContext.getColorAttr("colorTertiary")
        val primaryColor = themedContext.getColorAttr("colorPrimary")

        val (widthPx, heightPx) = widgetSizePx(context, appWidgetManager, appWidgetId, density)
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_card_bg,
            roundedRectBitmap(widthPx, heightPx, dp(28f, density), surfaceHighColor),
        )

        val (uploadBytes, downloadBytes) = MmkvManager.getTotalTrafficDetail() ?: (0L to 0L)
        fun formatTraffic(bytes: Long): String = MmkvManager.formatTrafficBytesPublic(bytes)
        remoteViews.setTextViewText(
            R.id.traffic_widget_combined_value,
            formatTraffic(uploadBytes + downloadBytes),
        )
        remoteViews.setTextViewText(R.id.traffic_widget_upload_value, formatTraffic(uploadBytes))
        remoteViews.setTextViewText(R.id.traffic_widget_download_value, formatTraffic(downloadBytes))
        localizedContext.apply {
            remoteViews.setTextViewText(
                R.id.traffic_widget_combined_label,
                getString(R.string.label_total_traffic_combined),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_upload_label,
                getString(R.string.label_total_traffic_upload),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_download_label,
                getString(R.string.label_total_traffic_download),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_today_label,
                getString(R.string.label_total_traffic_today),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_month_label,
                getString(R.string.label_total_traffic_month),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_history_title,
                getString(R.string.title_total_traffic_history),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_upload_legend,
                getString(R.string.label_total_traffic_up),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_download_legend,
                getString(R.string.label_total_traffic_down),
            )
            remoteViews.setTextViewText(
                R.id.traffic_widget_history_empty,
                getString(R.string.label_total_traffic_no_history),
            )
        }

        val (todayUp, todayDown) = MmkvManager.getTodayTrafficDetail()
        remoteViews.setTextViewText(
            R.id.traffic_widget_today_value,
            formatTraffic(todayUp + todayDown),
        )
        val (monthUp, monthDown) = MmkvManager.getCurrentMonthTrafficDetail()
        remoteViews.setTextViewText(
            R.id.traffic_widget_month_value,
            formatTraffic(monthUp + monthDown),
        )

        val innerWidth = (widthPx - dp(28f, density)).coerceAtLeast(1)
        val cardWidth = ((innerWidth - dp(6f, density)) / 2).coerceAtLeast(1)
        val metricCardHeight = dp(78f, density)
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_upload_bg,
            roundedRectBitmap(cardWidth, metricCardHeight, dp(18f, density), tertiaryContainerColor),
        )
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_download_bg,
            roundedRectBitmap(cardWidth, metricCardHeight, dp(18f, density), primaryContainerColor),
        )
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_today_bg,
            roundedRectBitmap(cardWidth, metricCardHeight, dp(18f, density), surfaceHighestColor),
        )
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_month_bg,
            roundedRectBitmap(cardWidth, metricCardHeight, dp(18f, density), surfaceHighestColor),
        )

        remoteViews.setInt(R.id.traffic_widget_icon, "setImageResource", RemixR.drawable.rmx_business_bar_chart_line)
        remoteViews.setInt(R.id.traffic_widget_icon, "setColorFilter", onsurfaceHighColor)
        remoteViews.setTextColor(R.id.traffic_widget_combined_value, primaryColor)
        remoteViews.setTextColor(R.id.traffic_widget_combined_label, onSurfaceVariantColor)

        remoteViews.setInt(R.id.traffic_widget_upload_icon, "setImageResource", RemixR.drawable.rmx_upload_line)
        remoteViews.setInt(R.id.traffic_widget_upload_icon, "setColorFilter", onTertiaryContainerColor)
        remoteViews.setTextColor(R.id.traffic_widget_upload_value, onTertiaryContainerColor)
        remoteViews.setTextColor(R.id.traffic_widget_upload_label, onTertiaryContainerColor)

        remoteViews.setInt(R.id.traffic_widget_download_icon, "setImageResource", RemixR.drawable.rmx_download_line)
        remoteViews.setInt(R.id.traffic_widget_download_icon, "setColorFilter", onPrimaryContainerColor)
        remoteViews.setTextColor(R.id.traffic_widget_download_value, onPrimaryContainerColor)
        remoteViews.setTextColor(R.id.traffic_widget_download_label, onPrimaryContainerColor)

        listOf(
            R.id.traffic_widget_today_value,
            R.id.traffic_widget_month_value,
            R.id.traffic_widget_history_title,
        ).forEach { remoteViews.setTextColor(it, onsurfaceHighColor) }
        listOf(
            R.id.traffic_widget_today_label,
            R.id.traffic_widget_month_label,
            R.id.traffic_widget_upload_legend,
            R.id.traffic_widget_download_legend,
        ).forEach { remoteViews.setTextColor(it, onSurfaceVariantColor) }
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_upload_dot,
            dotBitmap(dp(8f, density), tertiaryColor),
        )
        remoteViews.setImageViewBitmap(
            R.id.traffic_widget_download_dot,
            dotBitmap(dp(8f, density), primaryColor),
        )

        val history = MmkvManager.getDailyTrafficHistory(HISTORY_DAYS)
        val hasHistory = history.any { (_, up, down) -> up + down > 0L }
        remoteViews.setViewVisibility(R.id.traffic_widget_chart, if (hasHistory) View.VISIBLE else View.GONE)
        remoteViews.setViewVisibility(
            R.id.traffic_widget_history_empty,
            if (hasHistory) View.GONE else View.VISIBLE,
        )
        if (hasHistory) {
            remoteViews.setImageViewBitmap(
                R.id.traffic_widget_chart,
                trafficChartBitmap(
                    width = innerWidth,
                    height = dp(130f, density),
                    entries = history,
                    uploadColor = tertiaryColor,
                    downloadColor = primaryColor,
                    trackColor = surfaceHighestColor,
                    labelColor = onSurfaceVariantColor,
                    locale = locale,
                    density = density,
                ),
            )
        }

        val openDialogIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppConfig.EXTRA_SHOW_TOTAL_TRAFFIC_DETAIL, true)
        }
        val openDialogPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            openDialogIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        remoteViews.setOnClickPendingIntent(R.id.traffic_widget_root, openDialogPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TrafficDetailWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private const val HISTORY_DAYS = 7
        private val SERVICE_STATE_MESSAGES = setOf(
            AppConfig.MSG_STATE_RUNNING,
            AppConfig.MSG_STATE_NOT_RUNNING,
            AppConfig.MSG_STATE_START_SUCCESS,
            AppConfig.MSG_STATE_START_FAILURE,
            AppConfig.MSG_STATE_STOP_SUCCESS,
        )

        private fun widgetSizePx(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            density: Float,
        ): Pair<Int, Int> {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
            return max((widthDp * density).roundToInt(), dp(DEFAULT_WIDTH_DP.toFloat(), density)) to
            max((heightDp * density).roundToInt(), dp(DEFAULT_HEIGHT_DP.toFloat(), density))
        }

        private fun buildThemedContext(context: Context): Context {
            val key = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
            var themed: Context = ContextThemeWrapper(context, ThemeManager.getThemeStyleRes(key))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isDynamic = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
                val useCustom = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
                val customColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
                val isDynamicBanner = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR_BANNER, false)
                val bannerColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_BANNER_COLOR, 0)
                themed = when {
                    isDynamicBanner && bannerColor != 0 -> DynamicColors.wrapContextIfAvailable(
                        themed,
                        DynamicColorsOptions.Builder().setContentBasedSource(bannerColor).build(),
                    )
                    isDynamic -> DynamicColors.wrapContextIfAvailable(themed)
                    useCustom && customColor != 0 -> DynamicColors.wrapContextIfAvailable(
                        themed,
                        DynamicColorsOptions.Builder().setContentBasedSource(customColor).build(),
                    )
                    else -> themed
                }
            }
            val isTrueBlack = isDarkMode(context) &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
            if (isTrueBlack) themed.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack, true)
            return themed
        }

        private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

        private fun dp(value: Float, density: Float): Int = (value * density).roundToInt().coerceAtLeast(1)

        private fun roundedRectBitmap(width: Int, height: Int, radius: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(max(width, 1), max(height, 1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val cappedRadius = radius.toFloat().coerceAtMost(min(bitmap.width, bitmap.height) / 2f)
            canvas.drawRoundRect(
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                cappedRadius,
                cappedRadius,
                paint,
            )
            return bitmap
        }

        private fun dotBitmap(size: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
            )
            return bitmap
        }

        private fun trafficChartBitmap(
            width: Int,
            height: Int,
            entries: List<Triple<String, Long, Long>>,
            uploadColor: Int,
            downloadColor: Int,
            trackColor: Int,
            labelColor: Int,
            locale: Locale,
            density: Float,
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(max(width, 1), max(height, 1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor
                textAlign = Paint.Align.CENTER
                textSize = 11f * density
            }
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
            val labelMetrics = labelPaint.fontMetrics
            val chartBottom = height - (labelMetrics.bottom - labelMetrics.top) - 6f * density
            val chartHeight = (chartBottom - 0f).coerceAtLeast(1f)
            val spacing = 6f * density
            val minBarHeight = 3f * density
            val maxTotal = entries.maxOf { it.second + it.third }.coerceAtLeast(1L)
            val barWidth = ((width - spacing * (entries.size + 1)) / entries.size).coerceAtLeast(1f)
            var x = spacing
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val weekdayFormat = SimpleDateFormat("EEE", locale)
            val segmentPath = Path()

            entries.forEach { entry ->
                val total = entry.second + entry.third
                val barHeight = if (total <= 0L) minBarHeight else {
                    (chartHeight * total / maxTotal.toFloat()).coerceAtLeast(minBarHeight)
                }
                val trackRect = RectF(x, 0f, x + barWidth, chartBottom)
                canvas.drawRoundRect(trackRect, 6f * density, 6f * density, trackPaint)
                if (total > 0L) {
                    val barTop = chartBottom - barHeight
                    val uploadHeight = barHeight * entry.second / total.toFloat()
                    val downloadHeight = barHeight - uploadHeight
                    if (downloadHeight > 0f) {
                        barPaint.color = downloadColor
                        drawSegment(
                            canvas,
                            segmentPath,
                            RectF(x, chartBottom - downloadHeight, x + barWidth, chartBottom),
                            0f,
                            6f * density,
                            barPaint,
                        )
                    }
                    if (uploadHeight > 0f) {
                        barPaint.color = uploadColor
                        drawSegment(
                            canvas,
                            segmentPath,
                            RectF(x, barTop, x + barWidth, chartBottom - downloadHeight + 1f),
                            6f * density,
                            if (downloadHeight > 0f) 0f else 6f * density,
                            barPaint,
                        )
                    }
                }
                val label = runCatching {
                    dateFormat.parse(entry.first)?.let {
                        weekdayFormat.format(it).take(2).replaceFirstChar { char -> char.uppercase() }
                    }.orEmpty()
                }.getOrDefault("")
                canvas.drawText(label, x + barWidth / 2f, height - labelMetrics.bottom, labelPaint)
                x += barWidth + spacing
            }
            return bitmap
        }

        private fun drawSegment(
            canvas: Canvas,
            path: Path,
            rect: RectF,
            topRadius: Float,
            bottomRadius: Float,
            paint: Paint,
        ) {
            path.reset()
            path.addRoundRect(
                rect,
                floatArrayOf(
                    topRadius, topRadius, topRadius, topRadius,
                    bottomRadius, bottomRadius, bottomRadius, bottomRadius,
                ),
                Path.Direction.CW,
            )
            canvas.drawPath(path, paint)
        }

        private const val DEFAULT_WIDTH_DP = 240
        private const val DEFAULT_HEIGHT_DP = 360
    }
}
