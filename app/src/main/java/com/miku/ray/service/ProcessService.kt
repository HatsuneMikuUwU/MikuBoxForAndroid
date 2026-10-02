package com.miku.ray.service

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.extension.delay
import com.miku.ray.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProcessService {
    private var process: Process? = null
    private var watcherJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun runProcess(context: Context, cmd: MutableList<String>) {
        LogUtil.i(AppConfig.TAG, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            watcherJob?.cancel()
            val startedProcess = proBuilder
            .directory(context.filesDir)
            .start()
            process = startedProcess

            watcherJob = scope.launch {
                delay(50L)
                LogUtil.i(AppConfig.TAG, "runProcess check")
                startedProcess.waitFor()
                if (process === startedProcess) {
                    process = null
                }
                LogUtil.i(AppConfig.TAG, "runProcess exited")
            }
            LogUtil.i(AppConfig.TAG, process.toString())

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, e.toString(), e)
        }
    }

    fun stopProcess() {
        try {
            LogUtil.i(AppConfig.TAG, "runProcess destroy")
            watcherJob?.cancel()
            watcherJob = null
            process?.destroy()
            process = null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to destroy process", e)
        }
    }
}
