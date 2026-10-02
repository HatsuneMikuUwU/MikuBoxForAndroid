package com.miku.ray.dto

data class UrlContentRequest(
    val url: String?,
    val timeout: Int = 15000,
    val httpPort: Int = 0,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val userAgent: String? = null,
    val requestHeaders: String? = null,
    val hwid: String? = null,
    /**
     * A probe whose failure is an expected outcome — the exit-IP lookup, which
     * runs unattended and repeatedly — rather than something a user asked for.
     * Its failures are one warning line naming the endpoint and the reason;
     * the full stack of OkHttp's route retries stays out of the export.
     */
    val quiet: Boolean = false
)
