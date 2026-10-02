package com.miku.ray.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var uplinkTotal: Long = 0L,
    var downlinkTotal: Long = 0L,
    var countryCode: String? = null,
) {
    fun getTestDelayString(): String {
        if (testDelayMillis <= 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }
}
