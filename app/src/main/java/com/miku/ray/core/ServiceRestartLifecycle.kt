package com.miku.ray.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ServiceRestartLifecycle(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    internal class Token internal constructor(val id: Long)

    private val lock = Any()
    private var nextTokenId = 0L
    private var activeToken: Token? = null
    private var ownerJob: Job? = null
    private var restartJob: Job? = null

    fun launch(
        onStarting: () -> Unit,
        block: suspend (Token) -> Unit,
    ): Boolean {
        val token: Token
        val owner: Job
        val work: Job
        synchronized(lock) {
            if (activeToken != null) return false

            token = Token(++nextTokenId)
            owner = SupervisorJob()
            work = CoroutineScope(owner + dispatcher + CoroutineName("MikuRayServiceRestart"))
            .launch(start = CoroutineStart.LAZY) { block(token) }
            activeToken = token
            ownerJob = owner
            restartJob = work
        }

        work.invokeOnCompletion {
            synchronized(lock) {
                if (restartJob === work) {
                    restartJob = null
                    ownerJob = null
                }
            }
            owner.cancel()
        }

        try {
            onStarting()
        } catch (e: Exception) {
            cancel()
            throw e
        }
        work.start()
        return true
    }

    fun isActive(): Boolean = synchronized(lock) { activeToken != null }

    fun isCurrent(token: Token): Boolean = synchronized(lock) { activeToken == token }

    fun complete(token: Token): Boolean = synchronized(lock) {
        if (activeToken != token) return@synchronized false
        activeToken = null
        true
    }

    fun completeCurrent(): Boolean = synchronized(lock) {
        val wasActive = activeToken != null
        activeToken = null
        wasActive
    }

    fun cancel(): Boolean {
        val owner: Job?
        val wasActive: Boolean
        synchronized(lock) {
            wasActive = activeToken != null
            activeToken = null
            owner = ownerJob
            ownerJob = null
            restartJob = null
        }
        owner?.cancel()
        return wasActive
    }
}
