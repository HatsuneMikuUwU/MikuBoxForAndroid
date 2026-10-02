package com.miku.ray.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import com.miku.ray.extension.delay
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: () -> Unit,
) {
    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    private var upstream: Network? = null
    private var handoverJob: Job? = null
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val request by lazy {
        NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!registered) return
            val previous = upstream
            upstream = network
            onUnderlyingNetworksChanged(arrayOf(network))
            if (previous != null && previous != network) {
                scheduleHandover(network)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (registered) onUnderlyingNetworksChanged(arrayOf(network))
        }

        override fun onLost(network: Network) {
            if (registered && upstream == network) {
                upstream = null
                onUnderlyingNetworksChanged(null)
            }
        }
    }

    fun register() {
        if (registered) return
        try {
            connectivity.requestNetwork(request, callback)
            registered = true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to request network", e)
        }
    }

    fun unregister() {
        handoverJob?.cancel()
        handoverJob = null
        upstream = null
        if (!registered) return
        registered = false
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to unregister callback", e)
        }
    }

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream is now $network")
        handoverJob?.cancel()
        handoverJob = scope.launch {
            try {
                delay(HANDOVER_DEBOUNCE_MS)
                onHandover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to handle upstream change", e)
            }
        }
    }
}
