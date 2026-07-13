package top.uwu.mikubox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import top.uwu.mikubox.R
import top.uwu.mikubox.core.MihomoConfigStore
import top.uwu.mikubox.core.MihomoCore

/** Starts Mihomo's local mixed proxy listener without creating a VPN interface. */
class MikuProxyService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProxy()
            return START_NOT_STICKY
        }
        if (!running) {
            VpnController.disconnect(this)
            ensureChannel()
            startForeground(NOTIFICATION_ID, notification())
            val result = MihomoCore.start(this, MihomoConfigStore.activeConfig(this), MihomoCore.NO_TUN)
            if (result.isFailure) {
                stopProxy()
                return START_NOT_STICKY
            }
            running = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopProxy() {
        if (running) MihomoCore.stop()
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, "miku_vpn_status")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.local_proxy_notification_running))
        .setOngoing(true)
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel("miku_vpn_status") == null) {
            manager.createNotificationChannel(
                NotificationChannel("miku_vpn_status", getString(R.string.vpn_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val ACTION_STOP = "top.uwu.mikubox.action.STOP_PROXY"
        private const val NOTIFICATION_ID = 3

        @Volatile
        var running = false
            private set

        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, MikuProxyService::class.java),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, MikuProxyService::class.java).setAction(ACTION_STOP))
        }
    }
}
