package top.uwu.mikubox.profile

import android.content.Context
import top.uwu.mikubox.core.MihomoCore

/** Persistent per-profile totals, equivalent to UwU's profile traffic accounting. */
object MihomoTrafficStore {

    data class Totals(val upload: Long, val download: Long)

    private const val PREFS = "mihomo_traffic"
    private var activeProfileId: String? = null
    private var uploadBaseline = 0L
    private var downloadBaseline = 0L

    fun begin(profile: MihomoProfileStore.Profile?) {
        activeProfileId = profile?.id
        MihomoCore.traffic().also {
            uploadBaseline = it.uploadTotal
            downloadBaseline = it.downloadTotal
        }
    }

    fun finish(context: Context) {
        val id = activeProfileId ?: return
        val traffic = MihomoCore.traffic()
        val old = totals(context, id)
        prefs(context).edit()
            .putLong("$id.upload", old.upload + (traffic.uploadTotal - uploadBaseline).coerceAtLeast(0))
            .putLong("$id.download", old.download + (traffic.downloadTotal - downloadBaseline).coerceAtLeast(0))
            .commit()
        activeProfileId = null
    }

    fun totals(context: Context, profileId: String): Totals = Totals(
        upload = prefs(context).getLong("$profileId.upload", 0),
        download = prefs(context).getLong("$profileId.download", 0),
    )

    fun reset(context: Context, profileId: String) {
        prefs(context).edit().remove("$profileId.upload").remove("$profileId.download").commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
