package com.mikubox.mihomo.profile

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.mikubox.mihomo.R
import java.util.UUID

/**
 * Non-UI replacement for UwU's profile/group database.
 *
 * A profile is a complete Mihomo configuration rather than a sing-box node.
 * That preserves providers, proxy-groups and rules exactly as supplied.
 */
object MihomoProfileStore {

    data class Profile(
        val id: String,
        val name: String,
        val config: String,
        val subscriptionUrl: String? = null,
        val updateIntervalMinutes: Long = 0,
        val updateWhenConnectedOnly: Boolean = false,
        /** Fetch subscription updates through the tunnel instead of directly. */
        val updateThroughProxy: Boolean = false,
        val updatedAtMillis: Long = 0,
        val pinned: Boolean = false,
        val groupId: String = com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID,
    ) {
        val isSubscription: Boolean get() = !subscriptionUrl.isNullOrBlank()
    }

    private const val PREFS = "mihomo_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_SELECTED = "selected"

    fun profiles(context: Context): List<Profile> = synchronized(this) {
        val raw = prefs(context).getString(KEY_PROFILES, "[]") ?: "[]"
        runCatching {
            JSONArray(raw).let { array ->
                // One malformed entry must not wipe every profile: skip it and
                // keep the rest readable.
                (0 until array.length()).mapNotNull { index ->
                    runCatching { array.getJSONObject(index).toProfile(context) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
    }

    fun selected(context: Context): Profile? {
        val selectedId = prefs(context).getString(KEY_SELECTED, null) ?: return null
        return profiles(context).firstOrNull { it.id == selectedId }
    }

    fun activeConfig(context: Context): String =
        selected(context)?.config ?: DEFAULT_CONFIG

    fun create(context: Context, name: String, config: String, groupId: String = com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID): Profile {
        require(config.isNotBlank()) { context.getString(R.string.error_mihomo_config_blank) }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { context.getString(R.string.profile_default_name) },
            config = config,
            groupId = groupId,
            updatedAtMillis = System.currentTimeMillis(),
        )
        synchronized(this) {
            replaceProfiles(context, profiles(context) + profile)
            if (selected(context) == null) select(context, profile.id)
        }
        return profile
    }

    fun createSubscription(
        context: Context,
        name: String,
        url: String,
        intervalMinutes: Long = 24 * 60,
        updateWhenConnectedOnly: Boolean = false,
        updateThroughProxy: Boolean = false,
        groupId: String = com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID,
    ): Profile {
        require(url.isNotBlank()) { context.getString(R.string.error_subscription_url_blank) }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { context.getString(R.string.profile_subscription_default_name) },
            config = "", // A subscription must be downloaded before it can connect.
            subscriptionUrl = url,
            groupId = groupId,
            updateIntervalMinutes = if (intervalMinutes <= 0) 0 else intervalMinutes.coerceAtLeast(15),
            updateWhenConnectedOnly = updateWhenConnectedOnly,
            updateThroughProxy = updateThroughProxy,
        )
        synchronized(this) {
            replaceProfiles(context, profiles(context) + profile)
            if (selected(context) == null) select(context, profile.id)
        }
        MihomoSubscriptionUpdater.reconfigure(context)
        return profile
    }

    fun update(context: Context, profile: Profile) {
        synchronized(this) {
            val updated = profiles(context).map { if (it.id == profile.id) profile else it }
            require(updated.any { it.id == profile.id }) {
                context.getString(R.string.error_unknown_profile, profile.id)
            }
            replaceProfiles(context, updated)
        }
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    /** Commit a download only if its source and content have not changed meanwhile. */
    fun updateSubscription(context: Context, source: Profile, config: String) = synchronized(this) {
        require(config.isNotBlank()) { context.getString(R.string.error_mihomo_config_blank) }
        val current = profiles(context)
        val latest = current.firstOrNull { it.id == source.id }
            ?: error(context.getString(R.string.error_unknown_profile, source.id))
        check(latest.subscriptionUrl == source.subscriptionUrl && latest.config == source.config &&
            latest.updatedAtMillis == source.updatedAtMillis) { "Subscription changed during download" }
        val updated = latest.copy(config = config, updatedAtMillis = System.currentTimeMillis())
        replaceProfiles(context, current.map { if (it.id == updated.id) updated else it })
        // Content refreshes do not change the periodic schedule.
    }

    fun select(context: Context, profileId: String) {
        require(profiles(context).any { it.id == profileId }) {
            context.getString(R.string.error_unknown_profile, profileId)
        }
        prefs(context).edit().putString(KEY_SELECTED, profileId).commit()
    }

    /** Removing a group preserves profiles, selection and subscription settings atomically. */
    fun deleteGroup(context: Context, groupId: String) = synchronized(this) {
        require(groupId != com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID) {
            context.getString(R.string.mihomo_group_default_undeletable)
        }
        val groups = ProfileGroups.list(context)
        require(groups.any { it.id == groupId }) { context.getString(R.string.mihomo_group_missing) }
        val remaining = JSONArray().also { array ->
            groups.filter { it.id != groupId && it.id != com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID }
                .forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        }
        val moved = JSONArray().also { array ->
            profiles(context).forEach { profile ->
                array.put((if (profile.groupId == groupId) profile.copy(groupId = com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID) else profile).toJson())
            }
        }
        check(prefs(context).edit().putString("groups", remaining.toString())
            .putString(KEY_PROFILES, moved.toString()).commit())
    }

    fun remove(context: Context, profileId: String) {
        com.mikubox.mihomo.core.ScriptLibrary.removeProfile(context, profileId)
        synchronized(this) {
            val remaining = profiles(context).filterNot { it.id == profileId }
            replaceProfiles(context, remaining)
            if (prefs(context).getString(KEY_SELECTED, null) == profileId) {
                prefs(context).edit().putString(KEY_SELECTED, remaining.firstOrNull()?.id).commit()
            }
        }
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    /**
     * Reorders profiles so [orderedIds] lead the list in that order; anything
     * not mentioned keeps its previous relative position behind them. Called
     * after a drag on the home list.
     */
    fun reorder(context: Context, orderedIds: List<String>) {
        synchronized(this) {
            val current = profiles(context)
            val byId = current.associateBy { it.id }
            val moved = orderedIds.distinct().mapNotNull { byId[it] }
            if (moved.isEmpty()) return
            val movedIds = moved.map { it.id }.toSet()
            val rest = current.filterNot { it.id in movedIds }
            replaceProfiles(context, moved + rest)
        }
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    fun replaceActiveConfig(context: Context, config: String) {
        val active = selected(context)
        if (active == null) {
            create(context, context.getString(R.string.profile_default_name), config)
        } else {
            update(context, active.copy(config = config, updatedAtMillis = System.currentTimeMillis()))
        }
    }

    fun exportBackup(context: Context): String = synchronized(this) {
        JSONObject().apply {
            put("version", 1)
            put("groups", JSONArray(prefs(context).getString("groups", "[]")))
            put("selected", prefs(context).getString(KEY_SELECTED, null))
            // "Connect after device boot" is MikuRay's setting, and its own
            // receiver and preference carry it; the backup only has to say which
            // profile was selected.
            put("profiles", JSONArray().also { array -> profiles(context).forEach { array.put(it.toJson()) } })
        }.toString()
    }

    fun restoreBackup(context: Context, backup: String) {
        val root = JSONObject(backup)
        val imported = root.getJSONArray("profiles")
        val restored = List(imported.length()) { imported.getJSONObject(it).toProfile(context) }
        replaceProfiles(context, restored)
        val selected = root.optString("selected").takeIf { id -> restored.any { it.id == id } }
        prefs(context).edit()
            .putString("groups", (root.optJSONArray("groups") ?: JSONArray()).toString())
            .putString(KEY_SELECTED, selected ?: restored.firstOrNull()?.id)
            .commit()
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    private fun replaceProfiles(context: Context, profiles: List<Profile>) = synchronized(this) {
        val json = JSONArray().also { array -> profiles.forEach { array.put(it.toJson()) } }
        prefs(context).edit().putString(KEY_PROFILES, json.toString()).commit()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun Profile.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("config", config)
        put("subscriptionUrl", subscriptionUrl)
        put("updateIntervalMinutes", updateIntervalMinutes)
        put("updateWhenConnectedOnly", updateWhenConnectedOnly)
        // The subscription screen's "update through the proxy" switch: without a
        // line here the choice was dropped on every save.
        put("updateThroughProxy", updateThroughProxy)
        put("updatedAtMillis", updatedAtMillis)
        put("pinned", pinned)
        put("groupId", groupId)
    }

    private fun JSONObject.toProfile(context: Context) = Profile(
        id = getString("id"),
        name = optString("name", context.getString(R.string.profile_default_name)),
        config = getString("config"),
        subscriptionUrl = if (isNull("subscriptionUrl")) null else optString("subscriptionUrl").ifBlank { null },
        updateIntervalMinutes = optLong("updateIntervalMinutes"),
        updateWhenConnectedOnly = optBoolean("updateWhenConnectedOnly"),
        updateThroughProxy = optBoolean("updateThroughProxy"),
        updatedAtMillis = optLong("updatedAtMillis"),
        pinned = optBoolean("pinned"),
        groupId = optString("groupId", com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID),
    )

    private val DEFAULT_CONFIG = """
        mode: rule
        log-level: info
        dns:
          enable: true
          nameserver:
            - https://1.1.1.1/dns-query
        rules:
          - MATCH,DIRECT
    """.trimIndent()
}
