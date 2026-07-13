package top.uwu.mikubox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import top.uwu.mikubox.profile.MihomoProfileStore
import top.uwu.mikubox.profile.MihomoSubscriptionUpdater

/** Restores UwU's non-UI boot behaviour: scheduled updates and optional VPN restart. */
class MikuBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                MihomoSubscriptionUpdater.reconfigure(context)
                if (
                    intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
                    MihomoProfileStore.autoStart(context) &&
                    MihomoProfileStore.selected(context) != null
                ) {
                    // Android forbids a background receiver from showing the
                    // consent activity. Reconnect only when consent survived.
                    if (VpnService.prepare(context) == null) MikuVpnService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
