package com.miku.ray.util

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import com.miku.ray.AngApplication
import com.miku.ray.BuildConfig
import com.miku.ray.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ported from NekoBoxForAndroid's io.nekohasekai.sagernet.utils.CrashHandler.
 *
 * Instead of NekoBox's ProcessPhoenix + BlankActivity + SendLog detour, this
 * writes the crash report straight to a file and reuses the same
 * FileProvider ("${applicationId}.cache") and share-sheet flow
 * LogcatActivity.shareLogcat() already uses, so the log can be shared
 * immediately from the crash itself.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(thread.toString(), throwable.stackTraceToString())
        } catch (_: Exception) {
        }

        try {
            LogUtil.e(message = "Uncaught exception on $thread", throwable = throwable)
        } catch (_: Exception) {
        }

        try {
            shareCrashLog(thread, throwable)
        } catch (_: Exception) {
        }

        // Give the share sheet a moment to actually launch before the process dies.
        try {
            Thread.sleep(1000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    private fun shareCrashLog(thread: Thread, throwable: Throwable) {
        val app = AngApplication.application

        val report = buildString {
            appendLine("MikuRay ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("======= CRASH =======")
            appendLine("Thread: $thread")
            appendLine(throwable.stackTraceToString())
            appendLine()
            appendLine("======= RECENT LOG =======")
            // InProcessLogBuffer.getAll() is newest-first; flip it back to
            // chronological order for a readable report.
            appendLine(InProcessLogBuffer.getAll().asReversed().joinToString("\n"))
        }

        val shareDir = File(app.cacheDir, "shared_logs").apply { mkdirs() }
        shareDir.listFiles()?.forEach { it.delete() }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val logFile = File(shareDir, "MikuRay_crash_$timestamp.txt")
        logFile.writeText(report, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.cache", logFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, logFile.name)
            putExtra(Intent.EXTRA_TITLE, logFile.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(app.contentResolver, logFile.name, uri)
        }

        app.startActivity(
            Intent.createChooser(shareIntent, app.getString(R.string.logcat_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
