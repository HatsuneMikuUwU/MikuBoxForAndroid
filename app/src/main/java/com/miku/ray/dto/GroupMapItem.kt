package com.miku.ray.dto

data class GroupMapItem(
    var id: String,
    var remarks: String,
    var serverCount: Int = 0,
    var icon: String? = null,
)
