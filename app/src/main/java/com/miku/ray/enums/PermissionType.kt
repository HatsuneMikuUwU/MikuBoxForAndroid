package com.miku.ray.enums

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

enum class PermissionType {
    CAMERA {
        override fun getPermission(): String = Manifest.permission.CAMERA
    },

    POST_NOTIFICATIONS {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun getPermission(): String = Manifest.permission.POST_NOTIFICATIONS
    },

    ACCESS_LOCAL_NETWORK {
        // The platform constants for this one (the codename above API 37 and the
        // permission itself) do not exist in the SDK this app compiles against,
        // so the name is spelled out and the version gate lives at the call site.
        override fun getPermission(): String = "android.permission.ACCESS_LOCAL_NETWORK"
    },

    LOCATION {
        override fun getPermission(): String = Manifest.permission.ACCESS_FINE_LOCATION
        override fun getPermissions(): Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    };

    abstract fun getPermission(): String

    open fun getPermissions(): Array<String> = arrayOf(getPermission())

    fun getLabel(): String {
        return when (this) {
            CAMERA -> "Camera"
            POST_NOTIFICATIONS -> "Notification"
            ACCESS_LOCAL_NETWORK -> "Local Network"
            LOCATION -> "Location"
        }
    }
}
