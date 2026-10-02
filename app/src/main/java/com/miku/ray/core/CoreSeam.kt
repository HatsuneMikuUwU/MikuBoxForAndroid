package com.miku.ray.core

/**
 * The core seam, spelled out locally.
 *
 * MikuRay's copies of these three types came from its Xray GoMobile bindings
 * (`libv2ray.*`, generated from Go). MikuBox has no such bindings — its core is
 * mihomo, embedded in-process — so the same shapes are declared here and
 * implemented against MikuBox's own core. Everything above this line is
 * MikuRay's UI, unchanged; everything below it is the adapter.
 */

/** Progress callback the core reports through; mihomo logs instead. */
interface CoreCallbackHandler {
    fun startup(): Long

    fun shutdown(): Long

    fun onEmitStatus(code: Long, message: String?): Long
}

/** Connection lookup used by MikuRay's per-app/route-only features. */
interface ProcessFinder {
    fun findProcessByConnection(
        network: String,
        srcIP: String,
        srcPort: Long,
        destIP: String,
        destPort: Long,
    ): Long
}

/**
 * What MikuRay's service manager drives.
 *
 * The surface is exactly the six members `CoreServiceManager` calls, so its
 * logic keeps working against the mihomo implementation below.
 */
interface CoreController {
    val isRunning: Boolean

    fun startLoop(vpnInterface: android.os.ParcelFileDescriptor?, config: String): Boolean

    fun stopLoop(): Boolean

    fun measureDelay(config: String, testUrl: String): Long

    /** `tag,direction,bytes` entries, as MikuRay's stats reader expects. */
    fun queryAllOutboundTrafficStats(): String

    fun registerProcessFinder(finder: ProcessFinder)
}
