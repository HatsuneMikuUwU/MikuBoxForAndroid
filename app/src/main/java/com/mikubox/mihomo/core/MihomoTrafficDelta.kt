package com.mikubox.mihomo.core

import com.miku.ray.AppConfig

/**
 * Turns the core's session totals into the per-query deltas the vendored traffic
 * loop expects.
 *
 * The core reports, per node and group, everything carried since the tunnel came
 * up. MikuRay's loop reads each answer as "what moved since I last asked" and
 * adds it to the running totals behind the rows, so handing it the session totals
 * unchanged would count the same bytes again on every tick. The difference is
 * taken here, per tag, and the memory is dropped when a session starts or ends —
 * which is when the core resets its own counters.
 */
object MihomoTrafficDelta {

    private class Counters(var upload: Long, var download: Long)

    private val seen = mutableMapOf<String, Counters>()

    /** Forgets the last-seen totals, so the next answer counts from zero. */
    fun reset() = synchronized(seen) { seen.clear() }

    /** `tag,upload,bytes` lines, each a delta since the previous call. */
    fun take(): String = take(runCatching { MihomoCore.trafficByProxy() }.getOrDefault(emptyMap()))

    /**
     * The same, for counters handed in directly — which is how the arithmetic is
     * exercised without a core behind it.
     */
    fun take(current: Map<String, MihomoCore.ProxyTraffic>): String = synchronized(seen) {
        if (current.isEmpty()) return ""
        buildString {
            current.forEach { (name, counters) ->
                val tag = tagFor(name)
                val previous = seen[tag]
                val upload = (counters.upload - (previous?.upload ?: 0L)).coerceAtLeast(0L)
                val download = (counters.download - (previous?.download ?: 0L)).coerceAtLeast(0L)
                // A tag first seen mid-session carries everything the core counted
                // for it so far, which is what the caller has not been told yet.
                if (previous == null) {
                    seen[tag] = Counters(counters.upload, counters.download)
                } else {
                    previous.upload = counters.upload
                    previous.download = counters.download
                }
                if (upload > 0L) appendLine("$tag,${AppConfig.UPLINK},$upload")
                if (download > 0L) appendLine("$tag,${AppConfig.DOWNLINK},$download")
            }
        }
    }

    /**
     * The core spells its two built-in outbounds in upper case. MikuRay's loop
     * recognises its own spellings — anything else is filed as proxied traffic —
     * so a chain of DIRECT or REJECT would otherwise land on the selected row.
     */
    private fun tagFor(name: String): String = when (name.uppercase()) {
        "DIRECT" -> AppConfig.TAG_DIRECT
        "REJECT", "REJECT-DROP" -> AppConfig.TAG_BLOCKED
        else -> name
    }
}
