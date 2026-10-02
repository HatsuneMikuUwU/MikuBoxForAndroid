package com.mikubox.mihomo.profile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.miku.ray.util.LogUtil
import com.mikubox.mihomo.R
import com.mikubox.mihomo.core.CoreOverrides
import com.mikubox.mihomo.core.MihomoCore
import com.mikubox.mihomo.service.VpnController
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit

/** Periodically updates native Mihomo subscription profiles, including after reboot. */
object MihomoSubscriptionUpdater {

    private const val WORK_NAME = "mihomo-subscription-update"
    private const val CHANNEL_ID = "mihomo_subscription"
    private const val NOTIFICATION_ID = 2
    private val PROXY_NAME = Regex("^\\s*-\\s+name:\\s*(.+?)\\s*$")

    data class UpdateResult(
        val added: List<String>,
        val deleted: List<String>,
    )

    /**
     * Schedules the periodic update for whatever subscriptions exist.
     *
     * Best effort: this runs from every path that changes a profile — adding a
     * subscription, editing one, restoring a backup — so it must not be the reason
     * one of those fails. A process where WorkManager is not up (an early start,
     * or a test) simply gets no schedule; the next change schedules it again.
     */
    fun reconfigure(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context)
            val profiles = MihomoProfileStore.profiles(context).filter { it.isSubscription && it.updateIntervalMinutes > 0 }
            if (profiles.isEmpty()) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }

            val interval = profiles.minOf { it.updateIntervalMinutes.coerceAtLeast(15) }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(interval, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }.onFailure { error ->
            LogUtil.w(message = "subscription updates not scheduled: ${error.message}")
        }
    }

    /**
     * The user asked for this subscription to be fetched only while the tunnel
     * is up ("the provider is never contacted directly"). Raised by [update]
     * when a fetch would have to go out directly; callers surface it as a
     * failed update instead of silently contacting the provider.
     */
    class BlockedWhileDisconnected(message: String) : IllegalStateException(message)

    /** True when this subscription must not be contacted in the current state. */
    fun blockedWhileDisconnected(profile: MihomoProfileStore.Profile): Boolean =
        profile.updateWhenConnectedOnly && !VpnController.isRunning

    fun update(context: Context, profile: MihomoProfileStore.Profile): UpdateResult {
        // The gate lives at the entry, not only in the worker loop: the
        // editor's save (including a first fetch), the subscription list's
        // update, the home-screen refresh and the importer all funnel through
        // here, and the flag promises the provider is never contacted
        // directly. Skipping is a failure the caller can show, not a silent
        // direct fetch.
        if (blockedWhileDisconnected(profile)) {
            throw BlockedWhileDisconnected(
                "updateWhenConnectedOnly is set and the tunnel is down; not contacting ${profile.subscriptionUrl}",
            )
        }
        val url = requireNotNull(profile.subscriptionUrl)
        val routeThroughProxy = profile.updateThroughProxy &&
            (profile.config.isNotBlank() || VpnController.isRunning)
        // A first download cannot depend on the configuration it is downloading.
        // Preserve the preference: only an empty subscription may bootstrap directly.
        val body = fetch(context, url, routeThroughProxy)
        val config = MihomoSubscriptionDecoder.toMihomoConfig(context, body)
        val previous = proxyNames(profile.config)
        val current = proxyNames(config)
        MihomoProfileStore.updateSubscription(context, profile, config)
        return UpdateResult(
            added = current.filterNot(previous::contains),
            deleted = previous.filterNot(current::contains),
        )
    }

    /** Extracts named proxy entries from the standard Mihomo YAML proxy list. */
    private fun proxyNames(config: String): List<String> {
        var inProxies = false
        val names = mutableListOf<String>()
        config.lineSequence().forEach { line ->
            if (!inProxies) {
                // Top-level key only: a nested "proxies:" inside a
                // proxy-providers block lists its own nodes, not real proxies.
                if (line == "proxies:") inProxies = true
                return@forEach
            }
            if (line.isNotBlank() && line.first() != ' ' && line.first() != '\t') return names
            val match = PROXY_NAME.matchEntire(line) ?: return@forEach
            names += match.groupValues[1]
                .trim()
                .removeSurrounding("'")
                .replace("''", "'")
        }
        return names
    }

    /**
     * HttpURLConnection does not follow redirects that switch between http and https,
     * which many subscription providers rely on. Follow them manually.
     */
    private fun fetch(context: Context, initialUrl: String, throughProxy: Boolean = false, maxRedirects: Int = 5): String {
        var target = URL(initialUrl)
        // Through the tunnel: the core's mixed port is what the update is sent to
        // when the user asks for a proxied refresh — and only while it is up,
        // because there is no proxy to route through otherwise.
        val proxy = if (throughProxy) {
            check(VpnController.isRunning) { "Connect before updating through the proxy" }
            val port = com.miku.ray.handler.SettingsManager.getHttpPort()
            require(port in 1..65535) { "Invalid proxy port" }
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
        } else {
            Proxy.NO_PROXY
        }
        repeat(maxRedirects + 1) {
            val opened = target.openConnection(proxy)
            val connection = (opened as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", subscriptionUserAgent(context))
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error(context.getString(R.string.error_subscription_http, code))
                    target = URL(target, location)
                    return@repeat
                }
                check(code in 200..299) {
                    context.getString(R.string.error_subscription_http, code)
                }
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        error(context.getString(R.string.error_subscription_http, 310))
    }

    /** POST_NOTIFICATIONS is runtime-granted on Android 13+; skip posting if denied. */
    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun subscriptionUserAgent(context: Context): String {
        val appVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?.removePrefix("UwU-")
            ?: "unknown"
        // Optional version metadata must not prevent downloading a subscription.
        val coreVersion = runCatching { MihomoCore.version() }.getOrDefault("unknown")
        return "MikuBox/$appVersion mihomo/$coreVersion android/${Build.VERSION.RELEASE}"
    }

    class UpdateWorker(
        appContext: Context,
        parameters: WorkerParameters,
    ) : CoroutineWorker(appContext, parameters) {

        // doWork only posts through NotificationManagerCompat after
        // canPostNotifications, which lint cannot trace through the lambdas.
        @android.annotation.SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            ensureChannel(applicationContext)
            try {
                val profiles = MihomoProfileStore.profiles(applicationContext).filter { it.isSubscription && it.updateIntervalMinutes > 0 }
                var attempted = false
                var updated = false
                // One broken subscription must not stop the others, and the
                // ongoing notification has to leave even when some fail.
                profiles.forEach { profile ->
                    if (blockedWhileDisconnected(profile)) return@forEach
                    val age = System.currentTimeMillis() - profile.updatedAtMillis
                    if (age < profile.updateIntervalMinutes.coerceAtLeast(15) * 60_000L) return@forEach
                    attempted = true
                    if (canPostNotifications(applicationContext)) {
                        // The grant can be pulled while the worker waits; a
                        // revoked notification must not fail the update.
                        runCatching {
                            NotificationManagerCompat.from(applicationContext).notify(
                                NOTIFICATION_ID,
                                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                                    .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                                    .setContentTitle(applicationContext.getString(R.string.subscription_update_title))
                                    .setContentText(profile.name)
                                    .setOngoing(true)
                                    .build(),
                            )
                        }
                    }
                    runCatching { update(applicationContext, profile) }.onSuccess { updated = true }
                }
                return if (!attempted || updated) Result.success() else Result.retry()
            } finally {
                NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.subscription_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
