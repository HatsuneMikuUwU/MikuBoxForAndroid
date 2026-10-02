package com.miku.ray.util

import android.content.Context
import android.content.pm.PackageManager
import com.miku.ray.AppConfig
import java.util.concurrent.ConcurrentHashMap

object PackageUidResolver {

    private val packageUidCache = ConcurrentHashMap<String, String>()

    val packageUidMap: Map<String, String>
    get() = packageUidCache

    fun packageNamesToUids(context: Context, packageNames: List<String>): List<String> {
        return packageNames.mapNotNull { pkg ->
            packageUidCache[pkg] ?: resolveUid(context, pkg)?.also { uid ->
                packageUidCache[pkg] = uid
            }
        }
    }

    fun uidsToPackageNames(uids: List<String>): List<String> {
        if (uids.isEmpty()) return emptyList()
        return packageUidCache.filterValues { it in uids }.keys.toList()
    }

    fun uidToPackageName(uid: String): String? {
        return packageUidCache.entries.firstOrNull { it.value == uid }?.key
    }

    private fun resolveUid(context: Context, packageName: String): String? {
        if (packageName == AppConfig.UNIDENTIFIED_PACKAGE) {
            val uid = "-1"
            LogUtil.d(AppConfig.TAG, "Special package: $packageName -> UID: $uid")
            return uid
        }

        return try {
            val uid = context.packageManager.getPackageUid(packageName, 0).toString()
            LogUtil.d(AppConfig.TAG, "Package: $packageName -> UID: $uid")
            uid
        } catch (_: PackageManager.NameNotFoundException) {
            LogUtil.w(AppConfig.TAG, "Package not found: $packageName")
            null
        }
    }
}
