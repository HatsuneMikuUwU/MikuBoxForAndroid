package com.mikubox.mihomo.service

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Service-owned state: a request is never presented as an established tunnel. */
object ConnectionStatus {
    enum class Phase { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }
    private val mutable = MutableStateFlow(Phase.DISCONNECTED)
    val phase = mutable.asStateFlow()
    @Volatile private var connectedAt = 0L
    @Volatile var revision = 0L
        private set

    @Synchronized fun update(context: Context, value: Phase): Long {
        revision += 1
        if (value == Phase.CONNECTED && mutable.value != value) connectedAt = SystemClock.elapsedRealtime()
        if (value == Phase.DISCONNECTED) connectedAt = 0L
        mutable.value = value
        runCatching { TileService.requestListeningState(context, ComponentName(context, MikuTileService::class.java)) }
        return revision
    }

    fun elapsedMillis(): Long = if (phase.value == Phase.CONNECTED)
        (SystemClock.elapsedRealtime() - connectedAt).coerceAtLeast(0L) else 0L
}
