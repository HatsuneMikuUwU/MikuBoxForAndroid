package com.miku.ray.util

import com.miku.ray.AppConfig

object FakeDnsIpPool {

    data class Config(
        val cidr: String,
        val poolSize: Int,
    )

    fun parseOrDefault(value: String?): Config {
        val cidr = value?.trim().takeIf(::isValid) ?: AppConfig.DEFAULT_FAKE_DNS_IP_POOL
        return Config(cidr = cidr, poolSize = calculatePoolSize(cidr))
    }

    fun isValid(value: String?): Boolean {
        val parts = value?.trim()?.split("/") ?: return false
        if (parts.size != 2) return false

        val prefixLength = parts[1].toIntOrNull() ?: return false
        if (prefixLength !in 1..30) return false

        val octets = parts[0].split(".")
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun calculatePoolSize(cidr: String): Int {
        val prefixLength = cidr.substringAfter('/').toInt()
        val addressCount = 1L shl (32 - prefixLength)
        return (addressCount - 1)
        .coerceAtMost(AppConfig.FAKE_DNS_MAX_POOL_SIZE.toLong())
        .toInt()
    }
}
