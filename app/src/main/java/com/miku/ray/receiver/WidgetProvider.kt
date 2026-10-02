package com.miku.ray.receiver

import com.miku.ray.remixicon.R as RemixR
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.LauncherManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.main.MainActivity
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.getColorAttr
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, CoreServiceManager.isRunning())
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, CoreServiceManager.isRunning())
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, isRunning: Boolean) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        val density = context.resources.displayMetrics.density

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

        val toggleIntent = Intent(context, WidgetProvider::class.java).apply {
            action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, R.id.widget_action_button, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_action_button, togglePendingIntent)

        remoteViews.setViewVisibility(R.id.widget_restart_button, if (isRunning) View.VISIBLE else View.GONE)
        if (isRunning) {
            val restartIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
                `package` = AppConfig.ANG_PACKAGE
                putExtra("key", AppConfig.MSG_STATE_RESTART)
            }
            val restartPendingIntent = PendingIntent.getBroadcast(
                context, R.id.widget_restart_button, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_restart_button, restartPendingIntent)
        }

        val selectedGuid = MmkvManager.getSelectServer()
        val serverName = selectedGuid?.let { MmkvManager.decodeServerConfig(it)?.remarks }
        remoteViews.setTextViewText(
            R.id.widget_server_name,
            if (serverName.isNullOrBlank()) context.getString(R.string.widget_no_server_selected) else serverName
        )
        remoteViews.setTextViewText(
            R.id.widget_status,
            context.getString(if (isRunning) R.string.widget_status_connected else R.string.widget_status_disconnected)
        )

        val themedContext = buildThemedContext(context)
        val cardColor = themedContext.getColorAttr("colorSurfaceContainerHigh")
        val nameColor = themedContext.getColorAttr("colorOnSurface")
        val statusColor = if (isRunning) {
            themedContext.getColorAttr("colorPrimary")
        } else {
            themedContext.getColorAttr("colorOnSurfaceVariant")
        }
        val actionBgColor: Int
        val actionIconColor: Int
        val actionIconRes: Int
        if (isRunning) {
            actionBgColor = themedContext.getColorAttr("colorTertiaryContainer")
            actionIconColor = themedContext.getColorAttr("colorOnTertiaryContainer")
            actionIconRes = RemixR.drawable.rmx_media_stop_line
        } else {
            actionBgColor = themedContext.getColorAttr("colorPrimaryContainer")
            actionIconColor = themedContext.getColorAttr("colorOnPrimaryContainer")
            actionIconRes = RemixR.drawable.rmx_media_play_line
        }

        remoteViews.setTextColor(R.id.widget_server_name, nameColor)
        remoteViews.setTextColor(R.id.widget_status, statusColor)
        remoteViews.setInt(R.id.widget_action_icon, "setImageResource", actionIconRes)
        remoteViews.setInt(R.id.widget_action_icon, "setColorFilter", actionIconColor)

        val restartBgColor = themedContext.getColorAttr("colorSecondaryContainer")
        val restartIconColor = themedContext.getColorAttr("colorOnSecondaryContainer")

        remoteViews.setInt(R.id.widget_restart_icon, "setImageResource", RemixR.drawable.rmx_history_line)
        remoteViews.setInt(R.id.widget_restart_icon, "setColorFilter", restartIconColor)

        val (widthPx, heightPx) = widgetSizePx(context, appWidgetManager, appWidgetId, density)
        remoteViews.setImageViewBitmap(R.id.widget_card_bg, roundedRectBitmap(widthPx, heightPx, 20f * density, cardColor))

        val iconSizePx = (50 * density).toInt()
        val bannerSource = decodeSampledFromResource(context, R.drawable.uwu_banner_author, iconSizePx)
        val bannerSquare = centerCropSquare(bannerSource, iconSizePx)
        remoteViews.setImageViewBitmap(R.id.widget_icon, roundedClip(bannerSquare, 12f * density))

        val actionWidthPx = (72 * density).toInt()
        val restartWidthPx = (48 * density).toInt()
        val buttonHeightPx = (50 * density).toInt()

        remoteViews.setImageViewBitmap(
            R.id.widget_action_bg,
            roundedRectBitmap(actionWidthPx, buttonHeightPx, 12f * density, actionBgColor)
        )

        if (isRunning) {
            remoteViews.setImageViewBitmap(
                R.id.widget_restart_bg,
                roundedRectBitmap(restartWidthPx, buttonHeightPx, 12f * density, restartBgColor)
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppConfig.BROADCAST_ACTION_WIDGET_CLICK == intent.action) {
            if (CoreServiceManager.isRunning()) {
                LauncherManager.stopService(context)
            } else {
                LauncherManager.startServiceFromToggle(context)
            }
        } else if (AppConfig.BROADCAST_ACTION_ACTIVITY == intent.action) {
            AppWidgetManager.getInstance(context)?.let { manager ->
                val isRunning = when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> true
                    AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS -> false
                    else -> return
                }
                for (appWidgetId in manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))) {
                    updateWidget(context, manager, appWidgetId, isRunning)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_WIDTH_DP = 240
        private const val DEFAULT_HEIGHT_DP = 60

        private fun widgetSizePx(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            density: Float,
        ): Pair<Int, Int> {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
            val widthPx = max((widthDp * density).roundToInt(), (DEFAULT_WIDTH_DP * density).roundToInt())
            val heightPx = max((heightDp * density).roundToInt(), (72 * density).roundToInt())
            return widthPx to heightPx
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

                when {
                    isDynamicBanner && bannerColor != 0 -> {
                        themed = DynamicColors.wrapContextIfAvailable(
                            themed,
                            DynamicColorsOptions.Builder().setContentBasedSource(bannerColor).build()
                        )
                    }

                    isDynamic -> {
                        themed = DynamicColors.wrapContextIfAvailable(themed)
                    }

                    useCustom && customColor != 0 -> {
                        themed = DynamicColors.wrapContextIfAvailable(
                            themed,
                            DynamicColorsOptions.Builder().setContentBasedSource(customColor).build()
                        )
                    }
                }
            }

            val isTrueBlack = isDarkMode(context) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
            if (isTrueBlack) {
                themed.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack, true)
            }
            return themed
        }

        private fun isDarkMode(context: Context): Boolean {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return uiMode == Configuration.UI_MODE_NIGHT_YES
        }

        private fun roundedRectBitmap(widthPx: Int, heightPx: Int, radiusPx: Float, @ColorInt color: Int): Bitmap {
            val w = max(widthPx, 1)
            val h = max(heightPx, 1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val cappedRadius = radiusPx.coerceAtMost(min(w, h) / 2f)
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), cappedRadius, cappedRadius, paint)
            return bitmap
        }

        private fun decodeSampledFromResource(context: Context, @DrawableRes resId: Int, reqSizePx: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqSizePx, reqSizePx)
            }
            return BitmapFactory.decodeResource(context.resources, resId, options)
        }

        private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
            var inSampleSize = 1
            if (rawHeight > reqHeight || rawWidth > reqWidth) {
                var halfHeight = rawHeight / 2
                var halfWidth = rawWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val scale = max(size / src.width.toFloat(), size / src.height.toFloat())
            val scaledW = src.width * scale
            val scaledH = src.height * scale
            val dx = (size - scaledW) / 2f
            val dy = (size - scaledH) / 2f
            canvas.drawBitmap(src, null, RectF(dx, dy, dx + scaledW, dy + scaledH), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return output
        }

        private fun roundedClip(src: Bitmap, radiusPx: Float): Bitmap {
            val size = src.width
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val path = Path().apply {
                addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radiusPx, radiusPx, Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(src, 0f, 0f, null)
            return output
        }
    }
}
