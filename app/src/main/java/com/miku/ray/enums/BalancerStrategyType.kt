package com.miku.ray.enums

enum class BalancerStrategyType(
    val policyGroupType: String,
    val policyGroupTypeValue: String,
    val requiresBurstObservatory: Boolean = false,
    val requiresObservatory: Boolean = false,
    val supportsObservatory: Boolean = false,
) {
    LEAST_LOAD("leastLoad", "1", requiresBurstObservatory = true),
    RANDOM("random", "2", supportsObservatory = true),
    ROUND_ROBIN("roundRobin", "3", supportsObservatory = true),
    LEAST_PING("leastPing", "", requiresObservatory = true);

    companion object {
        fun from(policyGroupType: String?): BalancerStrategyType =
        entries.firstOrNull { it.policyGroupTypeValue == policyGroupType } ?: LEAST_PING
    }
}
