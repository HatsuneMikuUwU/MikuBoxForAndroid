package com.miku.ray.root

import com.miku.ray.util.waitForCompat

import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object RootManager {

    @Volatile
    private var cached: Boolean? = null

    fun cachedRoot(): Boolean = cached ?: false

    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val result = probe()
        cached = result
        result
    }

    private fun probe(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id -u")
            .redirectErrorStream(true)
            .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val finished = process.waitForCompat(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.w(AppConfig.TAG, "RootManager: su probe timed out")
                return false
            }
            val isRoot = process.exitValue() == 0 && output.lineSequence().lastOrNull()?.trim() == "0"
            LogUtil.i(AppConfig.TAG, "RootManager: root available = $isRoot")
            isRoot
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "RootManager: no root access (${e.message})")
            false
        }
    }
}
