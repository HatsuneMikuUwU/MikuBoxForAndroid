package com.miku.ray.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import com.miku.ray.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MainRepository(
    private val app: AngApplication,
) : MainDataSource {
    private val closed = AtomicBoolean(false)
    private val _mainServiceEvent = MutableSharedFlow<MainServiceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val mainServiceEvent: SharedFlow<MainServiceEvent> = _mainServiceEvent.asSharedFlow()

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageUtil.EXTRA_REQUEST_ID).orEmpty()
            val event = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_RESTART -> MainServiceEvent.StateRestart

                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess(
                    restarted = safeIntent.getBooleanContent(),
                )

                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure(
                    message = safeIntent.getStringExtra("content"),
                )

                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> MainServiceEvent.MeasureDelayResult(
                    text = safeIntent.getStringExtra("content").orEmpty(), requestId = requestId,
                )
                AppConfig.MSG_MEASURE_DELAY_CANCEL -> MainServiceEvent.MeasureDelayCancelled(requestId)

                AppConfig.MSG_MEASURE_IP_SUCCESS -> MainServiceEvent.MeasureIpResult(
                    ip = safeIntent.getStringExtra("content"),
                    requestId = requestId,
                )

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val content = safeIntent.getStringExtra("content")
                    val result = content?.parseJson(RealPingResult::class.java)
                    val rawGuid = if (result == null) {
                        content?.takeIf { !it.trimStart().startsWith("{") }
                    } else {
                        null
                    }
                    MainServiceEvent.MeasureConfigResult(result, rawGuid)
                }

                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(
                    progress = safeIntent.getStringExtra("content")?.parseJson(RealPingProgress::class.java),
                )

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    summary = safeIntent.getStringExtra("content")?.parseJson(RealPingSummary::class.java),
                    requestId = requestId,
                )

                AppConfig.MSG_COUNTRY_CODE_SUCCESS -> safeIntent.getStringExtra("content")
                    ?.let { MainServiceEvent.CountryCodeSuccess(it, requestId) }

                AppConfig.MSG_COUNTRY_CODE_NOTIFY -> MainServiceEvent.CountryCodeNotify(
                    info = safeIntent.getStringExtra("content")?.parseJson(TestProgressInfo::class.java),
                    requestId = requestId,
                )

                AppConfig.MSG_COUNTRY_CODE_FINISH -> MainServiceEvent.CountryCodeFinish(requestId)

                AppConfig.MSG_TRAFFIC_UPDATED -> safeIntent.getStringExtra("content")
                    ?.let(MainServiceEvent::TrafficUpdated)

                AppConfig.MSG_TRAFFIC_SPEED_UPDATED -> safeIntent.getStringExtra("content")
                    ?.let(MainServiceEvent::TrafficSpeedUpdated)

                AppConfig.MSG_SUB_UPDATE_FINISH -> MainServiceEvent.SubUpdateFinish

                else -> null
            }
            event?.let { _mainServiceEvent.tryEmit(it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { sendMsg2Service(AppConfig.MSG_UNREGISTER_CLIENT, "") }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister service client", it) }
        runCatching { app.unregisterReceiver(serviceReceiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it) }
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        if (com.miku.ray.MikuProfiles.impl != null) com.miku.ray.MikuSubscriptions.refreshAll() else AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        if (com.miku.ray.MikuProfiles.impl != null) com.miku.ray.MikuSubscriptions.refreshAll() else AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun requestIp(requestId: String) { MessageUtil.sendMsg2Service(app, AppConfig.MSG_MEASURE_IP, "", requestId) }

    override fun sendMsg2Service(msgId: Int, content: String) {
        MessageUtil.sendMsg2Service(app, msgId, content)
    }

    override fun queryRunningState(onResult: (isRunning: Boolean) -> Unit) {
        MessageUtil.sendMsg2ServiceForResult(app, AppConfig.MSG_REGISTER_CLIENT, "") { handled ->
            onResult(handled)
        }
    }

    override fun sendMsg2TestService(msg: TestServiceMessage, requestId: String?) {
        MessageUtil.sendMsg2TestService(app, msg, requestId)
    }

    override fun sendMsg2CountryCodeTestService(msg: CountryCodeTestMessage) {
        MessageUtil.sendMsg2CountryCodeTestService(app, msg)
    }

    override fun testCurrentServerRealPing(requestId: String) {
        MessageUtil.sendMsg2ServiceForResult(app, AppConfig.MSG_MEASURE_DELAY, "", requestId) { handled ->
            if (!handled) _mainServiceEvent.tryEmit(MainServiceEvent.MeasureDelayCancelled(requestId))
        }
    }
}

private fun Intent.getBooleanContent(): Boolean =
    serializable<Boolean>("content") == true

private fun <T> String.parseJson(cls: Class<T>): T? {
    val trimmed = trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
    return JsonUtil.fromJsonSafe(trimmed, cls)
}
