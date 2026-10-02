package com.miku.ray.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.LauncherManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.shortcut.ScStartActivity
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import java.lang.ref.SoftReference

class QSTileService : TileService() {

    fun setState(state: Int) {
        qsTile?.icon = Icon.createWithResource(applicationContext, R.drawable.ic_stat_name)
        if (state == Tile.STATE_INACTIVE) {
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = com.miku.ray.util.AppNameHelper.getDisplayName(applicationContext)
        } else if (state == Tile.STATE_ACTIVE) {
            qsTile?.state = Tile.STATE_ACTIVE
            qsTile?.label = CoreServiceManager.getRunningServerName()
        }

        qsTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()

        if (CoreServiceManager.isRunning()) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
        mMsgReceive = ReceiveMessageHandler(this)
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(applicationContext, mMsgReceive, mFilter, Utils.receiverFlags())
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onStopListening() {
        super.onStopListening()

        try {
            applicationContext.unregisterReceiver(mMsgReceive)
            mMsgReceive = null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to unregister receiver", e)
        }

    }

    override fun onClick() {
        super.onClick()
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                if (isLocked) {
                    unlockAndRun { startServiceWithActivityFallback() }
                } else {
                    startServiceWithActivityFallback()
                }
            }

            Tile.STATE_ACTIVE -> {
                LauncherManager.stopService(this)
            }
        }
    }

    private fun startServiceWithActivityFallback() {
        val needsVpnConsent = SettingsManager.isVpnMode() && VpnService.prepare(this) != null
        if (needsVpnConsent || !LauncherManager.startServiceFromToggle(this)) {
            startViaShortcutActivity()
        }
    }

    private fun startViaShortcutActivity() {
        val intent = Intent(this, ScStartActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_START_FROM_TILE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private var mMsgReceive: BroadcastReceiver? = null

    private class ReceiveMessageHandler(context: QSTileService) : BroadcastReceiver() {
        var mReference: SoftReference<QSTileService> = SoftReference(context)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val context = mReference.get()
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    context?.setState(Tile.STATE_ACTIVE)
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    context?.setState(Tile.STATE_INACTIVE)
                }
            }
        }
    }

    private companion object {
        const val REQUEST_CODE_START_FROM_TILE = 4_104
    }
}
