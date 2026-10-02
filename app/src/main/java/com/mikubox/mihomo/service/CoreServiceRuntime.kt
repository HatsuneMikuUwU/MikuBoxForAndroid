package com.mikubox.mihomo.service

import java.util.concurrent.Executors
import com.mikubox.mihomo.core.MihomoCore

/** Both Android services share one native core and one process-wide work queue. */
internal object CoreServiceRuntime {
    val executor = Executors.newSingleThreadExecutor()
    private val ownership = CoreOwnership()
    private var finishCurrent: () -> Unit = {}

    // Called only on executor; a destroyed service cannot stop its successor.
    fun start(owner: Any, beforeStop: () -> Unit = {}, action: () -> Result<Unit>): Result<Unit> {
        ownership.releaseCurrent(::stopCurrent)
        return action().also {
            if (it.isSuccess) {
                ownership.claim(owner)
                finishCurrent = beforeStop
            }
        }
    }

    fun stop(owner: Any) {
        ownership.release(owner, ::stopCurrent)
    }

    private fun stopCurrent() {
        try {
            finishCurrent()
        } finally {
            finishCurrent = {}
            MihomoCore.stop()
        }
    }
}

internal class CoreOwnership {
    private var current: Any? = null

    fun claim(owner: Any) { current = owner }

    fun release(owner: Any, stop: () -> Unit) {
        if (current !== owner) return
        current = null
        stop()
    }

    fun releaseCurrent(stop: () -> Unit) {
        current?.let { release(it, stop) }
    }
}
