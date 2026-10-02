package com.mikubox.mihomo.core

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import com.miku.ray.MikuDiagnostics
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import com.miku.ray.util.HttpUtil
import com.miku.ray.dto.UrlContentRequest
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Runs only in dedicated processes: tests must never replace the user's VPN core. */
object NativeProfileProbe : MikuDiagnostics.Impl {
    override fun attach(service: android.app.Service) {
        check(isProbeProcess(service))
        AndroidNetworkBridge.start(service)
    }
    override fun detach(service: android.app.Service) { AndroidNetworkBridge.stop(service) }
    private val lock = ReentrantLock()
    private val context get() = checkNotNull(MikuRayBridgeContext.application)

    fun isProbeProcess(context: Context): Boolean = processName(context).let {
        it.endsWith(":probe_delay") || it.endsWith(":probe_country")
    }

    private fun processName(context: Context): String =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName.orEmpty()

    internal fun prepare(config: String, port: Int, preferredExit: String? = null): Pair<String, String> {
        val raw = Yaml(SafeConstructor(LoaderOptions())).load<MutableMap<String, Any?>>(config)
            ?: error("Empty profile")
        val groups = (raw["proxy-groups"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val nodes = (raw["proxies"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val target = (preferredExit ?: groups.firstOrNull { it["name"] != "GLOBAL" }?.get("name")
            ?: nodes.firstOrNull()?.get("name") ?: groups.firstOrNull()?.get("name") ?: "DIRECT").toString()
        if (target != "GLOBAL") {
            raw["proxy-groups"] = groups.filter { it["name"] != "GLOBAL" } + mapOf(
                "name" to "GLOBAL", "type" to "select", "proxies" to listOf(target))
        }
        raw["mixed-port"] = port
        raw["port"] = 0; raw["socks-port"] = 0; raw["redir-port"] = 0; raw["tproxy-port"] = 0
        raw["allow-lan"] = false; raw["bind-address"] = "127.0.0.1"
        raw["authentication"] = emptyList<String>()
        raw["mode"] = "global"
        raw["rules"] = listOf("MATCH,GLOBAL")
        listOf("listeners", "rule-providers", "sub-rules", "external-controller", "external-controller-tls",
            "external-controller-unix", "external-ui", "external-ui-url", "tun", "tuic-server", "ntp").forEach(raw::remove)
        raw["profile"] = mapOf("store-selected" to false, "store-fake-ip" to false)
        // Provider refreshes/health checks must not outlive this one-shot probe.
        (raw["proxy-providers"] as? Map<*, *>)?.values?.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            (value as? MutableMap<String, Any?>)?.apply {
                this["interval"] = 0
                this["health-check"] = mapOf("enable" to false)
            }
        }
        return Yaml().dump(raw) to target
    }

    private fun <T> probe(config: String, operation: (Int, String) -> T): T = lock.withLock {
        check(isProbeProcess(context)) { "Profile probes require an isolated process" }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        } ?: error("No underlying network")
        check(cm.bindProcessToNetwork(network)) { "Cannot bind test network" }
        MihomoCore.updateSystemDns(cm.getLinkProperties(network)?.dnsServers.orEmpty().mapNotNull { it.hostAddress })
        val port = ServerSocket(0).use { it.localPort }
        val profile = com.mikubox.mihomo.profile.MihomoProfileStore.profiles(context).firstOrNull { it.config == config }
        val selected = context.getSharedPreferences("mihomo_routing_choices", 0).getString("exit:${profile?.id}", null)
        val (prepared, target) = prepare(config, port, selected)
        try {
            val suffix = if (processName(context).endsWith(":probe_country")) "country" else "delay"
            MihomoCore.start(context, prepared, -1, homeName = "mihomo-probe-$suffix").getOrThrow()
            check(target == "GLOBAL" || MihomoCore.selectProxy("GLOBAL", target)) { "Cannot select test outbound" }
            operation(port, target)
        } finally {
            MihomoCore.stop()
            cm.bindProcessToNetwork(null)
        }
    }

    override fun delay(config: String, url: String): Long = probe(config) { _, target ->
        MihomoCore.delay(target, url, 5000).toLong()
    }

    override fun country(config: String): String? = probe(config) { port, _ ->
        SpeedtestManager.getCountryCodeThroughProxy(port, SettingsManager.getCountryCodeTestTimeout())
    }

    override fun tcp(config: String): Long = probe(config) { _, target ->
        val endpoint = MihomoCore.proxyEndpoint(target) ?: return@probe -1L
        SpeedtestManager.socketConnectTime(endpoint.first, endpoint.second, 2500)
    }
}
