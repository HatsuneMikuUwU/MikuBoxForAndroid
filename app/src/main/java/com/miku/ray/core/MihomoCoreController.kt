package com.miku.ray.core

import android.content.Context
import com.miku.ray.util.LogUtil

/**
 * The mihomo implementation of [CoreController].
 *
 * MikuBox runs the core inside the app process and hands it the TUN descriptor
 * through its own VPN service, so there is no separate native loop to drive:
 * starting and stopping go through the service, delay measurement and traffic
 * accounting through the core's own API, and the process finder is accepted but
 * unused — mihomo resolves processes itself.
 */
class MihomoCoreController(
    private val handler: CoreCallbackHandler,
) : CoreController {

    override val isRunning: Boolean
        get() = com.miku.ray.MikuCoreBridge.isRunning()

    override fun startLoop(vpnInterface: android.os.ParcelFileDescriptor?, config: String): Boolean {
        handler.startup()
        val started = com.miku.ray.MikuCoreBridge.start(config)
        if (started) handler.onEmitStatus(0, "started")
        return started
    }

    override fun stopLoop(): Boolean {
        val stopped = com.miku.ray.MikuCoreBridge.stop()
        handler.shutdown()
        return stopped
    }

    override fun measureDelay(config: String, testUrl: String): Long =
        com.miku.ray.MikuCoreBridge.measureDelay(config, testUrl)

    override fun queryAllOutboundTrafficStats(): String =
        com.miku.ray.MikuCoreBridge.outboundTrafficStats()

    override fun registerProcessFinder(finder: ProcessFinder) {
        // mihomo matches processes itself (find-process-mode); nothing to install.
        LogUtil.d(com.miku.ray.AppConfig.TAG, "process finder not needed by the mihomo core")
    }

    companion object {
        fun create(context: Context?, handler: CoreCallbackHandler): CoreController =
            MihomoCoreController(handler)
    }
}
