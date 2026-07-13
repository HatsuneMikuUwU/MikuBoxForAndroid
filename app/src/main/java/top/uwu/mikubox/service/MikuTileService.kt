package top.uwu.mikubox.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import top.uwu.mikubox.profile.MihomoProfileStore

/** Quick Settings start/stop control, independent from the future UI port. */
class MikuTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun { toggle() }
        } else {
            toggle()
        }
    }

    private fun toggle() {
        if (VpnController.isRunning) {
            VpnController.disconnect(this)
        } else if (MihomoProfileStore.selected(this) != null) {
            VpnController.connect(this)
        }
        refresh()
    }

    private fun refresh() {
        qsTile?.apply {
            label = MihomoProfileStore.selected(this@MikuTileService)?.name
                ?: getString(top.uwu.mikubox.R.string.app_name)
            state = if (VpnController.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
