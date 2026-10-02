package com.mikubox.mihomo.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import com.miku.ray.util.LogUtil

/**
 * Public entry point for starting/stopping the VPN.
 *
 * The UI layer (built separately) only needs to call [connect] / [disconnect];
 * the consent handshake ([VpnService.prepare]) is handled transparently via
 * [VpnRequestActivity] when needed.
 */
object VpnController {

    val isRunning: Boolean
        get() = MikuVpnService.running

    /**
     * Start the VPN. If the user has not yet granted VPN consent, this routes
     * through [VpnRequestActivity] to show the system consent dialog first.
     */
    fun connect(context: Context) {
        if (isRunning) return
        ConnectionStatus.update(context, ConnectionStatus.Phase.CONNECTING)
        if (VpnService.prepare(context) != null) {
            context.startActivity(
                Intent(context, VpnRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            runCatching { MikuVpnService.start(context) }.onFailure { error ->
                // Tasker/widget callers run with no background-start exemption:
                // the platform rejects the service start, and both the crash it
                // would raise here and a phase stuck on CONNECTING (the tile
                // refuses taps then) are worse than reporting the failure.
                ConnectionStatus.update(context, ConnectionStatus.Phase.DISCONNECTED)
                LogUtil.w(message = "VPN start request rejected", throwable = error)
                com.miku.ray.util.MessageUtil.sendMsg2UI(context, com.miku.ray.AppConfig.MSG_STATE_START_FAILURE, "")
            }
        }
    }

    fun disconnect(context: Context) {
        MikuVpnService.stop(context)
    }

    /**
     * Reloads the core so settings that are read at start apply right away.
     *
     * Changing one setting is a reload, and changing five in a row is still only
     * one: requests are collected for a moment so a run through the settings page
     * does not restart the tunnel five times. The running check happens again
     * when the debounce fires, so a disconnect made during the wait stays a
     * disconnect instead of being resurrected by the stale request.
     */
    fun restart(context: Context) {
        if (!MikuVpnService.running) {
            // The start task reads the settings the moment it runs, so a change
            // made while the tunnel is connecting would silently apply to
            // nothing. Queue it: the service calls [onTunnelConnected] once the
            // tunnel is up and the request is replayed. While fully
            // disconnected nothing is queued — the next start re-reads every
            // setting anyway, so a replay would only bounce the tunnel.
            if (MikuVpnService.starting) pendingRestartWhenConnected = true
            return
        }
        val app = context.applicationContext
        pendingRestart?.let(handler::removeCallbacks)
        val request = Runnable {
            pendingRestart = null
            if (!MikuVpnService.running) return@Runnable
            runCatching {
                app.startService(
                    // The full, package-qualified action: the service matches
                    // vpnAction(...), so the bare suffix constant would be
                    // delivered to a `when` that silently drops it.
                    Intent(app, MikuVpnService::class.java)
                        .setAction(MikuVpnService.vpnAction(app.packageName, MikuVpnService.ACTION_RESTART)),
                )
            }.onFailure {
                // A rejected start used to vanish here, leaving the switch
                // looking applied while the core kept the old configuration.
                LogUtil.w(message = "Reload request could not reach the service", throwable = it)
            }
        }
        pendingRestart = request
        handler.postDelayed(request, RESTART_DEBOUNCE_MS)
    }

    /** Applies settings the running tunnel can adopt without reloading the core. */
    fun refresh(context: Context) {
        if (!MikuVpnService.running) return
        val app = context.applicationContext
        runCatching {
            app.startService(
                Intent(app, MikuVpnService::class.java)
                    .setAction(MikuVpnService.vpnAction(app.packageName, MikuVpnService.ACTION_REFRESH)),
            )
        }
    }

    /**
     * The service reached CONNECTED: replay a restart request that landed
     * while the tunnel was connecting. [MikuVpnService.starting] is re-checked
     * inside [restart] via the debounce callback, so a disconnect that raced
     * the replay still stays a disconnect.
     */
    fun onTunnelConnected(context: Context) {
        if (!pendingRestartWhenConnected) return
        pendingRestartWhenConnected = false
        restart(context)
    }

    /** A new start or a teardown invalidates a queued mid-connect restart. */
    fun clearPendingRestart() {
        pendingRestartWhenConnected = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRestart: Runnable? = null

    @Volatile
    private var pendingRestartWhenConnected = false

    private const val RESTART_DEBOUNCE_MS = 1200L
}
