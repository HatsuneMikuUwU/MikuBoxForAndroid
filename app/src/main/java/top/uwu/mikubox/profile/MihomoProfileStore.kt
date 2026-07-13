package top.uwu.mikubox.profile

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
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
        val updatedAtMillis: Long = 0,
    ) {
        val isSubscription: Boolean get() = !subscriptionUrl.isNullOrBlank()
    }

    private const val PREFS = "mihomo_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_SELECTED = "selected"
    private const val KEY_AUTOSTART = "autostart"

    fun profiles(context: Context): List<Profile> = synchronized(this) {
        val raw = prefs(context).getString(KEY_PROFILES, "[]") ?: "[]"
        runCatching {
            JSONArray(raw).let { array ->
                List(array.length()) { index -> array.getJSONObject(index).toProfile() }
            }
        }.getOrDefault(emptyList())
    }

    fun selected(context: Context): Profile? {
        val selectedId = prefs(context).getString(KEY_SELECTED, null) ?: return null
        return profiles(context).firstOrNull { it.id == selectedId }
    }

    fun activeConfig(context: Context): String =
        selected(context)?.config ?: DEFAULT_CONFIG

    fun create(context: Context, name: String, config: String): Profile {
        require(config.isNotBlank()) { "Mihomo configuration must not be blank" }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Profile" },
            config = config,
            updatedAtMillis = System.currentTimeMillis(),
        )
        replaceProfiles(context, profiles(context) + profile)
        if (selected(context) == null) select(context, profile.id)
        return profile
    }

    fun createSubscription(
        context: Context,
        name: String,
        url: String,
        intervalMinutes: Long = 24 * 60,
        updateWhenConnectedOnly: Boolean = false,
    ): Profile {
        require(url.isNotBlank()) { "Subscription URL must not be blank" }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Subscription" },
            config = DEFAULT_CONFIG,
            subscriptionUrl = url,
            updateIntervalMinutes = intervalMinutes.coerceAtLeast(15),
            updateWhenConnectedOnly = updateWhenConnectedOnly,
        )
        replaceProfiles(context, profiles(context) + profile)
        if (selected(context) == null) select(context, profile.id)
        MihomoSubscriptionUpdater.reconfigure(context)
        return profile
    }

    fun update(context: Context, profile: Profile) {
        val updated = profiles(context).map { if (it.id == profile.id) profile else it }
        require(updated.any { it.id == profile.id }) { "Unknown profile ${profile.id}" }
        replaceProfiles(context, updated)
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    fun select(context: Context, profileId: String) {
        require(profiles(context).any { it.id == profileId }) { "Unknown profile $profileId" }
        prefs(context).edit().putString(KEY_SELECTED, profileId).commit()
    }

    fun remove(context: Context, profileId: String) {
        val remaining = profiles(context).filterNot { it.id == profileId }
        replaceProfiles(context, remaining)
        if (prefs(context).getString(KEY_SELECTED, null) == profileId) {
            prefs(context).edit().putString(KEY_SELECTED, remaining.firstOrNull()?.id).commit()
        }
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    fun replaceActiveConfig(context: Context, config: String) {
        val active = selected(context)
        if (active == null) {
            create(context, "Default", config)
        } else {
            update(context, active.copy(config = config, updatedAtMillis = System.currentTimeMillis()))
        }
    }

    fun exportBackup(context: Context): String = synchronized(this) {
        JSONObject().apply {
            put("version", 1)
            put("selected", prefs(context).getString(KEY_SELECTED, null))
            put("autostart", autoStart(context))
            put("profiles", JSONArray().also { array -> profiles(context).forEach { array.put(it.toJson()) } })
        }.toString()
    }

    fun restoreBackup(context: Context, backup: String) {
        val root = JSONObject(backup)
        val imported = root.getJSONArray("profiles")
        val restored = List(imported.length()) { imported.getJSONObject(it).toProfile() }
        replaceProfiles(context, restored)
        val selected = root.optString("selected").takeIf { id -> restored.any { it.id == id } }
        prefs(context).edit()
            .putString(KEY_SELECTED, selected ?: restored.firstOrNull()?.id)
            .putBoolean(KEY_AUTOSTART, root.optBoolean("autostart", false))
            .commit()
        MihomoSubscriptionUpdater.reconfigure(context)
    }

    fun autoStart(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTOSTART, false)

    fun setAutoStart(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART, enabled).commit()
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
        put("updatedAtMillis", updatedAtMillis)
    }

    private fun JSONObject.toProfile() = Profile(
        id = getString("id"),
        name = optString("name", "Profile"),
        config = getString("config"),
        subscriptionUrl = optString("subscriptionUrl").ifBlank { null },
        updateIntervalMinutes = optLong("updateIntervalMinutes"),
        updateWhenConnectedOnly = optBoolean("updateWhenConnectedOnly"),
        updatedAtMillis = optLong("updatedAtMillis"),
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
