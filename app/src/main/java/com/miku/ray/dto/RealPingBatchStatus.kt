package com.miku.ray.dto

import java.io.Serializable

data class RealPingResult(
    val testId: String,
    val guid: String,
    val delayMillis: Long,
) : Serializable

data class RealPingProgress(
    val testId: String,
    val completed: Int,
    val total: Int,
) : Serializable

data class RealPingSummary(
    val testId: String,
    val live: Int,
    val total: Int,
    val cancelled: Boolean,
    val listChanged: Boolean = false,
) : Serializable
