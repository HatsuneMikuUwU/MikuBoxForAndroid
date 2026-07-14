package top.uwu.mikubox.profile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import top.uwu.mikubox.R
import top.uwu.mikubox.core.MihomoCore
import top.uwu.mikubox.service.VpnController
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Periodically updates native Mihomo subscription profiles, including after reboot. */
object MihomoSubscriptionUpdater {

    private const val WORK_NAME = "mihomo-subscription-update"
    private const val CHANNEL_ID = "mihomo_subscription"
    private const val NOTIFICATION_ID = 2

    fun reconfigure(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_NAME)
        val profiles = MihomoProfileStore.profiles(context).filter { it.isSubscription }
        if (profiles.isEmpty()) return

        val interval = profiles.minOf { it.updateIntervalMinutes.coerceAtLeast(15) }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(interval, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun update(context: Context, profile: MihomoProfileStore.Profile) {
        val url = requireNotNull(profile.subscriptionUrl)
        val body = fetch(context, url)
        val config = MihomoSubscriptionDecoder.toMihomoConfig(context, body)
        MihomoProfileStore.update(
            context,
            profile.copy(config = config, updatedAtMillis = System.currentTimeMillis()),
        )
    }

    /**
     * HttpURLConnection does not follow redirects that switch between http and https,
     * which many subscription providers rely on. Follow them manually.
     */
    private fun fetch(context: Context, initialUrl: String, maxRedirects: Int = 5): String {
        var target = URL(initialUrl)
        repeat(maxRedirects + 1) {
            val connection = (target.openConnection() as HttpURLConnection).apply {
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

    private fun subscriptionUserAgent(context: Context): String {
        val appVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?.removePrefix("UwU-")
            ?: "unknown"
        return "MikuBox/$appVersion mihomo/${MihomoCore.version()} android/${Build.VERSION.RELEASE}"
    }

    class UpdateWorker(
        appContext: Context,
        parameters: WorkerParameters,
    ) : CoroutineWorker(appContext, parameters) {

        override suspend fun doWork(): Result = runCatching {
            ensureChannel(applicationContext)
            val profiles = MihomoProfileStore.profiles(applicationContext).filter { it.isSubscription }
            profiles.forEach { profile ->
                if (profile.updateWhenConnectedOnly && !VpnController.isRunning) return@forEach
                val age = System.currentTimeMillis() - profile.updatedAtMillis
                if (age < profile.updateIntervalMinutes.coerceAtLeast(15) * 60_000L) return@forEach
                NotificationManagerCompat.from(applicationContext).notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(applicationContext.getString(R.string.subscription_update_title))
                        .setContentText(profile.name)
                        .setOngoing(true)
                        .build(),
                )
                update(applicationContext, profile)
            }
            NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
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
