package moe.mikubox.cla.service

import android.content.Context
import android.content.Intent
import android.net.VpnService

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
        if (VpnService.prepare(context) != null) {
            context.startActivity(
                Intent(context, VpnRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            MikuVpnService.start(context)
        }
    }

    fun disconnect(context: Context) {
        MikuVpnService.stop(context)
    }
}
