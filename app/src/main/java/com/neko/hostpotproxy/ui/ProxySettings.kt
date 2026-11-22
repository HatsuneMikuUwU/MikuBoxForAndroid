package com.neko.hostpotproxy.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.HotspotProxyScreen
import top.nkbe.mikubox.ui.page.tools.HotspotProxyUiState
import java.net.Inet4Address
import java.net.NetworkInterface

class ProxySettings : ThemedActivity() {

    private var proxyControl: IProxyControl? = null
    private var uiState by mutableStateOf(HotspotProxyUiState())

    companion object {
        const val KEY_PREFS = "proxy_pref"
        const val KEY_ENABLE = "proxy_enable"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            proxyControl = IProxyControl.Stub.asInterface(service)
            uiState = uiState.copy(isBound = true)
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            proxyControl = null
            uiState = uiState.copy(isBound = false, isRunning = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshIpDisplay()

        setMikuContent {
            HotspotProxyScreen(
                state = uiState,
                onToggleProxy = { toggleProxyState() },
                onCopyIp = { copyToClipboard(uiState.localIp) },
                onBack = { finish() },
            )
        }

        bindService(Intent(this, ProxyService::class.java), connection, Context.BIND_AUTO_CREATE)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshIpDisplay()
    }

    private fun toggleProxyState() {
        val control = proxyControl
        if (control == null) {
            Toast.makeText(this, R.string.service_not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val targetStateStart = !uiState.isRunning
            val intent = Intent(this, ProxyService::class.java)

            if (targetStateStart) {
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                val success = control.start()
                if (success) {
                    Toast.makeText(this, R.string.proxy_started, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.proxy_start_failed, Toast.LENGTH_SHORT).show()
                    stopService(intent)
                }
            } else {
                control.stop()
                stopService(intent)
                Toast.makeText(this, R.string.proxy_stopped, Toast.LENGTH_SHORT).show()
            }

            getSharedPreferences(KEY_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLE, targetStateStart)
                .apply()

            updateStatus()
        } catch (e: RemoteException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        try {
            val running = proxyControl?.isRunning == true
            val port = proxyControl?.getPort() ?: 0
            uiState = uiState.copy(
                isRunning = running,
                port = port,
                localIp = getLocalIpAddress(),
            )
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun refreshIpDisplay() {
        uiState = uiState.copy(localIp = getLocalIpAddress())
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = getString(R.string.proxy_ip_label)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.ip_copied_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        try {
            unbindService(connection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun getLocalIpAddress(): String {
        var bestCandidate = "127.0.0.1"
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.contains("tun")) continue
                val isLikelyHotspot = name.contains("ap") || name.contains("rndis") || name.contains("wlan")
                val enumIp = intf.inetAddresses
                while (enumIp.hasMoreElements()) {
                    val inet = enumIp.nextElement()
                    if (!inet.isLoopbackAddress && inet is Inet4Address) {
                        val ip = inet.hostAddress ?: continue
                        if (isLikelyHotspot) return ip
                        bestCandidate = ip
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bestCandidate
    }
}
