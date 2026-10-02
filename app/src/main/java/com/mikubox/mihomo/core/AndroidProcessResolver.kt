package com.mikubox.mihomo.core

import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.Keep
import java.net.InetSocketAddress

/** Called from mihomo worker threads. Android 10+ exposes socket ownership to the active VPN. */
@Keep
object AndroidProcessResolver {
    @Keep @JvmStatic
    fun resolve(protocol: Int, source: String, sourcePort: Int, destination: String, destinationPort: Int, knownUid: Int): String {
        val context = MikuRayBridgeContext.application ?: return ""
        return runCatching {
            val uid = if (Build.VERSION.SDK_INT >= 29) {
                context.getSystemService(ConnectivityManager::class.java).getConnectionOwnerUid(
                    protocol, InetSocketAddress(source, sourcePort), InetSocketAddress(destination, destinationPort))
            } else knownUid
            if (uid <= 0) return ""
            val name = context.packageManager.getPackagesForUid(uid)?.sorted()?.firstOrNull() ?: return ""
            "$uid\n$name"
        }.getOrDefault("")
    }
}
