package com.mikubox.mihomo.profile

import android.content.Context
import org.json.JSONObject
import com.mikubox.mihomo.core.MihomoCore

/**
 * Persistent per-profile traffic accounting, equivalent to UwU's profile traffic
 * log.
 *
 * The core reports what the running session moved in total and how that splits
 * across nodes and proxy groups (a connection counts for every proxy it passed
 * through, so a group carries the sum of its members). Both are cumulative for
 * the session, so this store keeps a baseline and folds only the difference into
 * the profile's stored numbers: the service checkpoints that every so often, and
 * a connection that ends abruptly still keeps everything up to the last
 * checkpoint instead of losing the whole session.
 */
object MihomoTrafficStore {

    data class Totals(val upload: Long, val download: Long)

    private const val PREFS = "mihomo_traffic"
    private const val KEY_PROXY_SUFFIX = ".proxy"

    /**
     * begin/finish/checkpoint run on the VPN service's executor while screens
     * read [proxyTotals] from the main thread; every entry point takes this lock
     * so the baselines cannot tear mid-fold.
     */
    private val lock = Any()

    private var activeProfileId: String? = null
    private var uploadBaseline = 0L
    private var downloadBaseline = 0L
    private var proxyBaseline: Map<String, MihomoCore.ProxyTraffic> = emptyMap()

    /**
     * The breakdown a screen last asked for, cached for the core's own sampling
     * interval. The list screens read this once a second and every read crosses
     * JNI and parses JSON, which made the per-frame work show up while scrolling.
     * Folding always reads the counters directly, so nothing is lost by serving
     * the screens a value that is at most one sample old.
     */
    private var sessionReadAt = 0L
    private var sessionByProxy: Map<String, MihomoCore.ProxyTraffic> = emptyMap()

    private fun sampledProxyTraffic(): Map<String, MihomoCore.ProxyTraffic> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - sessionReadAt < SESSION_SAMPLE_MS) return sessionByProxy
        sessionReadAt = now
        sessionByProxy = MihomoCore.trafficByProxy()
        return sessionByProxy
    }

    /** Parsed breakdown cache, keyed by the raw preference it was parsed from. */
    private var parsedKey: String? = null
    private var parsedValue: Map<String, Totals> = emptyMap()

    fun begin(profile: MihomoProfileStore.Profile?) = synchronized(lock) {
        activeProfileId = profile?.id
        MihomoCore.traffic().also {
            uploadBaseline = it.uploadTotal
            downloadBaseline = it.downloadTotal
        }
        proxyBaseline = emptyMap()
        Unit
    }

    /** Folds everything measured so far into the profile, without ending the session. */
    fun checkpoint(context: Context) = fold(context)

    fun finish(context: Context) = synchronized(lock) {
        fold(context)
        activeProfileId = null
        Unit
    }

    fun totals(context: Context, profileId: String): Totals = Totals(
        upload = prefs(context).getLong("$profileId.upload", 0),
        download = prefs(context).getLong("$profileId.download", 0),
    )

    /**
     * The stored totals plus what the running session has moved since the last
     * fold, without writing anything.
     *
     * The session total comes from the core's own counters, which count every
     * connection — including the short ones a sampler would miss — so this is the
     * reading to show live.
     */
    fun liveTotals(context: Context, profileId: String): Totals = synchronized(lock) {
        val stored = totals(context, profileId)
        if (profileId != activeProfileId) return stored
        val traffic = MihomoCore.traffic()
        Totals(
            upload = stored.upload + (traffic.uploadTotal - uploadBaseline).coerceAtLeast(0),
            download = stored.download + (traffic.downloadTotal - downloadBaseline).coerceAtLeast(0),
        )
    }

    /**
     * Traffic each node and group carried for [profileId] so far, including the
     * part of the running session that has not been folded in yet.
     */
    fun proxyTotals(context: Context, profileId: String): Map<String, Totals> = synchronized(lock) {
        val stored = storedProxyTotals(context, profileId).toMutableMap()
        if (profileId == activeProfileId) foldPending(profileId, stored, sampledProxyTraffic())
        stored
    }

    /** Clears both the profile total and its per-node breakdown. */
    fun reset(context: Context, profileId: String) = synchronized(lock) {
        if (profileId == activeProfileId) {
            val traffic = MihomoCore.traffic()
            uploadBaseline = traffic.uploadTotal
            downloadBaseline = traffic.downloadTotal
            proxyBaseline = MihomoCore.trafficByProxy()
        }
        prefs(context).edit()
            .remove("$profileId.upload")
            .remove("$profileId.download")
            .remove("$profileId$KEY_PROXY_SUFFIX")
            .commit()
        Unit
    }

    /** Adds what the session moved since the last fold to the stored numbers. */
    private fun fold(context: Context): Unit = synchronized(lock) {
        val id = activeProfileId ?: return
        val session = MihomoCore.trafficByProxy()
        val stored = storedProxyTotals(context, id).toMutableMap()
        foldPending(id, stored, session)

        val traffic = MihomoCore.traffic()
        val previous = totals(context, id)
        prefs(context).edit()
            .putLong(
                "$id.upload",
                previous.upload + (traffic.uploadTotal - uploadBaseline).coerceAtLeast(0),
            )
            .putLong(
                "$id.download",
                previous.download + (traffic.downloadTotal - downloadBaseline).coerceAtLeast(0),
            )
            .putString("$id$KEY_PROXY_SUFFIX", encode(stored))
            .commit()

        uploadBaseline = traffic.uploadTotal
        downloadBaseline = traffic.downloadTotal
        proxyBaseline = session
    }

    /**
     * Adds the per-proxy difference between [session] and the last fold into
     * [stored]. Keeping the baseline means a checkpoint never counts the same
     * bytes twice.
     */
    private fun foldPending(
        profileId: String,
        stored: MutableMap<String, Totals>,
        session: Map<String, MihomoCore.ProxyTraffic>,
    ) {
        if (profileId != activeProfileId) return
        val folded = proxyBaseline
        session.forEach { (name, current) ->
            val last = folded[name]
            val upload = (current.upload - (last?.upload ?: 0L)).coerceAtLeast(0)
            val download = (current.download - (last?.download ?: 0L)).coerceAtLeast(0)
            if (upload == 0L && download == 0L) return@forEach
            val base = stored[name] ?: Totals(0, 0)
            stored[name] = Totals(base.upload + upload, base.download + download)
        }
    }

    private fun encode(totals: Map<String, Totals>): String = JSONObject().also { root ->
        totals.forEach { (name, value) ->
            root.put(
                name,
                JSONObject().put("upload", value.upload).put("download", value.download),
            )
        }
    }.toString()

    private fun storedProxyTotals(context: Context, profileId: String): Map<String, Totals> {
        val raw = prefs(context).getString("$profileId$KEY_PROXY_SUFFIX", null) ?: return emptyMap()
        if (raw == parsedKey) return parsedValue
        val value = runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { name ->
                    val entry = root.optJSONObject(name) ?: return@forEach
                    put(name, Totals(entry.optLong("upload"), entry.optLong("download")))
                }
            }
        }.getOrDefault(emptyMap())
        parsedKey = raw
        parsedValue = value
        return value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val SESSION_SAMPLE_MS = 500L
}
