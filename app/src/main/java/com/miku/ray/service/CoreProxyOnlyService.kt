package com.miku.ray.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.miku.ray.AppConfig
import com.miku.ray.contracts.ServiceControl
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.handler.NotificationManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MyContextWrapper

class CoreProxyOnlyService : Service(), ServiceControl {
    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service created")
        CoreServiceManager.serviceControl = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service command received")

        if (CoreServiceManager.isRunning()) {
            LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Core is already running")
            return START_STICKY
        }

        if (!CoreServiceManager.startCoreLoop(null)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Proxy: Failed to start core loop")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        CoreServiceManager.stopCoreLoop()
        CoreServiceManager.clearServiceControl(this)
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
    }

    override fun stopService() {
        stopSelf()
    }

    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
