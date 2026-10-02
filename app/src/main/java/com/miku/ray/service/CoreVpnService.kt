package com.miku.ray.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Network
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.StrictMode
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.LOOPBACK
import com.miku.ray.BuildConfig
import com.miku.ray.contracts.ServiceControl
import com.miku.ray.contracts.Tun2SocksControl
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.NotificationManager
import com.miku.ray.handler.TrafficController
import com.miku.ray.handler.SettingsManager
import com.miku.ray.root.RootLanSharing
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.MyContextWrapper
import com.miku.ray.util.SoundPlayer
import com.miku.ray.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("VpnServicePolicy")
class CoreVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val isStartingLock = AtomicBoolean(false)

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service created")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        CoreServiceManager.serviceControl = this
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "StartCore-VPN: Failed to raise thread priority", e)
        }
    }

    override fun onRevoke() {
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: Permission revoked")
        stopAllService()
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service destroyed")
        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopCoreLoop()
        }
        CoreServiceManager.clearServiceControl(this)

        if (isRunning) {
            try {
                if (::mInterface.isInitialized) {
                    mInterface.close()
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: VPN interface closed in onDestroy")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to close interface in onDestroy", e)
            }
        }

        unlockStart()
        NotificationManager.cancelNotification()
        TrafficController.stop()
        serviceScope.cancel()

        MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_NOT_RUNNING, "")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        val isSystemVpnStart = intent == null || intent.action == SERVICE_INTERFACE
        if (isSystemVpnStart) {
            unlockStart()
        }
        if (!tryLockStart()) {
            LogUtil.w(AppConfig.TAG, "StartCore-VPN: Start already in progress")
            return START_NOT_STICKY
        }
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service command received, systemVpnStart=$isSystemVpnStart")
        TrafficController.start()

        serviceScope.launch {
            val setupFailure = try {
                if (setupVpnService()) null else "VPN setup failed"
            } catch (e: Exception) {
                val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: $message", e)
                message
            }
            if (setupFailure != null) {
                withContext(Dispatchers.Main) {
                    unlockStart()
                    CoreServiceManager.reportStartFailure(this@CoreVpnService, setupFailure)

                    stopSelf()
                }
            } else {
                startService()
                unlockStart()
            }
        }
        return START_STICKY
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        if (!::mInterface.isInitialized) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Interface not initialized")
            return
        }
        if (!CoreServiceManager.startCoreLoop(mInterface)) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to start core loop")
            stopAllService()
            return
        }
    }

    override fun stopService() {
        stopAllService(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    override fun setUnderlyingNetworks(networks: Array<Network>?): Boolean {
        return super<VpnService>.setUnderlyingNetworks(networks)
    }

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    private fun setupVpnService(): Boolean {
        val prepare = prepare(this)
        if (prepare != null) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Permission not granted")
            return false
        }

        if (configureVpnService() != true) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Configuration failed")
            return false
        }

        runTun2socks()
        return true
    }

    private fun configureVpnService(): Boolean {
        val builder = Builder()

        configureNetworkSettings(builder)

        configurePerAppProxy(builder)

        try {
            if (::mInterface.isInitialized) {
                mInterface.close()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Failed to close old interface", e)
        }

        configurePlatformFeatures(builder)

        try {
            mInterface = builder.establish()!!
            isRunning = true
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_KEEP_AWAKE, false)) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "${AppConfig.TAG}:VpnWakeLock"
                ).also { it.acquire() }
                LogUtil.i(AppConfig.TAG, "StartCore-VPN: WakeLock acquired")
            }
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
                SoundPlayer.playConnect(this)
            }
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopAllService()
        }
        return false
    }

    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 18)
            } else {
                builder.addRoute("::", 0)
            }
        }

        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }

    }

    private fun configurePlatformFeatures(builder: Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    builder.addDisallowedApplication(it)
                } else {
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to configure app", e)
            }
        }
    }

    private fun runTun2socks() {

        tun2SocksService?.stopTun2Socks()
        if (SettingsManager.isUsingHevTun()) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = null
        }

        tun2SocksService?.startTun2Socks()
        RootLanSharing.startClientSharing(this)
    }

    private fun stopAllService(isForced: Boolean = true) {
        unlockStart()
        isRunning = false
        RootLanSharing.stopClientSharing(this)
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                LogUtil.i(AppConfig.TAG, "StartCore-VPN: WakeLock released")
            }
        }
        wakeLock = null
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
            SoundPlayer.playDisconnect(this)
        }

        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        CoreServiceManager.stopCoreLoop()

        if (isForced) {
            stopSelf()

            try {
                if (::mInterface.isInitialized) {
                    mInterface.close()
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: VPN interface closed")
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to close interface", e)
            }
        }
    }

    fun tryLockStart(): Boolean {
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: tryLockStart: ${isStartingLock.get()}")
        return isStartingLock.compareAndSet(false, true)
    }

    fun unlockStart() {
        isStartingLock.set(false)
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: unlockStart")
    }
}
