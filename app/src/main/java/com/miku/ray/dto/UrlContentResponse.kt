package com.miku.ray.dto

data class UrlContentResponse(
    val content: String,
    val headers: Map<String, String>,
)
