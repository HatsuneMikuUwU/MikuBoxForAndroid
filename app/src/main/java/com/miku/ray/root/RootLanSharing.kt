package com.miku.ray.root

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RootLanSharing {

    private var lanSharingStarted = false
    private var lanShareJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun startClientSharing(context: Context): Boolean {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING) || !RootManager.cachedRoot()) {
            return true
        }
        if (lanShareJob?.isActive == true) return false

        val appContext = context.applicationContext
        lanSharingStarted = true
        lanShareJob = scope.launch {
            runCatching { RootProxyManager.startClientSharing(appContext) }
            .onFailure { lanSharingStarted = false }
        }
        return true
    }

    @Synchronized
    fun stopClientSharing(context: Context) {
        if (!lanSharingStarted && lanShareJob == null) return

        lanSharingStarted = false
        val setupJob = lanShareJob
        setupJob?.cancel()
        lanShareJob = null
        val appContext = context.applicationContext
        scope.launch {
            setupJob?.join()
            runCatching { RootProxyManager.stop(appContext) }
        }
    }
}
