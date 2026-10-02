package com.miku.ray.contracts

interface Tun2SocksControl {
    fun startTun2Socks()

    fun stopTun2Socks()

    fun isTunnelRunning(): Boolean

    fun getTunnelStats(): LongArray?
}
