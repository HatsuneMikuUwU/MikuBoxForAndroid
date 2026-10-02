package com.miku.ray.dto

import java.io.Serializable

data class SubscriptionUpdateMessage(
    val key: Int,
    val forcedUpdate: Boolean,
    val subIds: List<String> = listOf()
) : Serializable
