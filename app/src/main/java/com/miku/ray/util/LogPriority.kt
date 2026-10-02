package com.miku.ray.util

object LogPriority {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val ASSERT = 7

    fun levelChar(priority: Int): Char = when (priority) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        ASSERT -> 'F'
        else -> '?'
    }

    fun fromLevelChar(level: Char): Int = when (level) {
        'V' -> VERBOSE
        'D' -> DEBUG
        'I' -> INFO
        'W' -> WARN
        'E' -> ERROR
        'F' -> ASSERT
        else -> INFO
    }
}
