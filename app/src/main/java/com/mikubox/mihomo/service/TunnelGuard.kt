package com.mikubox.mihomo.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.LogUtil

/**
 * Brings the tunnel back when the process is killed with it up.
 *
 * START_STICKY asks Android to recreate a killed service; framework-managed
 * sticky foreground-service restarts are exempt from background-start limits.
 * This alarm is a best-effort fallback for devices that delay that restart.
 * Its explicit start is still subject to background restrictions, and Doze may
 * defer the alarm, so it cannot promise a restart deadline or defeat force-stop.
 * The persisted expectation is cleared on manual disconnect and VPN revocation.
 */
object TunnelGuard {

    private const val CHECK_INTERVAL_MS = 30_000L

    fun recoveryPaused(context: Context): Boolean = RecoveryBackoff.failures(context) >= 3

    fun resetFailures(context: Context) = RecoveryBackoff.reset(context)

    fun failed(context: Context) {
        if (!isExpected(context)) return
        RecoveryBackoff.failed(context)
        if (recoveryPaused(context)) {
            cancel(context)
            LogUtil.w(message = "Automatic VPN recovery paused after three failures; reconnect manually")
        } else schedule(context)
    }

    /** True while the tunnel is supposed to be up. */
    fun isExpected(context: Context): Boolean =
        runCatching { MmkvManager.decodeSettingsBool(AppConfig.PREF_TUNNEL_EXPECTED) }
            .getOrDefault(false)

    /**
     * Records whether a tunnel should be running, arming or disarming the
     * check with it. Called when the service reaches connected state and when
     * it tears down, so a deliberate disconnect never resurrects.
     */
    fun expectRunning(context: Context, expected: Boolean) {
        recordExpectation(context, expected)
        resetFailures(context)
        if (expected) schedule(context) else cancel(context)
    }

    /**
     * Arms the guard for a start that has not connected yet, without touching
     * the failure backoff: resetting it here would let every recovery retry
     * clear the count on its way through the service entry, and a bad profile
     * would then retry forever instead of tripping the three-failure breaker
     * (see [recoveryPaused]). The connected checkpoint still calls
     * [expectRunning], whose reset is the "the start succeeded" signal.
     */
    fun expectRunningWhileConnecting(context: Context) {
        recordExpectation(context, true)
        schedule(context)
    }

    private fun recordExpectation(context: Context, expected: Boolean) {
        val recorded = runCatching { MmkvManager.encodeSettings(AppConfig.PREF_TUNNEL_EXPECTED, expected) }
            .getOrElse { failure ->
                LogUtil.w(message = "Could not record the tunnel expectation", throwable = failure)
                false
            }
        if (!recorded) {
            // encodeSettings reports a failed write by returning false, not by
            // throwing (MmkvManager.encodeSettings): an unrecorded expectation
            // silently leaves the guard disarmed, so it has to reach the log.
            LogUtil.w(message = "Tunnel expectation was not persisted; the guard stays disarmed")
        }
    }

    /** Arms the next check; the receiver re-arms it after every firing. */
    fun schedule(context: Context) {
        if (!isExpected(context) || recoveryPaused(context)) { cancel(context); return }
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + maxOf(CHECK_INTERVAL_MS, RecoveryBackoff.remainingMillis(context))
        runCatching {
            // Allow-while-idle needs no exact-alarm permission; the platform
            // may defer it in Doze, which is the documented behaviour.
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context))
        }.onFailure { LogUtil.w(message = "Could not arm the tunnel guard", throwable = it) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pending(context)) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        // Explicit component: matches the manifest entry without needing an
        // action filter, so nothing else can trigger the guard.
        Intent(context, TunnelGuardReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * The alarm's landing point. Runs even when the app's process is gone, because
 * the platform starts a fresh one for the broadcast.
 */
class TunnelGuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!TunnelGuard.isExpected(context) || TunnelGuard.recoveryPaused(context)) {
            // Disconnected while the alarm was in flight; let it lapse.
            TunnelGuard.cancel(context)
            return
        }
        if (RecoveryBackoff.remainingMillis(context) > 0) { TunnelGuard.schedule(context); return }
        if (!MikuVpnService.running && ConnectionStatus.phase.value != ConnectionStatus.Phase.CONNECTING) {
            LogUtil.w(message = "Tunnel expected but not running; restarting it")
            // The start is refused outright when the platform does not allow a
            // background foreground-service start (no battery-optimization
            // exemption, device in Doze); say so instead of throwing out of a
            // broadcast, and keep the alarm armed so the next window retries.
            runCatching { MikuVpnService.start(context, recovery = true) }
                .onFailure {
                    ConnectionStatus.update(context, ConnectionStatus.Phase.DISCONNECTED)
                    TunnelGuard.failed(context)
                    LogUtil.w(message = "Tunnel guard could not restart the service", throwable = it)
                }
        }
        TunnelGuard.schedule(context)
    }
}

/** Persist across process death so a bad profile cannot produce an endless reconnect loop. */
internal object RecoveryBackoff {
    private fun prefs(context: Context) = context.getSharedPreferences("tunnel_recovery", Context.MODE_PRIVATE)
    fun failures(context: Context): Int = prefs(context).getInt("failures", 0)
    fun remainingMillis(context: Context, now: Long = System.currentTimeMillis()): Long =
        (prefs(context).getLong("retry_at", 0) - now).coerceIn(0L, 120_000L)
    @Synchronized fun failed(context: Context, now: Long = System.currentTimeMillis()) {
        val failures = (failures(context) + 1).coerceAtMost(3)
        prefs(context).edit().putInt("failures", failures)
            .putLong("retry_at", now + (30_000L shl (failures - 1))).commit()
    }
    @Synchronized fun reset(context: Context) { prefs(context).edit().clear().commit() }
}
