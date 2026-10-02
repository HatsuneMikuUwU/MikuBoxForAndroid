package com.miku.ray.util

import android.util.Log
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import java.util.Locale

object LogUtil {

    const val TAG_CORE = "XrayCore"

    private const val DEFAULT_LEVEL = "warning"
    private const val CACHE_UNSET = Int.MIN_VALUE

    @Volatile
    private var cachedMinPriority: Int = CACHE_UNSET

    private fun parsePriority(level: String?): Int {
        return when ((level ?: DEFAULT_LEVEL).lowercase(Locale.US)) {
            "verbose" -> LogPriority.VERBOSE
            "debug" -> LogPriority.DEBUG
            "info" -> LogPriority.INFO
            "warn", "warning" -> LogPriority.WARN
            "error" -> LogPriority.ERROR
            "none", "off" -> Int.MAX_VALUE
            else -> LogPriority.WARN
        }
    }

    // Reads the persisted level only when MMKV is ready. Unit tests (and any
    // very-early process state before Application.onCreate) run without
    // MMKV.initialize(), and a logging helper must never be the thing that
    // crashes the caller — fall back to the default level instead.
    private fun readPersistedLevel(): String? =
        runCatching { MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL, DEFAULT_LEVEL) }
            .getOrNull()

    @Suppress("unused")
    fun refreshLogLevel() {
        cachedMinPriority = parsePriority(readPersistedLevel())
    }

    private fun minPriority(): Int {
        val cached = cachedMinPriority
        if (cached != CACHE_UNSET) return cached

        return synchronized(this) {
            val current = cachedMinPriority
            if (current != CACHE_UNSET) {
                current
            } else {
                parsePriority(readPersistedLevel()).also {
                    cachedMinPriority = it
                }
            }
        }
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message

        // Buffered unconditionally (matches the old MikuRayLogTree behavior),
        // so LogcatActivity always has the full history regardless of PREF_LOGLEVEL.
        InProcessLogBuffer.append(priority, tag, fullMessage)

        if (priority >= minPriority()) {
            Log.println(priority, tag, fullMessage)
        }
    }

    fun v(tag: String = AppConfig.TAG, message: String) = log(LogPriority.VERBOSE, tag, message)
    fun d(tag: String = AppConfig.TAG, message: String) = log(LogPriority.DEBUG, tag, message)
    fun i(tag: String = AppConfig.TAG, message: String) = log(LogPriority.INFO, tag, message)
    fun w(tag: String = AppConfig.TAG, message: String) = log(LogPriority.WARN, tag, message)
    fun e(tag: String = AppConfig.TAG, message: String) = log(LogPriority.ERROR, tag, message)

    fun d(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = log(LogPriority.DEBUG, tag, message, throwable)
    fun i(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = log(LogPriority.INFO, tag, message, throwable)
    fun w(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = log(LogPriority.WARN, tag, message, throwable)
    fun e(tag: String = AppConfig.TAG, message: String, throwable: Throwable) = log(LogPriority.ERROR, tag, message, throwable)

    fun core(levelHint: Long, message: String?) {
        if (message.isNullOrEmpty()) return
        when {
            levelHint >= 3L -> e(TAG_CORE, message)
            levelHint == 2L -> w(TAG_CORE, message)
            levelHint == 0L -> d(TAG_CORE, message)
            else -> i(TAG_CORE, message)
        }
    }
}
