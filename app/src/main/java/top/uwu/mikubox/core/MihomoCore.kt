package top.uwu.mikubox.core

import android.content.Context
import org.json.JSONObject
import top.uwu.mikubox.R
import java.io.File

/** JNI entry point for the bundled Mihomo Alpha core. */
object MihomoCore {

    const val NO_TUN = -1

    data class Traffic(
        val uploadPerSecond: Long,
        val downloadPerSecond: Long,
        val uploadTotal: Long,
        val downloadTotal: Long,
    )

    init {
        System.loadLibrary("mihomo")
        System.loadLibrary("mikubox_core")
    }

    fun start(context: Context, config: String, tunFd: Int): Result<Unit> = runCatching {
        val home = File(context.filesDir, "mihomo").apply { mkdirs() }
        check(nativeStart(config, home.absolutePath, tunFd) == 0) {
            nativeLastError().ifBlank { context.getString(R.string.mihomo_start_failed) }
        }
    }

    fun stop() {
        nativeStop()
    }

    fun version(): String = nativeVersion()

    fun traffic(): Traffic = JSONObject(nativeTraffic()).let {
        Traffic(
            uploadPerSecond = it.optLong("upload"),
            downloadPerSecond = it.optLong("download"),
            uploadTotal = it.optLong("uploadTotal"),
            downloadTotal = it.optLong("downloadTotal"),
        )
    }

    private external fun nativeStart(config: String, home: String, tunFd: Int): Int
    private external fun nativeStop()
    private external fun nativeLastError(): String
    private external fun nativeVersion(): String
    private external fun nativeTraffic(): String
}
