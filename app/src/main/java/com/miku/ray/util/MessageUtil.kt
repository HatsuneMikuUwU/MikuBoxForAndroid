package com.miku.ray.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.SubscriptionUpdateMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.service.CoreTestService
import com.miku.ray.service.CountryCodeTestService
import com.miku.ray.service.SubscriptionUpdateService
import java.io.Serializable

object MessageUtil {

    const val EXTRA_REQUEST_ID = "requestId"

    fun sendMsg2Service(ctx: Context, what: Int, content: Serializable, requestId: String? = null) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_SERVICE, what, content, requestId)
    }

    fun sendMsg2ServiceForResult(
        ctx: Context,
        what: Int,
        content: Serializable,
        onResult: (handled: Boolean) -> Unit,
    ) = sendMsg2ServiceForResult(ctx, what, content, null, onResult)

    fun sendMsg2ServiceForResult(
        ctx: Context,
        what: Int,
        content: Serializable,
        requestId: String?,
        onResult: (handled: Boolean) -> Unit,
    ) {
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onResult(resultCode == Activity.RESULT_OK)
            }
        }
        try {
            ctx.sendOrderedBroadcast(
                messageIntent(AppConfig.BROADCAST_ACTION_SERVICE, what, content).apply {
                    requestId?.let { putExtra(EXTRA_REQUEST_ID, it) }
                },
                null,
                resultReceiver,
                null,
                Activity.RESULT_CANCELED,
                null,
                null,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send ordered message to service", e)
            onResult(false)
        }
    }

    fun sendMsg2UI(ctx: Context, what: Int, content: Serializable, requestId: String? = null) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_ACTIVITY, what, content, requestId)
    }

    fun sendMsg2TestService(ctx: Context, message: TestServiceMessage, requestId: String? = null) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, CoreTestService::class.java)
            intent.putExtra("content", message)
            requestId?.let { intent.putExtra(EXTRA_REQUEST_ID, it) }
            when (message.key) {
                AppConfig.MSG_MEASURE_CONFIG_START -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }

                else -> {
                    ctx.startService(intent)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message to test service", e)
            if (message.key == AppConfig.MSG_MEASURE_CONFIG_START) {
                sendMsg2UI(ctx, AppConfig.MSG_MEASURE_CONFIG_FINISH, "", requestId)
            }
        }
    }

    fun sendMsg2CountryCodeTestService(ctx: Context, message: CountryCodeTestMessage) {
        try {
            val intent = Intent(ctx, CountryCodeTestService::class.java)
                .putExtra("content", message)
                .putExtra(EXTRA_REQUEST_ID, message.requestId)
            when (message.key) {
                AppConfig.MSG_COUNTRY_CODE_START -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }
                AppConfig.MSG_COUNTRY_CODE_CANCEL -> ctx.stopService(intent)
                else -> ctx.startService(intent)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message to country code service", e)
            if (message.key == AppConfig.MSG_COUNTRY_CODE_START) {
                sendMsg2UI(ctx, AppConfig.MSG_COUNTRY_CODE_FINISH, "", message.requestId)
            }
        }
    }

    fun sendMsg2SubscriptionService(ctx: Context, message: SubscriptionUpdateMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, SubscriptionUpdateService::class.java)
            intent.putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_SUB_UPDATE_START -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }

                AppConfig.MSG_SUB_UPDATE_CANCEL -> {
                    ctx.stopService(intent)
                }

                else -> {
                    ctx.startService(intent)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message to subscription update service", e)
        }
    }

    private fun sendMsg(ctx: Context, action: String, what: Int, content: Serializable, requestId: String? = null) {
        try {
            ctx.sendBroadcast(messageIntent(action, what, content).apply {
                requestId?.let { putExtra(EXTRA_REQUEST_ID, it) }
            })
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message with action: $action", e)
        }
    }

    private fun messageIntent(action: String, what: Int, content: Serializable): Intent =
    Intent(action).apply {
        `package` = AppConfig.ANG_PACKAGE
        putExtra("key", what)
        putExtra("content", content)
    }
}
