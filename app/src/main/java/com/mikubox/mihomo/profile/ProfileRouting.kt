package com.mikubox.mihomo.profile

import android.content.Context
import com.miku.ray.ui.server.ConfigDocument

/** Filters runtime rules only; subscription refreshes never destroy the source. */
object ProfileRouting {
    private fun prefs(context: Context) = context.getSharedPreferences("mihomo_profiles", Context.MODE_PRIVATE)
    fun enabled(context: Context, id: String) = prefs(context).getBoolean("routing:$id", true)
    fun disabledPolicies(context: Context, id: String): Set<String> = prefs(context).getStringSet("routing-policies:$id", emptySet()).orEmpty().toSet()
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        check(prefs(context).edit().putBoolean("routing:$id", enabled).commit())
    }
    fun setPolicy(context: Context, id: String, policy: String, enabled: Boolean) {
        synchronized(this) {
            val disabled = disabledPolicies(context, id).toMutableSet()
            if (enabled) disabled.remove(policy) else disabled.add(policy)
            check(prefs(context).edit().putStringSet("routing-policies:$id", disabled).commit())
        }
    }
    fun apply(context: Context, profile: MihomoProfileStore.Profile): String =
        filter(profile.config, enabled(context, profile.id), disabledPolicies(context, profile.id))

    internal fun filter(config: String, enabled: Boolean, disabled: Set<String>): String {
        if (enabled && disabled.isEmpty()) return config
        val document = ConfigDocument.parse(config)
        val rules = ConfigDocument.rules(document)
        // Removing rules preserves order; unmatched traffic follows an explicit DIRECT fallback.
        val kept = if (enabled) rules.filter { ConfigDocument.policy(it) !in disabled } else emptyList()
        document["rules"] = if (kept.any { it.substringBefore(',').trim().equals("MATCH", true) }) kept else kept + "MATCH,DIRECT"
        return ConfigDocument.dump(document)
    }
}
