package moe.matsuri.nb4a

import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.ServiceNotification
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import libbox.*
import moe.matsuri.nb4a.net.LocalResolverImpl
import java.net.InetSocketAddress
import java.net.NetworkInterface as JavaNetworkInterface

class NativeInterface(
    private val context: Context
) : PlatformInterface, CommandServerHandler {

    companion object {
        private var instance: NativeInterface? = null
        fun getInstance(ctx: Context): NativeInterface {
            return instance ?: NativeInterface(ctx).also { instance = it }
        }
    }

    // ===== PlatformInterface implementation =====

    override fun localDNSTransport(): LocalDNSTransport {
        return LocalResolverImpl
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean {
        return true
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        DataStore.vpnService?.protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (DataStore.vpnService == null) {
            throw Exception("no VpnService")
        }
        return DataStore.vpnService!!.startVpn(options)
    }

    override fun useProcFS(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner? {
        val uid = SagerNet.connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        if (uid < 0) return null
        val result = ConnectionOwner()
        result.userId = uid
        result.userName = ""
        result.processPath = ""
        val packageName = try {
            PackageCache.uidMap[uid]?.firstOrNull()
        } catch (_: Exception) { null }
        if (packageName != null) {
            result.setAndroidPackageNames(object : StringIterator {
                private var done = false
                override fun len(): Int = 1
                override fun next(): String? {
                    return if (!done) { done = true; packageName } else null
                }
                override fun hasNext(): Boolean = !done
            })
        }
        return result
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        runOnDefaultDispatcher {
            DefaultNetworkListener.start(listener) { network ->
                network?.let { updateDefaultInterface(listener, it) }
            }
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        runOnDefaultDispatcher {
            DefaultNetworkListener.stop(listener)
        }
    }

    private fun updateDefaultInterface(listener: InterfaceUpdateListener, network: Network) {
        val link = SagerNet.connectivity.getLinkProperties(network) ?: return
        val interfaceName = link.interfaceName ?: return
        val capabilities = SagerNet.connectivity.getNetworkCapabilities(network)
        listener.updateDefaultInterface(
            interfaceName,
            JavaNetworkInterface.getByName(interfaceName)?.index ?: 0,
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            false,
        )
    }

    override fun getInterfaces(): NetworkInterfaceIterator? {
        val interfaces = JavaNetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { networkInterface ->
            NetworkInterface().apply {
                index = networkInterface.index
                mtu = runCatching { networkInterface.mtu }.getOrDefault(0)
                name = networkInterface.name
                addresses = stringIterator(networkInterface.inetAddresses.toList().map { it.hostAddress ?: "" })
                flags = 0
                type = 0
                dnsServer = stringIterator(emptyList())
                metered = false
            }
        }
        return object : NetworkInterfaceIterator {
            private var index = 0
            override fun hasNext(): Boolean = index < interfaces.size
            override fun next(): NetworkInterface? = interfaces.getOrNull(index++)
        }
    }

    private fun stringIterator(values: List<String>) = object : StringIterator {
        private var index = 0
        override fun len(): Int = values.size
        override fun hasNext(): Boolean = index < values.size
        override fun next(): String? = values.getOrNull(index++)
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? {
        val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        return Libbox.newWIFIState(
            connectionInfo.ssid ?: "",
            connectionInfo.bssid ?: ""
        )
    }

    override fun clearDNSCache() {
        // No-op on Android
    }

    override fun sendNotification(notification: Notification?): Unit {
        // Notifications are handled by ServiceNotification on Android side
    }

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {
        // Not used on Android
    }

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {
        // Not used on Android
    }

    override fun registerMyInterface(name: String?) {
        // Not used on Android
    }

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        throw Exception("shell not available")
    }

    override fun openShellSession(
        user: PlatformUser?,
        command: String?,
        environ: StringIterator?,
        term: String?,
        rows: Int,
        cols: Int
    ): ShellSession? {
        throw Exception("shell not available")
    }

    override fun lookupUser(username: String?): PlatformUser? {
        throw Exception("user lookup not available")
    }

    override fun lookupSFTPServer(): String? {
        throw Exception("sftp not available")
    }

    override fun readSystemSSHHostKey(): String? {
        throw Exception("ssh host key not available")
    }

    override fun tailscaleHostname(): String = ""

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession? {
        throw Exception("bridge not available")
    }

    // ===== CommandServerHandler implementation =====

    override fun serviceStop() {
        SagerNet.stopService()
    }

    override fun serviceReload() {
        SagerNet.reloadService()
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? {
        return SystemProxyStatus().apply {
            enabled = false
            available = false
        }
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        // Not implemented for Android
    }

    override fun triggerNativeCrash() {
        throw RuntimeException("native crash triggered")
    }

    override fun writeDebugMessage(message: String?) {
        Logs.d(message ?: "")
    }

    override fun connectSSHAgent(): Int {
        throw Exception("ssh agent not available")
    }
}
