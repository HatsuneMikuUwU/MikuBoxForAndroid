package com.mikubox.mihomo.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep

/** Underlying-network DNS and socket ownership for the embedded Android core. */
@Keep
object AndroidNetworkBridge {
    @Volatile private var owner: android.app.Service? = null
    @Volatile private var vpn: VpnService? = null
    @Volatile private var underlying: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var manager: ConnectivityManager? = null
    private var lastDns = emptyList<String>()

    @Synchronized fun start(service: android.app.Service) {
        stop()
        owner = service
        vpn = service as? VpnService
        val cm = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager = cm
        fun update(network: Network?, properties: LinkProperties? = null) = synchronized(this) {
            if (owner !== service) return@synchronized
            if (network != underlying) {
                underlying = network
                vpn?.setUnderlyingNetworks(network?.let { arrayOf(it) } ?: emptyArray())
                com.miku.ray.util.LogUtil.i(message = "VPN underlying network: $network")
            }
            val servers = (properties ?: network?.let(cm::getLinkProperties))?.dnsServers.orEmpty()
                .mapNotNull { it.hostAddress }.distinct()
            if (servers != lastDns) {
                MihomoCore.updateSystemDns(servers)
                lastDns = servers
            }
        }
        fun fallback(lost: Network? = null): Network? {
            val candidates = cm.allNetworks.filter { it != lost }.mapNotNull { network ->
                cm.getNetworkCapabilities(network)?.let { network to it }
            }
            return chooseUnderlyingNetwork(candidates, cm.activeNetwork, underlying)
        }
        // Before the callback arrives, retain the physical default. The VPN itself
        // becomes activeNetwork after establish(), so it must never enter this list.
        update(fallback())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        callback = if (android.os.Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { update(network) }
                override fun onLost(network: Network) {
                    if (underlying == network) update(null)
                }
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                    if (underlying == network) update(network, properties)
                }
            }.also {
                cm.registerBestMatchingNetworkCallback(request, it, android.os.Handler(android.os.Looper.getMainLooper()))
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { update(fallback()) }
                override fun onLost(network: Network) { update(fallback(network)) }
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { update(fallback()) }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { update(fallback()) }
            }.also { cm.registerNetworkCallback(request, it) }
        }
    }

    @Synchronized fun stop(service: android.app.Service? = null) {
        if (service != null && owner !== service) return
        callback?.let { cb -> runCatching { manager?.unregisterNetworkCallback(cb) } }
        callback = null
        manager = null
        underlying = null
        owner = null
        vpn = null
        lastDns = emptyList()
    }

    @Keep @JvmStatic fun protect(fd: Int): Boolean {
        val service = vpn
        if (service != null && !service.protect(fd)) return false
        val network = underlying ?: return true
        return runCatching {
            // bindSocket(FileDescriptor) does not take ownership. Borrow a dup
            // so neither Java nor a network switch can close Go's descriptor.
            ParcelFileDescriptor.fromFd(fd).use { network.bindSocket(it.fileDescriptor) }
            true
        }.getOrDefault(false)
    }
}

/** Pre-callback / pre-Android 12 fallback; network enumeration order is not priority. */
internal fun chooseUnderlyingNetwork(
    candidates: List<Pair<Network, NetworkCapabilities>>,
    active: Network?, previous: Network?,
): Network? {
    val physical = candidates.filter { (_, caps) ->
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }
    physical.firstOrNull { it.first == active }?.let { return it.first }
    return physical.maxByOrNull { (network, caps) ->
        (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0) +
            (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) 30 else 0) +
            (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 20 else 0) +
            (if (network == previous) 1 else 0)
    }?.first
}
