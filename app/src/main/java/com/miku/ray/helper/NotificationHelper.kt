package com.miku.ray.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.miku.ray.R
import com.miku.ray.enums.NotificationChannelType

object NotificationHelper {

    private var cachedNotificationManager: NotificationManager? = null
    private val builderCache = mutableMapOf<Int, NotificationCompat.Builder>()

    fun notify(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ) {
        ensureChannelCreated(channelType, context)
        val notificationManager = getNotificationManager(context)
        val builder = buildNotificationBuilder(channelType, context, title, content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    fun updateNotification(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String
    ) {
        val notificationManager = getNotificationManager(context)

        val builder = builderCache.getOrPut(channelType.notificationId) {
            buildNotificationBuilder(channelType, context, title, content)
        }

        builder.setContentText(content)
        notificationManager.notify(channelType.notificationId, builder.build())
    }

    fun startForeground(
        service: Service,
        channelType: NotificationChannelType,
        title: String,
        content: String,
        action: NotificationCompat.Action? = null
    ) {
        ensureChannelCreated(channelType, service)
        val builder = buildNotificationBuilder(channelType, service, title, content, action)
        builderCache[channelType.notificationId] = builder
        service.startForeground(channelType.notificationId, builder.build())
    }

    fun stopForeground(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    fun cancel(
        channelType: NotificationChannelType,
        context: Context
    ) {
        getNotificationManager(context).cancel(channelType.notificationId)
        builderCache.remove(channelType.notificationId)
    }

    private fun getNotificationManager(context: Context): NotificationManager {
        if (cachedNotificationManager == null) {
            cachedNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return cachedNotificationManager!!
    }

    private fun ensureChannelCreated(channelType: NotificationChannelType, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(channelType.channelId) != null) return

        val channel = NotificationChannel(
            channelType.channelId,
            channelType.channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotificationBuilder(
        channelType: NotificationChannelType,
        context: Context,
        title: String,
        content: String,
        action: NotificationCompat.Action? = null
    ): NotificationCompat.Builder {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelType.channelId
        } else {
            ""
        }

        val displayTitle = title.ifEmpty { com.miku.ray.util.AppNameHelper.getDisplayName(context) }
        return NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_name)
        .setContentTitle(displayTitle)
        .setContentText(content)
        .setOngoing(false)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .apply { action?.let(::addAction) }
    }
}
