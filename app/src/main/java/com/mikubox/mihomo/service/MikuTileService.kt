package com.mikubox.mihomo.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.*
import com.mikubox.mihomo.R
import com.mikubox.mihomo.profile.MihomoProfileStore

class MikuTileService : TileService() {
    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch { ConnectionStatus.phase.collect { refresh() } }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listening?.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { safelyToggle() } else safelyToggle()
    }

    private fun safelyToggle() {
        runCatching { toggle() }.onFailure {
            ConnectionStatus.update(this, if (VpnController.isRunning) ConnectionStatus.Phase.CONNECTED else ConnectionStatus.Phase.DISCONNECTED)
            Toast.makeText(this, it.message ?: getString(R.string.tile_disconnected), Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    // The PendingIntent overload is only available on Android 14+.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun toggle() {
        when (ConnectionStatus.phase.value) {
            ConnectionStatus.Phase.CONNECTING, ConnectionStatus.Phase.DISCONNECTING -> return
            ConnectionStatus.Phase.CONNECTED -> VpnController.disconnect(this)
            ConnectionStatus.Phase.DISCONNECTED -> {
                if (MihomoProfileStore.selected(this) == null) {
                    Toast.makeText(this, R.string.tile_select_profile, Toast.LENGTH_SHORT).show()
                    return
                }
                if (VpnService.prepare(this) != null) {
                    ConnectionStatus.update(this, ConnectionStatus.Phase.CONNECTING)
                    val intent = Intent(this, VpnRequestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    else startActivityAndCollapse(intent)
                } else VpnController.connect(this)
            }
        }
        refresh()
    }

    private fun refresh() {
        val phase = ConnectionStatus.phase.value
        val description = getString(when (phase) {
            ConnectionStatus.Phase.DISCONNECTED -> R.string.tile_disconnected
            ConnectionStatus.Phase.CONNECTING -> R.string.tile_connecting
            ConnectionStatus.Phase.CONNECTED -> R.string.tile_connected
            ConnectionStatus.Phase.DISCONNECTING -> R.string.tile_disconnecting
        })
        qsTile?.apply {
            icon = Icon.createWithResource(this@MikuTileService, com.miku.ray.R.drawable.ic_stat_name)
            label = if (phase == ConnectionStatus.Phase.CONNECTED)
                MihomoProfileStore.selected(this@MikuTileService)?.name ?: getString(R.string.app_name)
            else getString(R.string.app_name)
            state = if (phase == ConnectionStatus.Phase.CONNECTED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = description else label = "$label · $description"
            if (Build.VERSION.SDK_INT >= 30) stateDescription = description
            contentDescription = "$label, $description"
            updateTile()
        }
    }
}
