package com.miku.ray.handler

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.workDataOf
import com.miku.ray.AngApplication
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.dto.SubscriptionUpdateMessage
import com.miku.ray.enums.NotificationChannelType
import com.miku.ray.helper.NotificationHelper
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import java.util.concurrent.TimeUnit

object SubscriptionUpdater {

    fun sync(
        context: Context = AngApplication.application,
        forceReschedule: Boolean = false
    ) {
        val existingWorkPolicy =
        if (forceReschedule) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        MmkvManager.decodeSubscriptions()
        .filter { it.subscription.autoUpdate && it.subscription.url.isNotEmpty() }
        .forEach { sub ->
            scheduleOne(
                context = context,
                subId = sub.guid,
                existingWorkPolicy = existingWorkPolicy
            )
        }
        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: sync complete forceReschedule=$forceReschedule"
        )
    }

    fun syncOne(context: Context = AngApplication.application, subId: String) {
        scheduleOne(
            context = context,
            subId = subId,
            existingWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
        )
    }

    fun cancelOne(context: Context = AngApplication.application, subId: String) {
        RemoteWorkManager.getInstance(context)
        .cancelUniqueWork(taskName(subId))
    }

    fun updateLastUpdatedAndReschedule(context: Context = AngApplication.application, subId: String) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        subItem.lastUpdated = System.currentTimeMillis()
        MmkvManager.encodeSubscription(subId, subItem)
        syncOne(context, subId)
    }

    private fun taskName(subId: String) = "${AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME}_$subId"

    private fun scheduleOne(
        context: Context,
        subId: String,
        existingWorkPolicy: ExistingPeriodicWorkPolicy
    ) {
        val subItem = MmkvManager.decodeSubscription(subId) ?: return
        val rw = RemoteWorkManager.getInstance(context)
        if (!subItem.autoUpdate) {
            cancelOne(context, subId)
            LogUtil.d(AppConfig.TAG, "SubscriptionUpdater: cancelled task for ${subItem.remarks}")
            return
        }

        if (subItem.url.isEmpty()) {
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater: url isEmpty for ${subItem.remarks}, skip")
            return
        }

        val intervalMinutes = maxOf(
            AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES,
            subItem.updateInterval
        )

        val lastUpdated = subItem.lastUpdated
        val intervalMillis = intervalMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        var initialDelayMillis = if (lastUpdated <= 0L) {
            0L
        } else {
            maxOf(0L, lastUpdated + intervalMillis - now)
        }

        if (existingWorkPolicy == ExistingPeriodicWorkPolicy.REPLACE && initialDelayMillis < 5000L) {
            initialDelayMillis = 5000L
        }

        val request = PeriodicWorkRequestBuilder<UpdateTask>(intervalMinutes, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        )
        .setInputData(workDataOf(KEY_SUB_ID to subId))
        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
        .addTag(AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME)
        .build()

        rw.enqueueUniquePeriodicWork(
            taskName(subId),
            existingWorkPolicy,
            request
        )

        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionUpdater: scheduled [${subItem.remarks}] interval=${intervalMinutes}min " +
            "initialDelay=${initialDelayMillis / 1000}s policy=$existingWorkPolicy"
        )
    }

    private const val KEY_SUB_ID = "subId"

    class UpdateTask(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subId = inputData.getString(KEY_SUB_ID)
            LogUtil.i(AppConfig.TAG, "SubscriptionUpdater update starting via Service: $subId")

            if (subId.isNullOrEmpty()) {
                LogUtil.w(AppConfig.TAG, "SubscriptionUpdater: missing subId in worker input")
                return Result.success()
            }

            val subItem = MmkvManager.decodeSubscription(subId)
            NotificationHelper.notify(
                channelType = NotificationChannelType.SUBSCRIPTION_UPDATE,
                context = applicationContext,
                title = applicationContext.getString(R.string.title_pref_auto_update_subscription),
                content = applicationContext.getString(
                    R.string.subscription_update_background_start,
                ) + if (subItem?.remarks.isNullOrBlank()) "" else ": ${subItem?.remarks}",
            )

            updateLastUpdatedAndReschedule(applicationContext, subId)

            MessageUtil.sendMsg2SubscriptionService(
                applicationContext,
                SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, true, listOf(subId))
            )

            return Result.success()
        }
    }
}
