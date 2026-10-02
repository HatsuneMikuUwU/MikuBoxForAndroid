package com.miku.ray.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.util.LogUtil

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (context == null) return

        LogUtil.i(AppConfig.TAG, "BootReceiver received: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                if (userManager != null && !userManager.isUserUnlocked) {
                    LogUtil.w(AppConfig.TAG, "BootReceiver: User is locked, skipping auto start")
                    return
                }
            }
            else -> {
                LogUtil.w(AppConfig.TAG, "BootReceiver: Unhandled action: $action")
                return
            }
        }

        if (!MmkvManager.decodeStartOnBoot()) {
            LogUtil.i(AppConfig.TAG, "BootReceiver: Auto-start on boot is disabled")
            return
        }

        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            LogUtil.w(AppConfig.TAG, "BootReceiver: No server selected")
            return
        }

        if (!com.miku.ray.MikuCoreBridge.startOnBoot()) {
            LogUtil.i(AppConfig.TAG, "BootReceiver: Waiting for VPN permission or a selected profile")
        }
        SubscriptionUpdater.sync(context)
    }
}
