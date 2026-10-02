package com.miku.ray.handler

import com.miku.ray.remixicon.R as RemixR
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.extension.toSpeedString
import com.miku.ray.ui.main.MainActivity
import com.miku.ray.util.AppNameHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import com.miku.ray.extension.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

object NotificationManager : TrafficController.Listener {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    private var mBuilder: NotificationCompat.Builder? = null
    private var timerNotificationJob: Job? = null
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mNotificationManager: NotificationManager? = null

    @Volatile private var lastSpeedText: String = ""
    @Volatile private var lastProxyTraffic: Long = 0L
    @Volatile private var lastDirectTraffic: Long = 0L
    @Volatile private var lastDataUsageText: String = ""
    @Volatile private var sessionUplink: Long = 0L
    @Volatile private var sessionDownlink: Long = 0L

    fun startSpeedNotification() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (CoreServiceManager.isRunning() == false) return
        if (timerNotificationJob?.isActive == true) return

        timerNotificationJob?.cancel()
        TrafficController.setListener(this)

        timerNotificationJob = notificationScope.launch {
            while (isActive) {
                updateTimerNotification()
                delay(1000L)
            }
        }
    }

    override fun onTraffic(
        proxyUplink: Long,
        proxyDownlink: Long,
        directUplink: Long,
        directDownlink: Long,
        intervalMs: Long,
    ) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return

        val sinceLastQueryInSeconds = intervalMs / 1000.0
        val proxyTotal = proxyUplink + proxyDownlink
        val directTotal = directUplink + directDownlink

        val hideDirectTraffic = MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_DIRECT_TRAFFIC_NOTIFICATION) == true

        val text = StringBuilder()
        appendSpeedString(
            text, AppConfig.TAG_PROXY,
            proxyUplink / sinceLastQueryInSeconds,
            proxyDownlink / sinceLastQueryInSeconds
        )
        if (!hideDirectTraffic) {
            appendSpeedString(
                text, AppConfig.TAG_DIRECT,
                directUplink / sinceLastQueryInSeconds,
                directDownlink / sinceLastQueryInSeconds
            )
        }
        lastSpeedText = text.toString()
        lastProxyTraffic = proxyTotal
        lastDirectTraffic = if (hideDirectTraffic) 0L else directTotal

        sessionUplink += proxyUplink + directUplink
        sessionDownlink += proxyDownlink + directDownlink
        val service = getService()
        lastDataUsageText = service?.getString(
            R.string.notification_data_usage,
            formatBytes(sessionUplink),
            formatBytes(sessionDownlink)
        ) ?: ""
    }

    fun showNotification(currentConfig: ProfileItem?, isNewConnection: Boolean = true) {
        val service = getService() ?: return

        val existingStartTime = MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
        if (isNewConnection || existingStartTime == 0L) {
            MmkvManager.encodeSettings(
                AppConfig.PREF_VPN_CONNECT_START_TIME,
                System.currentTimeMillis(),
            )
            lastSpeedText = ""
            lastProxyTraffic = 0L
            lastDirectTraffic = 0L
            lastDataUsageText = ""
            sessionUplink = 0L
            sessionDownlink = 0L
        }

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        mBuilder = NotificationCompat.Builder(service, channelId)
        .setSmallIcon(R.drawable.ic_stat_name)
        .setContentTitle(buildNotificationTitle(service, currentConfig))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentPendingIntent)
        .addAction(
            RemixR.drawable.rmx_delete_bin_line,
            service.getString(R.string.notification_action_stop_v2ray),
            stopV2RayPendingIntent
        )
        .addAction(
            RemixR.drawable.rmx_history_line,
            service.getString(R.string.title_service_restart),
            restartV2RayPendingIntent
        )

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null, isNewConnection = false) else service.startForeground(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
        TrafficController.setListener(null)
        timerNotificationJob?.cancel()
        timerNotificationJob = null
        mNotificationManager = null
    }

    fun stopSpeedNotification() {
        TrafficController.setListener(null)
        timerNotificationJob?.cancel()
        timerNotificationJob = null
        updateNotification("", 0, 0)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME

        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        chan.lightColor = Color.DKGRAY

        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.take(min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append(":  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    private fun updateTimerNotification() {
        val startTime = MmkvManager.decodeSettingsLong(AppConfig.PREF_VPN_CONNECT_START_TIME, 0L)
        if (startTime == 0L) return
        val service = getService() ?: return
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(0L)
        val h = elapsed / 3600
        val m = (elapsed % 3600) / 60
        val s = elapsed % 60
        val timeStr = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        val timerLine = service.getString(R.string.notification_connect_time, timeStr)
        val combined = buildString {
            if (lastSpeedText.isNotEmpty()) append(lastSpeedText)
            if (lastDataUsageText.isNotEmpty()) append("$lastDataUsageText\n")
            append(timerLine)
        }
        updateNotification(combined, lastProxyTraffic, lastDirectTraffic)
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var i = 0
        while (size >= 1024 && i < units.size - 1) { size /= 1024; i++ }
        return String.format(java.util.Locale.getDefault(), "%.2f %s", size, units[i])
    }

    private fun buildNotificationTitle(service: Service, currentConfig: ProfileItem?): String {
        val baseTitle = currentConfig?.remarks ?: AppNameHelper.getDisplayName(service)
        val showGroupName = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_GROUP_NAME_NOTIFICATION) == true
        if (!showGroupName) return baseTitle

        val subscriptionId = currentConfig?.subscriptionId
        if (subscriptionId.isNullOrEmpty()) return baseTitle

        val groupName = MmkvManager.decodeSubscription(subscriptionId)?.remarks
        if (groupName.isNullOrEmpty()) return baseTitle

        return "$baseTitle ($groupName)"
    }

    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.getService()
    }
}
