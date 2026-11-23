package io.nekohasekai.sagernet.bg

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService as AndroidVpnService
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import android.service.quicksettings.TileService as BaseTileService

@RequiresApi(24)
class TileService : BaseTileService(), SagerConnection.Callback {
    private val iconIdle by lazy { Icon.createWithResource(this, R.drawable.ic_service_idle) }
    private val iconBusy by lazy { Icon.createWithResource(this, R.drawable.ic_service_busy) }
    private val iconConnected by lazy {
        Icon.createWithResource(this, R.drawable.ic_service_active)
    }
    private var tapPending = false

    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_TILE)
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) =
        updateTile(state, profileName)

    override fun onServiceConnected(service: ISagerNetService) {
        val state = BaseService.State.values().getOrElse(service.state) { BaseService.State.Stopped }
        updateTile(state, service.profileName)
        if (tapPending) {
            tapPending = false
            onClick()
        }
    }

    override fun cbSelectorUpdate(id: Long) {
        val profile = SagerDatabase.proxyDao.getById(id) ?: return
        updateTile(BaseService.State.Connected, profile.displayName())
    }

    override fun onStartListening() {
        super.onStartListening()
        connection.connect(this, this)
    }

    override fun onStopListening() {
        connection.disconnect(this)
        super.onStopListening()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(this::toggle) else toggle()
    }

    private fun updateTile(serviceState: BaseService.State, profileName: String?) {
        qsTile?.apply {
            label = null
            when (serviceState) {
                BaseService.State.Idle -> {
                    icon = iconIdle
                    state = Tile.STATE_INACTIVE
                }
                BaseService.State.Connecting -> {
                    icon = iconBusy
                    state = Tile.STATE_ACTIVE
                }
                BaseService.State.Connected -> {
                    icon = iconConnected
                    label = profileName
                    state = Tile.STATE_ACTIVE
                }
                BaseService.State.Stopping -> {
                    icon = iconBusy
                    state = Tile.STATE_UNAVAILABLE
                }
                BaseService.State.Stopped -> {
                    icon = iconIdle
                    state = Tile.STATE_INACTIVE
                }
            }
            label = label ?: getString(R.string.app_name)
            updateTile()
        }
    }

    private fun toggle() {
        val service = connection.service
        if (service == null) {
            tapPending = true
            return
        }
        val state = BaseService.State.values().getOrElse(service.state) { BaseService.State.Stopped }
        when {
            state.canStop -> SagerNet.stopService()
            state == BaseService.State.Stopped || state == BaseService.State.Idle -> {
                runOnDefaultDispatcher {
                    val selected = if (DataStore.selectedProxy > 0L) {
                        SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                    } else {
                        null
                    }
                    val target = selected
                        ?: SagerDatabase.proxyDao.getByGroup(DataStore.selectedGroup).firstOrNull()
                        ?: SagerDatabase.groupDao.allGroups()
                            .asSequence()
                            .mapNotNull { group ->
                                SagerDatabase.proxyDao.getByGroup(group.id).firstOrNull()?.also {
                                    DataStore.selectedGroup = group.id
                                }
                            }
                            .firstOrNull()
                    if (target != null) {
                        if (selected == null) {
                            DataStore.selectedProxy = target.id
                        }
                        onMainDispatcher {
                            if (DataStore.serviceMode == Key.MODE_VPN && AndroidVpnService.prepare(this@TileService) != null) {
                                @Suppress("DEPRECATION")
                                startActivityAndCollapse(
                                    Intent(this@TileService, VpnRequestActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                SagerNet.startService()
                            }
                        }
                    }
                }
            }
        }
    }
}
