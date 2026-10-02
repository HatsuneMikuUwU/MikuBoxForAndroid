package com.miku.ray.dto

sealed class RealPingEvent {

    data class Progress(val completed: Int, val total: Int) : RealPingEvent()

    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    data class Finish(
        val live: Int,
        val completed: Int,
        val total: Int,
    ) : RealPingEvent()
}
