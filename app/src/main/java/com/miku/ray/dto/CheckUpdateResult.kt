package com.miku.ray.dto

data class CheckUpdateResult(
    val hasUpdate: Boolean,
    val latestVersion: String? = null,
    val releaseNotes: String? = null,
    val releaseUrl: String? = null,
    val downloadUrl: String? = null,
    val error: String? = null,
    val isPreRelease: Boolean = false
)
