package com.miku.ray.core

object PolicyGroupTrafficRegistry {
    private val tagToGuid = mutableMapOf<String, String>()

    @Synchronized
    fun clear() {
        tagToGuid.clear()
    }

    @Synchronized
    fun register(tag: String, guid: String) {
        if (tag.isBlank() || guid.isBlank()) return
        tagToGuid[tag] = guid
    }

    @Synchronized
    fun guidForTag(tag: String): String? = tagToGuid[tag]
}
