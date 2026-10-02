package com.miku.ray.handler

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The home screen and notification share one sample for the current exit.
 *
 * Failures are shared as well. Every requester used to start a fetch of its own
 * — the notification beats every three seconds, the screen retries on the same
 * order, and the mutex below only *serialized* those fetches, it did not dedupe
 * them — so an exit that cannot be measured (a node that is down, a profile
 * that routes the probe's endpoints through a node of its own, a core still
 * warming up) was probed again the moment each caller got its turn, and one
 * round is three HTTPS attempts with a five-second window. A failed probe now
 * holds every caller off for [retryDelayMs], so a broken exit costs a round
 * every 15-30 s instead of a continuous stream. [invalidate] is the way to say
 * "ask again now": it is what a deliberate refresh does.
 */
object ExitIpSnapshot {
    private val mutex = Mutex()
    private var epoch = 0L
    private var value: String? = null
    private var sampledAt = 0L
    private var failedAt = 0L
    private var failures = 0

    @Synchronized fun current(): String? = value

    /** A deliberate refresh: the reading is gone, and so is the backoff. */
    @Synchronized fun invalidate() {
        epoch++
        value = null
        sampledAt = 0L
        failedAt = 0L
        failures = 0
    }

    suspend fun get(fetch: suspend () -> String?): String? = mutex.withLock {
        val request: Long
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            value?.takeIf { now - sampledAt < SAMPLE_TTL_MS }?.let { return@withLock it }
            if (failures > 0 && now - failedAt < retryDelayMs(failures)) return@withLock null
            request = epoch
        }
        val fetched = fetch()
        synchronized(this) {
            when {
                // Obsolete: the world moved past the sample this fetch was
                // asked for. The state stays as it is, backoff included.
                epoch != request -> null

                fetched.isNullOrBlank() -> {
                    failures++
                    failedAt = SystemClock.elapsedRealtime()
                    null
                }

                else -> {
                    value = fetched
                    sampledAt = SystemClock.elapsedRealtime()
                    failures = 0
                    fetched
                }
            }
        }
    }

    /**
     * How long a failed probe holds the next one off: 15 s after the first
     * (usually the core's warm-up window swallowing the attempt right after a
     * connect or a node switch), 30 s from the second on, capped there — a
     * tunnel whose exit is unreadable still carries traffic, so waiting minutes
     * for the line is worse than a cheap probe every half minute. Public
     * because the notification's own beat reads the same schedule; the probe
     * itself and the notification must not drift apart.
     */
    fun retryDelayMs(failures: Int): Long =
        RETRY_STEP_MS shl (failures - 1).coerceIn(0, 1)

    /** Reading lifetime: the notification refreshes its line on this order. */
    private const val SAMPLE_TTL_MS = 60_000L

    private const val RETRY_STEP_MS = 15_000L
}
