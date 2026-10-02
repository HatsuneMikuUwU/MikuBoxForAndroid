package com.miku.ray.dto.entities

data class RulesetItem(
    var remarks: String? = "",
    var ip: List<String>? = null,
    var domain: List<String>? = null,
    var process: List<String>? = null,
    var outboundTag: String = "",
    var port: String? = null,
    var network: String? = null,
    var protocol: List<String>? = null,
    var enabled: Boolean = true,
    var locked: Boolean? = false,

    var id: String = "",

    /**
     * The rule exactly as the selected profile writes it, for entries read out of
     * a profile rather than authored here.
     *
     * MikuRay's model describes a rule by its fields, which is enough to build one
     * for the core but not to reproduce it: a config rule's type carries meaning
     * the fields cannot (`DOMAIN-KEYWORD` and `DOMAIN-SUFFIX` look identical once
     * the payload is a list). Entries that came from a profile therefore carry the
     * original line and are passed through untouched; entries authored in the
     * routing screen have none and are built from the fields as before.
     */
    var rawRule: String? = null,
)
