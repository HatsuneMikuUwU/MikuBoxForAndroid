package com.mikubox.mihomo.core

import android.content.Context

/** Automatic connections are opt-in and only use the currently selected profile. */
object OnDemandSettings {
    const val STORE = "miku_on_demand"
    enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER }
    data class NetworkState(val transport: Transport, val ssid: String? = null)
    enum class Decision { CONNECT, DISCONNECT, WAIT_FOR_SSID }
    fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).apply() }
    fun excluded(context: Context) = parseSsids(prefs(context).getString("excluded", "").orEmpty())
    fun parseSsids(text: String): Set<String> = text.lines().map(String::trim).filter(String::isNotEmpty).toSet().also {
        require(it.all { ssid -> ssid.toByteArray(Charsets.UTF_8).size <= 32 }) { "SSID exceeds 32 bytes" }
    }
    fun decide(state: NetworkState?, allowed: Set<Transport>, excluded: Set<String>): Decision {
        if (state == null || state.transport !in allowed) return Decision.DISCONNECT
        if (state.transport == Transport.WIFI && excluded.isNotEmpty()) {
            // Missing permission must never accidentally connect on a trusted SSID.
            if (state.ssid.isNullOrBlank() || state.ssid == "<unknown ssid>") return Decision.WAIT_FOR_SSID
            if (state.ssid in excluded) return Decision.DISCONNECT
        }
        return Decision.CONNECT
    }
    fun allowed(context: Context): Set<Transport> = Transport.entries.filter {
        prefs(context).getBoolean(it.name, true)
    }.toSet()
}
