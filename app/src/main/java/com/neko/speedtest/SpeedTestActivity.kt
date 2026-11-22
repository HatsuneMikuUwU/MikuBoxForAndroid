package com.neko.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.SpeedTestScreen
import top.nkbe.mikubox.ui.page.tools.SpeedTestUiState
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SpeedTestActivity : ThemedActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var testJob: Job? = null
    private val testDurationSeconds = 15.0

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    private val dummyData by lazy { ByteArray(1024 * 1024) }
    private var uiState by mutableStateOf(SpeedTestUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiState = defaultUiState()

        setMikuContent {
            SpeedTestScreen(
                state = uiState,
                onStartFull = { startFullSpeedTest() },
                onStop = { stopSpeedTest() },
                onTestDownload = { runIndividualTest(isDownload = true) },
                onTestUpload = { runIndividualTest(isDownload = false) },
                onTestPing = { runPingOnlyTest() },
                onTestJitter = { runJitterOnlyTest() },
                onBack = { finish() },
            )
        }

        scope.launch(Dispatchers.IO) {
            dummyData.fill(1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        testJob?.cancel()
        scope.cancel()
    }

    private fun defaultUiState() = SpeedTestUiState(
        liveMbps = 0f,
        isRunning = false,
        downloadText = getString(R.string.st_test_download),
        uploadText = getString(R.string.st_test_upload),
        pingText = getString(R.string.st_test_ping),
        jitterText = getString(R.string.st_test_jitter),
    )

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun stopSpeedTest() {
        testJob?.cancel("Test stopped by user")
        testJob = null
        uiState = defaultUiState()
    }

    private fun resetIdleGauge() {
        if (isFinishing) return
        uiState = uiState.copy(isRunning = false, liveMbps = 0f)
    }

    private suspend fun performLatencyCheck(iterations: Int = 10): List<Long> = withContext(Dispatchers.IO) {
        val targetUrl = "https://www.gstatic.com/generate_204"
        val results = mutableListOf<Long>()

        try {
            val warmup = Request.Builder()
                .url(targetUrl)
                .header("Connection", "keep-alive")
                .build()
            client.newCall(warmup).execute().close()
        } catch (_: Exception) {
        }

        for (i in 0 until iterations) {
            if (!isActive) break
            val start = System.nanoTime()
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    val end = System.nanoTime()
                    if (response.isSuccessful || response.code == 204) {
                        results.add((end - start) / 1_000_000)
                    }
                }
            } catch (_: Exception) {
            }
            delay(50)
        }

        if (results.size > 4) {
            results.sort()
            return@withContext results.dropLast(1)
        }
        return@withContext results
    }

    private fun calculateJitter(latencies: List<Long>): Long {
        if (latencies.size < 2) return 0L
        var diffSum = 0L
        for (i in 0 until latencies.size - 1) {
            diffSum += abs(latencies[i] - latencies[i + 1])
        }
        return diffSum / (latencies.size - 1)
    }

    private fun runPingOnlyTest() {
        if (!isInternetAvailable()) return
        uiState = uiState.copy(
            isRunning = true,
            liveMbps = 0f,
            pingText = getString(R.string.st_status_testing),
        )
        testJob = scope.launch {
            try {
                val latencies = performLatencyCheck(12)
                uiState = uiState.copy(
                    pingText = if (latencies.isNotEmpty()) "${latencies.average().toLong()} ms" else "Error",
                )
            } catch (_: Exception) {
                if (!isFinishing) uiState = uiState.copy(pingText = "Error")
            } finally {
                resetIdleGauge()
            }
        }
    }

    private fun runJitterOnlyTest() {
        if (!isInternetAvailable()) return
        uiState = uiState.copy(
            isRunning = true,
            liveMbps = 0f,
            jitterText = getString(R.string.st_status_testing),
        )
        testJob = scope.launch {
            try {
                val latencies = performLatencyCheck(12)
                uiState = uiState.copy(
                    jitterText = if (latencies.size >= 2) "${calculateJitter(latencies)} ms" else "N/A",
                )
            } catch (_: Exception) {
                if (!isFinishing) uiState = uiState.copy(jitterText = "Error")
            } finally {
                resetIdleGauge()
            }
        }
    }

    private fun runIndividualTest(isDownload: Boolean) {
        if (!isInternetAvailable()) return
        uiState = uiState.copy(isRunning = true, liveMbps = 0f)
        testJob = scope.launch {
            try {
                if (isDownload) {
                    uiState = uiState.copy(downloadText = getString(R.string.st_status_running))
                    val downloadSpeed = measureDownloadSpeed()
                    uiState = uiState.copy(
                        downloadText = getString(R.string.st_format_mbps, downloadSpeed),
                        liveMbps = downloadSpeed.toFloat(),
                    )
                } else {
                    uiState = uiState.copy(uploadText = getString(R.string.st_status_running))
                    val uploadSpeed = measureUploadSpeed()
                    uiState = uiState.copy(
                        uploadText = getString(R.string.st_format_mbps, uploadSpeed),
                        liveMbps = uploadSpeed.toFloat(),
                    )
                }
            } catch (_: Exception) {
            } finally {
                resetIdleGauge()
            }
        }
    }

    private fun startFullSpeedTest() {
        if (!isInternetAvailable()) return
        uiState = SpeedTestUiState(
            liveMbps = 0f,
            isRunning = true,
            downloadText = getString(R.string.st_status_testing),
            uploadText = getString(R.string.st_status_waiting),
            pingText = getString(R.string.st_status_testing),
            jitterText = getString(R.string.st_status_testing),
        )

        testJob = scope.launch {
            try {
                val latencies = performLatencyCheck(12)
                uiState = if (latencies.isNotEmpty()) {
                    uiState.copy(
                        pingText = "${latencies.average().toLong()} ms",
                        jitterText = "${calculateJitter(latencies)} ms",
                    )
                } else {
                    uiState.copy(pingText = "N/A", jitterText = "N/A")
                }

                delay(500)

                uiState = uiState.copy(downloadText = getString(R.string.st_status_running))
                val downloadSpeed = measureDownloadSpeed()
                uiState = uiState.copy(
                    downloadText = getString(R.string.st_format_mbps, downloadSpeed),
                    liveMbps = downloadSpeed.toFloat(),
                )

                delay(1000)
                uiState = uiState.copy(liveMbps = 0f)
                delay(800)

                uiState = uiState.copy(uploadText = getString(R.string.st_status_running))
                val uploadSpeed = measureUploadSpeed()
                uiState = uiState.copy(
                    uploadText = getString(R.string.st_format_mbps, uploadSpeed),
                    liveMbps = uploadSpeed.toFloat(),
                )

                delay(1000)
            } catch (_: CancellationException) {
            } catch (_: Exception) {
            } finally {
                resetIdleGauge()
            }
        }
    }

    private suspend fun measureDownloadSpeed(): Double = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://dl.google.com/android/studio/install/android-studio-ide-2023.3.1.20-windows.exe",
            "https://download.mozilla.org/?product=firefox-latest-ssl&os=win64&lang=en-US",
            "https://github.com/topjohnwu/Magisk/releases/download/canary-28103/app-release.apk",
        )

        var totalBytes = 0L
        var speedMbps = 0.0
        var currentUrlIndex = 0
        val startTime = System.nanoTime()
        var lastUpdateTime = System.currentTimeMillis()

        while (isActive) {
            val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
            if (elapsedSeconds > testDurationSeconds || currentUrlIndex >= urls.size) break

            try {
                val request = Request.Builder()
                    .url(urls[currentUrlIndex])
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Body null")
                    val stream = body.byteStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (isActive) {
                        bytesRead = stream.read(buffer)
                        if (bytesRead == -1) break
                        totalBytes += bytesRead

                        val innerElapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
                        if (innerElapsed > testDurationSeconds) break

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 200) {
                            speedMbps = (totalBytes * 8) / (innerElapsed * 1_000_000.0)
                            withContext(Dispatchers.Main) {
                                if (!isFinishing) uiState = uiState.copy(liveMbps = speedMbps.toFloat())
                            }
                            lastUpdateTime = now
                        }
                    }
                }
            } catch (_: Exception) {
                currentUrlIndex++
                delay(100)
            }
        }
        return@withContext speedMbps
    }

    private suspend fun measureUploadSpeed(): Double = withContext(Dispatchers.IO) {
        val uploadUrls = listOf(
            "https://httpbun.com/post",
            "https://postman-echo.com/post",
            "https://httpbin.org/post",
        )

        val mediaType = "application/octet-stream".toMediaType()
        val payloadSize = dummyData.size.toLong()
        var totalBytesUploaded = 0L
        var speedMbps = 0.0
        var currentUrlIndex = 0
        val startTime = System.nanoTime()
        var lastUpdateTime = System.currentTimeMillis()

        while (isActive) {
            val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
            if (elapsedSeconds > testDurationSeconds || currentUrlIndex >= uploadUrls.size) break

            try {
                val requestBody = dummyData.toRequestBody(mediaType, 0, dummyData.size)
                val request = Request.Builder()
                    .url(uploadUrls[currentUrlIndex])
                    .post(requestBody)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        totalBytesUploaded += payloadSize
                    } else {
                        throw IOException("HTTP ${response.code}")
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime > 200) {
                    val currentElapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
                    if (currentElapsed > 0) {
                        speedMbps = (totalBytesUploaded * 8) / (currentElapsed * 1_000_000.0)
                        withContext(Dispatchers.Main) {
                            if (!isFinishing) uiState = uiState.copy(liveMbps = speedMbps.toFloat())
                        }
                        lastUpdateTime = now
                    }
                }
            } catch (_: Exception) {
                currentUrlIndex++
                delay(100)
            }
        }
        return@withContext speedMbps
    }
}
