package com.miku.ray.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.RealPingEvent
import com.miku.ray.dto.SubscriptionUpdateMessage
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.extension.serializable
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.LogUtil
import com.miku.ray.helper.NotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionUpdateService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val runningTasks = AtomicInteger(0)

    @Volatile
    private var activeWorker: RealPingWorkerService? = null

    private val updateSemaphore = Semaphore(2)
    private val probeSemaphore = Semaphore(1)

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService is being destroyed")
        activeWorker?.cancel()
        activeWorker = null
        serviceJob.cancel()
        NotificationHelper.stopForeground(this)
        NotificationHelper.cancel(NotificationChannelType.SUBSCRIPTION_UPDATE, this)
        super.onDestroy()

        Handler(Looper.getMainLooper()).post { Process.killProcess(Process.myPid()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            getString(R.string.title_pref_auto_update_subscription),
            getString(R.string.subscription_update_background_start)
        )
        val message = intent?.serializable<SubscriptionUpdateMessage>("content")
        if (message == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (message.key) {
            AppConfig.MSG_SUB_UPDATE_START -> handleUpdateStart(message)
            AppConfig.MSG_SUB_UPDATE_CANCEL -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
            }

            else -> {
                NotificationHelper.stopForeground(this)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleUpdateStart(message: SubscriptionUpdateMessage) {
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService starting update task for ${message.subIds.size} subscriptions")

        runningTasks.incrementAndGet()
        serviceScope.launch {
            updateSemaphore.withPermit {
                try {
                    message.subIds.forEach { subId ->
                        updateSingle(subId, message.forcedUpdate)
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SubscriptionUpdateService update failed", e)
                } finally {
                    if (runningTasks.decrementAndGet() == 0) {
                        NotificationHelper.stopForeground(this@SubscriptionUpdateService)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun updateSingle(subId: String, forcedUpdate: Boolean) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        if (!subItem.enabled || subItem.url.isEmpty()) return

        val sub = SubscriptionCache(subId, subItem)

        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: Updating ${subItem.remarks}")
        showNotification(
            context = this,
            titleResId = R.string.title_pref_auto_update_subscription,
            content = getString(R.string.subscription_update_updating, subItem.remarks)
        )

        if (forcedUpdate || MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)) {
            AngConfigManager.updateConfigViaSub(sub)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)) {
            val testCompleted = probeSemaphore.withPermit {
                testSubscriptionServers(sub)
            }

            if (testCompleted &&
                MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
            ) {
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: removing invalid servers for ${subItem.remarks}")
                showNotification(
                    context = this,
                    titleResId = R.string.title_del_invalid_config,
                    content = subItem.remarks
                )
                AngConfigManager.removeInvalidServer(subId)
            }
        }

        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: Finished ${subItem.remarks}")
    }

    private suspend fun testSubscriptionServers(sub: SubscriptionCache): Boolean {
        val subId = sub.guid
        LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: starting test phase for ${sub.subscription.remarks}")
        showNotification(
            context = this,
            titleResId = R.string.title_real_ping_all_server,
            content = sub.subscription.remarks
        )

        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) return true

        val deferred = CompletableDeferred<Boolean>()
        val worker = RealPingWorkerService(
            context = this,
            guids = guids,
            onEvent = { event ->
                handleWorkerEvent(event, sub.subscription.remarks) { completed ->
                    deferred.complete(completed)
                }
            },
        )
        activeWorker = worker
        return try {
            worker.start()
            val completed = deferred.await()
            LogUtil.i(
                AppConfig.TAG,
                "SubscriptionUpdateService: test phase finished for ${sub.subscription.remarks}",
            )
            completed
        } finally {
            activeWorker = null
        }
    }

    private fun handleWorkerEvent(event: RealPingEvent, remarks: String, onWorkerDone: (Boolean) -> Unit) {
        when (event) {
            is RealPingEvent.Progress -> {
                val progressText = "${event.completed} / ${event.total}"
                val notificationText = getString(
                    R.string.subscription_update_progress,
                    progressText,
                    remarks,
                )
                showNotification(
                    context = this,
                    titleResId = R.string.title_real_ping_all_server,
                    content = notificationText
                )
                LogUtil.i(AppConfig.TAG, "SubscriptionUpdateService: $notificationText")
            }

            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
            }

            is RealPingEvent.Finish -> {
                onWorkerDone(event.completed == event.total)
            }
        }
    }

    private fun showNotification(context: Context, titleResId: Int, content: String) {
        NotificationHelper.notify(
            NotificationChannelType.SUBSCRIPTION_UPDATE,
            context,
            context.getString(titleResId),
            content
        )
    }
}
