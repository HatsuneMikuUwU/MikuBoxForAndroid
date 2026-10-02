package com.miku.ray.dto

import java.io.Serializable

data class CountryCodeTestMessage(
    val key: Int,
    val requestId: String = "",
    val subscriptionId: String = "",
    val serverGuids: List<String> = emptyList()
) : Serializable
