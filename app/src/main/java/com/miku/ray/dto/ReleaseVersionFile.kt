package com.miku.ray.dto

import com.google.gson.annotations.SerializedName

/** The tiny version.json asset the release workflow attaches to every tag;
 *  its versionCode is what the updater compares against the installed one. */
data class ReleaseVersionFile(
    @SerializedName("versionCode")
    val versionCode: Long,
    @SerializedName("versionName")
    val versionName: String = ""
)
