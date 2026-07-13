package top.uwu.mikubox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import top.uwu.mikubox.R
import top.uwu.mikubox.ui.MainActivity

/**
 * The MikuBox VPN service.
 *
 * At this milestone it proves the full VpnService lifecycle: consent → TUN
 * establishment → foreground notification → stop. It does **not** yet route
 * traffic — the Mihomo core that consumes the TUN file descriptor lands in a
 * later unit. To avoid disrupting connectivity while "on", the TUN is
 * established with an address but **no catch-all route**, so nothing is
 * captured until the core is wired in.
 */
class MikuVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        // System or another VPN app revoked our permission.
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        if (tun != null) return
        startForegroundNotification()
        tun = establishTun() ?: return
        running = true
    }

    private fun stopVpn() {
        running = false
        runCatching { tun?.close() }
        tun = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun establishTun(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(TUN_MTU)
            .addAddress(PRIVATE_VLAN4_CLIENT, PRIVATE_VLAN4_PREFIX)
            // No default route yet: the core (later unit) owns routing. Allowing
            // bypass keeps existing connectivity intact during this milestone.
            .allowBypass()
        builder.setConfigureIntent(configurePendingIntent())
        return try {
            builder.establish()
        } catch (t: Throwable) {
            stopVpn()
            null
        }
    }

    // region notification

    private fun startForegroundNotification() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MikuVpnService::class.java).setAction(ACTION_STOP),
            pendingFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.vpn_notification_running))
            .setContentIntent(configurePendingIntent())
            .addAction(0, getString(R.string.vpn_action_stop), stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun configurePendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        pendingFlags(),
    )

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    // endregion

    companion object {
        const val ACTION_STOP = "top.uwu.mikubox.action.STOP_VPN"

        private const val CHANNEL_ID = "miku_vpn_status"
        private const val NOTIFICATION_ID = 1

        private const val TUN_MTU = 9000
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_PREFIX = 30

        /** Coarse running flag for the UI/controller to reflect state. */
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, MikuVpnService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MikuVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
