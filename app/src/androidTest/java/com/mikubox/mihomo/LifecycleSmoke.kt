package com.mikubox.mihomo

import android.app.Instrumentation
import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import com.miku.ray.AppConfig
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.handler.MmkvManager
import com.mikubox.mihomo.core.MihomoCoreSettings
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.service.*
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Tests the network boundary during preparation, cancellation and a completed session. */
internal object LifecycleSmoke {
    fun run(test: Instrumentation): Bundle {
        val context = test.targetContext
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun waitUntil(message: String, condition: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!condition() && System.nanoTime() < end) Thread.sleep(50)
            check(condition()) { message }
        }
        fun stopped() = ConnectionStatus.phase.value == ConnectionStatus.Phase.DISCONNECTED
        test.runOnMainSync { VpnController.disconnect(context) }
        waitUntil("previous tunnel didn't stop", ::stopped)
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "2")
        MihomoCoreSettings.setMode(context, MihomoCoreSettings.ProxyMode.FOLLOW)
        val physical = cm.getNetworkCapabilities(cm.activeNetwork)
        val wasWifi = physical?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        fun hasVpn() = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        fun echo() {
            val completed = CountDownLatch(1)
            var result: Bundle? = null
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) { result = intent.extras; completed.countDown() }
            }
            androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                IntentFilter("com.mikubox.mihomo.QA_NETWORK_RESULT"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            try {
                test.runOnMainSync { context.startActivity(Intent().setClassName("com.mikubox.mihomo.test", "com.mikubox.mihomo.NetworkProbeActivity")
                    .putExtra("echo_only", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                check(completed.await(30, TimeUnit.SECONDS)) { "External UID echo timed out" }
                for (size in listOf(4096, 65536, 393216)) check(result?.getString("direct_$size") == "PASS") { "Echo failed: $result" }
            } finally { context.unregisterReceiver(receiver) }
        }
        // One successful preparation, then one cancelled while its provider is held.
        repeat(2) { attempt ->
            val accepted = CountDownLatch(1)
            val release = CountDownLatch(1)
            ServerSocket(0).use { server ->
                val worker = Thread {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        accepted.countDown()
                        release.await(30, TimeUnit.SECONDS)
                        val body = "proxies: [{name: local, type: direct}]\n"
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                    }
                }.apply { isDaemon = true; start() }
                val profile = MihomoProfileStore.create(context, "Lifecycle $attempt", """
                    mixed-port: 10808
                    dns: {enable: true, nameserver: [system]}
                    proxy-providers:
                      slow: {type: http, url: 'http://127.0.0.1:${server.localPort}/proxies', path: './providers/lifecycle-${System.nanoTime()}.yaml'}
                    proxy-groups:
                      - {name: QA, type: select, use: [slow]}
                    rules: ['MATCH,DIRECT']
                """.trimIndent())
                MihomoProfileStore.select(context, profile.id)
                try {
                    test.runOnMainSync { VpnController.connect(context) }
                    check(accepted.await(15, TimeUnit.SECONDS)) { "Slow provider not requested" }
                    check(!hasVpn()) { "VPN captured traffic while its provider was still blocked" }
                    echo()
                    if (attempt == 1) test.runOnMainSync { CoreServiceManager.serviceControl!!.stopService() }
                    release.countDown()
                    worker.join(5000)
                    if (attempt == 0) {
                        waitUntil("VPN never became ready") { VpnController.isRunning }
                        if (wasWifi) waitUntil("VPN moved from Wi-Fi to cellular") {
                            cm.getNetworkCapabilities(cm.activeNetwork)?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } == true
                        }
                        echo()
                        test.runOnMainSync { CoreServiceManager.serviceControl!!.stopService() }
                    }
                    waitUntil("cancelled or stopped core did not finish", ::stopped)
                    // ConnectivityService publishes interface removal asynchronously.
                    waitUntil("stopped VPN interface was not released") { !hasVpn() }
                    check(!TunnelGuard.isExpected(context)) { "UI service stop retained recovery intent" }
                    test.runOnMainSync { TunnelGuardReceiver().onReceive(context, Intent()) }
                    Thread.sleep(1000)
                    check(!hasVpn() && !VpnController.isRunning) { "Late startup or guard resurrected a stopped VPN" }
                    ServerSocket(10808).use { }
                    java.net.DatagramSocket(10808).use { }
                } finally {
                    release.countDown()
                    test.runOnMainSync { VpnController.disconnect(context) }
                }
            }
        }
        return Bundle().apply { putString("stream", "PASS: external-UID traffic while provider is blocked; no premature TUN; Wi-Fi preserved; cancelled preparation does not reconnect; UI stop disarms recovery; TCP/UDP ports released") }
    }
}
