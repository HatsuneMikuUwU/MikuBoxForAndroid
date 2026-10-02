package com.miku.ray.dto

import com.google.gson.annotations.SerializedName

data class IPAPIInfo(
    var ip: String? = null,
    var clientIp: String? = null,
    var ip_addr: String? = null,
    var query: String? = null,
    var country: String? = null,
    var country_name: String? = null,
    var country_code: String? = null,
    var countryCode: String? = null,
    var location: LocationBean? = null,
    var isp: String? = null,
    var org: String? = null,
    var organization: String? = null,
    var asn_organization: String? = null,
    @SerializedName("as")
    var asOrg: String? = null,
    var asname: String? = null
) {
    data class LocationBean(
        var country_code: String? = null
    )
}
