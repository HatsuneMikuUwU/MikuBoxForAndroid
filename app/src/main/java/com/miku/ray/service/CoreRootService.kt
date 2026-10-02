package com.miku.ray.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.miku.ray.AppConfig
import com.miku.ray.contracts.ServiceControl
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.NotificationManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.TrafficController
import com.miku.ray.root.RootProxyManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MyContextWrapper
import com.miku.ray.util.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CoreRootService : Service(), ServiceControl {

    private var isRunning = false
    private var setupJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service command received")

        if (CoreServiceManager.isRunning()) {
            LogUtil.i(AppConfig.TAG, "StartCore-Root: Core is already running")
            return START_STICKY
        }

        NotificationManager.showNotification(null)
        TrafficController.start()

        setupJob?.cancel()
        setupJob = serviceScope.launch {
            if (!CoreServiceManager.startCoreLoop(null)) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: Failed to start core loop")
                stopService()
                return@launch
            }

            isRunning = true

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
                SoundPlayer.playConnect(this@CoreRootService)
            }

            try {
                RootProxyManager.start(this@CoreRootService)
                LogUtil.i(AppConfig.TAG, "StartCore-Root: iptables/tun/hev setup complete")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: setup failed", e)
                stopService()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service destroyed")
        runBlocking {
            setupJob?.cancelAndJoin()
        }
        setupJob = null
        RootProxyManager.stopFull(applicationContext)
        CoreServiceManager.stopCoreLoop()
        CoreServiceManager.clearServiceControl(this)
        serviceScope.cancel()
    }

    override fun getService(): Service = this

    override fun startService() {
    }

    override fun stopService() {
        stopAllService()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    private fun stopAllService(isForced: Boolean = true) {
        isRunning = false

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SOUND_ON_CONNECT, true)) {
            SoundPlayer.playDisconnect(this)
        }

        setupJob?.cancel()
        setupJob = null

        if (isForced) {
            stopSelf()
        }
    }
}
