package com.miku.ray.core

import android.content.Context
import com.miku.ray.MikuCoreBridge
import com.miku.ray.handler.SettingsChangeManager

/**
 * The connect control's way into the core, on mihomo.
 *
 * MikuRay's version starts one of its own services — root, VPN or proxy-only —
 * chosen from the selected server and the root setting, because the Xray core
 * lived inside those services. MikuBox owns the tunnel in its own VPN service,
 * and that service is what [MikuCoreBridge] drives, so this keeps the same
 * surface (the home button, the quick-settings tile, the widgets and the boot
 * receiver all call it) and routes start, stop and restart there.
 *
 * A consequence worth stating: root mode and the proxy-only mode have no
 * counterpart here — this app always runs one VPN-based tunnel.
 */
object LauncherManager {

    fun startServiceFromToggle(context: Context): Boolean = startServiceAfterRestart(context)

    fun startService(context: Context, guid: String? = null) {
        guid?.let { com.miku.ray.handler.MmkvManager.setSelectServer(it) }
        // Which profile to connect is MikuBox's own selection; the bridge moves
        // that selection to match MikuRay's list before it starts (see its `start`).
        startServiceAfterRestart(context)
    }

    internal fun startServiceAfterRestart(context: Context): Boolean {
        val started = runCatching { MikuCoreBridge.start("") }.getOrDefault(false)
        SettingsChangeManager.makeRefreshDisplayPrefs()
        return started
    }

    fun stopService(context: Context) {
        runCatching { MikuCoreBridge.stop() }
        SettingsChangeManager.makeRefreshDisplayPrefs()
    }

    fun restartService(context: Context) {
        restartService(context) { }
    }

    fun restartService(context: Context, onResult: (handled: Boolean) -> Unit) {
        // Keep the service alive while its serialized core queue performs the reload.
        onResult(runCatching { MikuCoreBridge.restart() }.getOrDefault(false))
    }

    fun restartServiceOrStart(context: Context, startIfStopped: () -> Unit) {
        if (MikuCoreBridge.isRunning()) {
            restartService(context)
        } else {
            startIfStopped()
        }
    }
}
