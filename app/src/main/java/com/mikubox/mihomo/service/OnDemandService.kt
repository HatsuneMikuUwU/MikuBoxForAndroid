package com.mikubox.mihomo.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mikubox.mihomo.R
import com.mikubox.mihomo.core.OnDemandSettings
import com.mikubox.mihomo.core.OnDemandSettings.Transport
import com.mikubox.mihomo.core.OnDemandSettings.NetworkState
import com.mikubox.mihomo.core.OnDemandSettings.Decision
import com.mikubox.mihomo.profile.MihomoProfileStore

/** Remains foreground while the tunnel is paused, so leaving trusted Wi-Fi can reconnect. */
class OnDemandService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val networks = mutableMapOf<Network, NetworkCapabilities>()
    private lateinit var manager: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastKey: String? = null
    private var destroyed = false
    private val evaluate = Runnable { evaluateNetwork() }
    private val preferences = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        lastKey = null
        schedule()
    }

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        destroyed = false
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.mihomo_on_demand), NotificationManager.IMPORTANCE_LOW))
        val notification = notification(getString(R.string.mihomo_on_demand_waiting))
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        manager = getSystemService(ConnectivityManager::class.java)
        OnDemandSettings.prefs(this).registerOnSharedPreferenceChangeListener(preferences)
        // Only physical networks; the VPN's own callbacks must not trigger a reconnect loop.
        callback = if (Build.VERSION.SDK_INT >= 31) object : ConnectivityManager.NetworkCallback(
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
        ) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                handler.post { if (!destroyed) { networks[network] = caps; schedule() } }
            }
            override fun onLost(network: Network) {
                handler.post { if (!destroyed) { networks.remove(network); schedule() } }
            }
        } else object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                handler.post { if (!destroyed) { networks[network] = caps; schedule() } }
            }
            override fun onLost(network: Network) {
                handler.post { if (!destroyed) { networks.remove(network); schedule() } }
            }
        }
        manager.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback!!)
        schedule()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            OnDemandSettings.setEnabled(this, false)
            MikuVpnService.stop(this)
        }
        if (!OnDemandSettings.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        schedule()
        return START_STICKY
    }
    private fun schedule() {
        handler.removeCallbacks(evaluate)
        handler.postDelayed(evaluate, 800)
    }
    @Suppress("DEPRECATION")
    private fun evaluateNetwork() {
        if (!OnDemandSettings.enabled(this)) { stopSelf(); return }
        // Prefer validated Wi-Fi over retained mobile data when activeNetwork is the VPN.
        val entry = networks.entries.sortedByDescending { (network, caps) ->
            (if (network == manager.activeNetwork) 100 else 0) +
                (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 20 else 0) +
                (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 2 else 0)
        }.firstOrNull()
        val state = entry?.value?.let { caps ->
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
                else -> Transport.OTHER
            }
            val ssid = if (transport == Transport.WIFI) runCatching {
                val info = if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null
                (info?.ssid?.takeUnless { it.removeSurrounding("\"") == "<unknown ssid>" } ?: (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo?.ssid)
                    ?.removeSurrounding("\"")
            }.getOrNull() else null
            NetworkState(transport, ssid)
        }
        val decision = OnDemandSettings.decide(state, OnDemandSettings.allowed(this), OnDemandSettings.excluded(this))
        val key = "${entry?.key}:$state:$decision"
        if (lastKey == key) return // No retry storm after a failed connection or a manual stop.
        if (ConnectionStatus.phase.value == ConnectionStatus.Phase.DISCONNECTING) { schedule(); return }
        lastKey = key
        var message = when (decision) {
            Decision.CONNECT -> R.string.mihomo_on_demand_connecting
            Decision.DISCONNECT -> R.string.mihomo_on_demand_paused
            Decision.WAIT_FOR_SSID -> R.string.mihomo_on_demand_ssid_missing
        }
        if (decision == Decision.CONNECT) {
            if (VpnService.prepare(this) != null || MihomoProfileStore.selected(this) == null) {
                message = R.string.mihomo_on_demand_setup
            } else if (ConnectionStatus.phase.value == ConnectionStatus.Phase.DISCONNECTED) {
                runCatching { MikuVpnService.start(this) }.onFailure { message = R.string.mihomo_on_demand_setup }
            }
        } else if (ConnectionStatus.phase.value != ConnectionStatus.Phase.DISCONNECTED) {
            MikuVpnService.stop(this)
        }
        getSystemService(NotificationManager::class.java).notify(ID, notification(getString(message)))
    }
    private fun notification(message: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)!!
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.miku.ray.R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.mihomo_on_demand)).setContentText(message)
            .setContentIntent(PendingIntent.getActivity(this, ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0, getString(R.string.mihomo_on_demand_stop), PendingIntent.getService(this, ID,
                Intent(this, OnDemandService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setSilent(true).build()
    }
    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        OnDemandSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(preferences)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "on_demand"
        private const val ID = 1104
        private const val STOP = "on_demand.stop"
        fun refresh(context: Context) {
            if (OnDemandSettings.enabled(context)) ContextCompat.startForegroundService(context, Intent(context, OnDemandService::class.java))
            else context.stopService(Intent(context, OnDemandService::class.java))
        }
    }
}
