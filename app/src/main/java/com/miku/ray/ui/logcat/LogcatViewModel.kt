package com.miku.ray.ui.logcat

import com.miku.ray.util.waitForCompat

import android.os.Process
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.util.InProcessLogBuffer
import com.miku.ray.util.LogEntry
import com.miku.ray.util.LogUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

class LogcatViewModel : ViewModel() {

    companion object {
        /**
         * The tag this app's core and VPN service log under.
         *
         * It is not in [ownTags] on purpose: those lines are dropped when they
         * come from this process, because the in-process buffer is assumed to
         * hold them — but the mihomo core logs straight to logcat and never
         * reaches that buffer, so its lines are the ones worth reading here.
         */
        private const val CORE_TAG = "MikuBox"
    }

    private val logsetsAll: MutableList<String> = mutableListOf()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private var currentFilter: String = ""

    var usedFallback: Boolean = false
    private set

    fun getAll(): List<String> = _logs.value

    private val ownTags = setOf(ANG_PACKAGE, LogUtil.TAG_CORE)

    fun loadLogcat() {
        val bufferLines = InProcessLogBuffer.getAll()
        val myPid = Process.myPid().toString()

        val systemLines = (tryLogcatProcessBuilder() ?: tryLogcatPidOnly())
        ?.filter { line ->
            val entry = LogEntry.parse(line)
            val tag = entry.tag
            val pid = entry.meta.substringBefore('/').trim()
            tag.isEmpty() || tag !in ownTags || pid != myPid
        }
        .orEmpty()

        usedFallback = systemLines.isEmpty() && bufferLines.isNotEmpty()

        logsetsAll.clear()
        logsetsAll.addAll(mergeByTimestamp(bufferLines, systemLines))
        applyFilter()
    }

    private fun mergeByTimestamp(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a + b).sortedByDescending { LogEntry.parse(it).timestamp }
    }

    private fun tryLogcatProcessBuilder(): List<String>? {
        return try {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "time",
                "-s", "GoLog,${LogUtil.TAG_CORE},$ANG_PACKAGE,$CORE_TAG,AndroidRuntime,System.err,VpnService"
            )
            .redirectErrorStream(true)
            .start()

            val exited = process.waitForCompat(5, TimeUnit.SECONDS)
            if (!exited) {
                process.destroy()
                return null
            }

            val lines = process.inputStream.bufferedReader().readLines()
            if (lines.isEmpty()) null
            else lines.reversed()
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "logcat ProcessBuilder failed: ${e.message}")
            null
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "logcat ProcessBuilder blocked: ${e.message}")
            null
        }
    }

    private fun tryLogcatPidOnly(): List<String>? {
        return try {
            val pid = Process.myPid().toString()
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid")
            .redirectErrorStream(true)
            .start()

            val exited = process.waitForCompat(5, TimeUnit.SECONDS)
            if (!exited) {
                process.destroy()
                return null
            }

            val lines = process.inputStream.bufferedReader().readLines()
            if (lines.isEmpty()) null
            else lines.reversed()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "logcat --pid fallback failed: ${e.message}")
            null
        }
    }

    fun clearLogcat() {
        try {
            val process = ProcessBuilder("logcat", "-c")
            .redirectErrorStream(true)
            .start()
            process.waitForCompat(3, TimeUnit.SECONDS)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "logcat clear failed: ${e.message}")
        }
        InProcessLogBuffer.clear()
        logsetsAll.clear()
        _logs.value = emptyList()
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        _logs.value = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter, ignoreCase = true) }
        }
    }
}
