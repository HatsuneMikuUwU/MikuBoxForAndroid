package com.mikubox.mihomo.core

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.Utils
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Android owns the routes, since mihomo receives an already established VPN FD. */
object VpnRoutes {
    fun custom(context: Context): String = context.getSharedPreferences("miku_android_routes", 0).getString("addresses", "").orEmpty()
    fun setCustom(context: Context, value: String) { context.getSharedPreferences("miku_android_routes", 0).edit().putString("addresses", value).apply() }
    fun parse(value: String): List<String> = value.split(Regex("[\\s,]+" )).filter(String::isNotBlank).onEach { cidr ->
        val ip = cidr.substringBefore('/')
        require('/' in cidr && Utils.isPureIpAddress(ip)) { "Invalid route: $cidr" }
        val prefix = cidr.substringAfter('/').toIntOrNull()
        require(prefix != null && prefix in 0..(if (':' in ip) 128 else 32)) { "Invalid route: $cidr" }
        val bytes = java.net.InetAddress.getByName(ip).address
        for (bit in prefix until bytes.size * 8) require((bytes[bit / 8].toInt() and (1 shl (7 - bit % 8))) == 0) { "Route must use a network address: $cidr" }
    }
    fun selected(context: Context, config: String, ipv6: Boolean): List<String> {
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
        val routes = when (mode) {
            "1" -> AndroidVpnSettings.publicRoutes() + listOf("2000::/3")
            "3" -> parse(custom(context)).also { require(it.isNotEmpty()) { "Custom VPN routes are empty" } }
            "0" -> {
                val root = Yaml(SafeConstructor(LoaderOptions())).load<Any>(config) as? Map<*, *>
                val tun = root?.get("tun") as? Map<*, *>
                val configured = listOf("route-address", "inet4-route-address", "inet6-route-address").flatMap { (tun?.get(it) as? List<*>)?.map(Any?::toString).orEmpty() }
                if (configured.isEmpty()) listOf("0.0.0.0/0", "::/0") else parse(configured.joinToString("\n"))
            }
            else -> listOf("0.0.0.0/0", "::/0")
        }
        return routes.distinct().filter { ipv6 || ':' !in it }
    }
}
