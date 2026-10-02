package com.miku.ray.root

import com.miku.ray.util.waitForCompat

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.util.LogUtil
import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {

    data class Result(val code: Int, val output: String) {
        val success: Boolean get() = code == 0
    }

    fun runScript(context: Context, name: String, script: String): Result {
        val dir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val file = File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }
        val safePath = file.absolutePath.replace("'", "'\\''")
        return exec("sh '$safePath'")
    }

    private fun exec(command: String, timeoutSeconds: Long = 30): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitForCompat(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.e(AppConfig.TAG, "RootShell: timed out: $command")
                return Result(-1, output)
            }
            val result = Result(process.exitValue(), output)
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell: '$command' exited ${result.code}: ${output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }
}
