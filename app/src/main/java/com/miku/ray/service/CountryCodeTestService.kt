package com.miku.ray.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.extension.serializable
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import com.miku.ray.core.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.atomic.AtomicInteger

class CountryCodeTestService : Service() {

    private val cancelled = AtomicBoolean(false)
    private val workerScope = CoroutineScope(Dispatchers.IO)
    private var worker: Job? = null
    private val progressLock = Any()
    @Volatile
    private var activeRequestId = ""

    private val cancelAction by lazy {
        val intent = Intent(this, CountryCodeTestService::class.java).putExtra(
            "content",
            CountryCodeTestMessage(AppConfig.MSG_COUNTRY_CODE_CANCEL)
        )
        val pendingIntent = PendingIntent.getService(
            this,
            NotificationChannelType.CORE_TEST.notificationId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            RemixR.drawable.rmx_media_stop_line,
            getString(android.R.string.cancel),
            pendingIntent
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        com.miku.ray.MikuDiagnostics.impl?.attach(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelled.set(true)
        worker?.cancel()
        worker = null
        sendFinish(activeRequestId)
        activeRequestId = ""
        NotificationHelper.stopForeground(this)
        com.miku.ray.MikuDiagnostics.impl?.detach(this)
        super.onDestroy()
        if (com.miku.ray.MikuDiagnostics.impl != null) {
            // This service is declared in its own process, just like CoreTestService.
            android.os.Handler(mainLooper).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 500)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<CountryCodeTestMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_COUNTRY_CODE_START -> handleStart(message, startId)
            AppConfig.MSG_COUNTRY_CODE_CANCEL -> handleCancel(message.requestId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(message: CountryCodeTestMessage, startId: Int) {
        if (worker?.isActive == true) {
            cancelled.set(true)
            worker?.cancel()
            sendFinish(activeRequestId)
            activeRequestId = ""
        }
        val requestId = message.requestId

        val guids = when {
            message.serverGuids.isNotEmpty() -> message.serverGuids
            message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
            else -> MmkvManager.decodeAllServerList()
        }
        if (guids.isEmpty()) {
            sendFinish(requestId)
            stopSelf(startId)
            return
        }

        cancelled.set(false)
        activeRequestId = requestId
        val targetGuids = guids.toList()
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            AppNameHelper.getDisplayName(this),
            getString(R.string.title_country_code_all_server),
            cancelAction
        )

        worker = workerScope.launch(
            Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency()),
        ) {
            try {
                SettingsManager.initAssets(this@CountryCodeTestService, assets)
                CoreNativeManager.initCoreEnv(this@CountryCodeTestService)
                val completed = AtomicInteger(0)
                supervisorScope {
                    targetGuids.map { guid ->
                        async {
                            currentCoroutineContext().ensureActive()
                            val countryCode = try {
                                lookupThroughProfile(guid)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                LogUtil.e(AppConfig.TAG, "Country-code probe failed for $guid", e)
                                null
                            }
                            if (!cancelled.get() && activeRequestId == requestId) {
                                MmkvManager.encodeServerCountryCode(guid, countryCode ?: AppConfig.COUNTRY_CODE_TEST_FAILED)
                            }
                            synchronized(progressLock) {
                                MessageUtil.sendMsg2UI(this@CountryCodeTestService, AppConfig.MSG_COUNTRY_CODE_SUCCESS, guid, requestId)
                                val current = completed.incrementAndGet()
                                val progress = TestProgressInfo(guid, 0L, current, targetGuids.size)
                                NotificationHelper.updateNotification(
                                    channelType = NotificationChannelType.CORE_TEST,
                                    context = this@CountryCodeTestService,
                                    title = getString(R.string.title_country_code_all_server),
                                    content = getString(
                                        R.string.connection_runing_task_left,
                                        "${current} / ${targetGuids.size}",
                                    ),
                                )
                                MessageUtil.sendMsg2UI(
                                    this@CountryCodeTestService,
                                    AppConfig.MSG_COUNTRY_CODE_NOTIFY,
                                    JsonUtil.toJson(progress),
                                    requestId,
                                )
                            }
                        }
                    }.awaitAll()
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "CountryCodeTestService failed", e)
            } finally {
                sendFinish(requestId)
                if (activeRequestId == requestId) activeRequestId = ""
                stopSelf(startId)
            }
        }
    }

    private fun lookupThroughProfile(guid: String): String? {
        // This worker must never start/stop the application's single VPN core.
        com.miku.ray.MikuProfiles.impl?.let { profiles ->
            val raw = profiles.get(guid)?.config ?: return null
            return com.miku.ray.MikuDiagnostics.impl?.country(raw)
        }
        val result = CoreConfigManager.getV2rayConfig4Speedtest(this, guid)
        if (!result.status || result.content.isBlank()) return null

        val config = JsonUtil.parseString(result.content) ?: return null
        val inbounds = JsonArray()
        config.add("inbounds", inbounds)

        val httpPort = Utils.findRandomFreePort()
        var socksPort = Utils.findRandomFreePort()
        while (socksPort == httpPort) {
            socksPort = Utils.findRandomFreePort()
        }
        inbounds.add(createSocksInbound(socksPort))
        inbounds.add(createHttpInbound(httpPort))

        val controller = CoreNativeManager.newCoreController(CountryCallback())
        return try {
            // No descriptor: this test used to spin up a second, temporary core on
            // a socks port. The embedded mihomo core is the app's one tunnel, so
            // the loop is started without a descriptor and the proxy ports below
            // are what the test talks to.
            controller.startLoop(null, JsonUtil.toJson(config))
            if (!waitForProxy(httpPort)) return null

            val timeoutMs = SettingsManager.getCountryCodeTestTimeout()
            val countryCode = SpeedtestManager.getCountryCodeThroughProxy(httpPort, timeoutMs)
            if (countryCode != null || cancelled.get()) {
                countryCode
            } else {
                Thread.sleep(120)
                val retryTimeoutMs = (timeoutMs - 1000).coerceAtLeast(1000)
                SpeedtestManager.getCountryCodeThroughProxy(httpPort, timeoutMs = retryTimeoutMs)
            }
        } finally {
            runCatching { controller.stopLoop() }
        }
    }

    private fun waitForProxy(port: Int, timeoutMs: Int = 1800): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cancelled.get() && System.currentTimeMillis() < deadline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 120)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(60)
            }
        }
        return false
    }

    private fun createSocksInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "country-socks")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply {
                    addProperty("auth", "noauth")
                    addProperty("udp", true)
            })
        }
    }

    private fun createHttpInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "country-http")
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("port", port)
            addProperty("protocol", "http")
            add("settings", JsonObject())
        }
    }

    private fun handleCancel(requestId: String) {
        cancelled.set(true)
        worker?.cancel()
        worker = null
        sendFinish(requestId.ifEmpty { activeRequestId })
        activeRequestId = ""
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    private fun sendFinish(requestId: String) {
        MessageUtil.sendMsg2UI(this, AppConfig.MSG_COUNTRY_CODE_FINISH, "0", requestId)
    }

    private class CountryCallback : CoreCallbackHandler {
        override fun startup(): Long = 0L
        override fun shutdown(): Long = 0L
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0L
        }
    }
}
