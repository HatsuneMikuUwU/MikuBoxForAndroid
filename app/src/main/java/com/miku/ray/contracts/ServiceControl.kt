package com.miku.ray.contracts

import android.app.Service
import android.net.Network

interface ServiceControl {
    fun getService(): Service

    fun startService()

    fun stopService()

    fun vpnProtect(socket: Int): Boolean

    fun setUnderlyingNetworks(networks: Array<Network>?): Boolean = false
}
