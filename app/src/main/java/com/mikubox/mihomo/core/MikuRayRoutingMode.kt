package com.mikubox.mihomo.core

import android.content.Context
import com.miku.ray.MikuRouting
import com.miku.ray.util.LogUtil
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import com.mikubox.mihomo.profile.MihomoProfileStore

/** Offline state is scoped to the complete profile, never to a node's display name. */
object MikuRayRoutingMode : MikuRouting.Impl {
    private val context get() = checkNotNull(MikuRayBridgeContext.application)
    @Volatile private var activeProfileId: String? = null
    private fun prefs(context: Context) = context.getSharedPreferences("mihomo_routing_choices", 0)

    fun configuredOptions(config: String): List<MikuRouting.Exit> {
        val root = parse(config)
        fun entries(key: String, group: Boolean) = (root[key] as? List<*>).orEmpty().mapNotNull {
            val name = (it as? Map<*, *>)?.get("name") as? String
            name?.takeIf { it.isNotBlank() && it != "GLOBAL" }?.let { MikuRouting.Exit(it, group) }
        }
        val options = (entries("proxy-groups", true) + entries("proxies", false) + MikuRouting.Exit("DIRECT", false)).distinctBy { it.name }
        val global = (root["proxy-groups"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["name"] == "GLOBAL" }
            ?: return options
        val explicit = (global["proxies"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val includeNodes = global["include-all"] == true || global["include-all-proxies"] == true
        return options.filter { it.name in explicit || (includeNodes && !it.group && it.name != "DIRECT") }
    }

    private fun parse(config: String): Map<*, *> = runCatching {
        Yaml(SafeConstructor(LoaderOptions())).load<Any>(config) as? Map<*, *> ?: emptyMap<Any, Any>()
    }.getOrDefault(emptyMap<Any, Any>())

    override fun state(): MikuRouting.State {
        val profile = MihomoProfileStore.selected(context)
        val config = profile?.config.orEmpty()
        val live = profile != null && profile.id == activeProfileId && com.miku.ray.MikuCoreBridge.isRunning()
        val options = if (live) RoutingMode.globalExitOptions().map { MikuRouting.Exit(it.name, it.isGroup, it.delay, it.type) }
            .also { saveCache(profile.id, config, it) } else {
            val cached = runCatching { JSONObject(prefs(context).getString("cache:${profile?.id}", "{}")!!) }.getOrDefault(JSONObject())
            // Provider nodes are usable offline only when discovered for this exact config.
            if (cached.optString("config") == config) {
                val array = cached.optJSONArray("options") ?: JSONArray()
                (0 until array.length()).map { i -> array.getJSONObject(i).let { MikuRouting.Exit(it.getString("name"), it.getBoolean("group")) } }
            } else configuredOptions(config)
        }
        val selected = if (live) RoutingMode.globalExit() else prefs(context).getString("exit:${profile?.id}", null)
        val stored = MihomoCoreSettings.mode(context).value
        val mode = stored ?: (parse(config)["mode"] as? String)?.lowercase()?.takeIf { it in listOf("rule", "global", "direct") } ?: "rule"
        return MikuRouting.State(mode, selected?.takeIf { name -> options.any { it.name == name } }, options)
    }

    private fun saveCache(id: String, config: String, options: List<MikuRouting.Exit>) {
        val value = JSONObject().put("config", config).put("options", JSONArray().apply {
            options.forEach { put(JSONObject().put("name", it.name).put("group", it.group)) }
        }).toString()
        val prefs = prefs(context)
        if (prefs.getString("cache:$id", null) != value) prefs.edit().putString("cache:$id", value).apply()
    }

    override fun mode(value: String): Boolean {
        val mode = MihomoCoreSettings.ProxyMode.entries.firstOrNull { it.value == value } ?: return false
        val runningAtCheck = com.miku.ray.MikuCoreBridge.isRunning()
        val appliedLive = runningAtCheck && MihomoCore.setMode(value)
        if (runningAtCheck && !appliedLive) return false
        MihomoCoreSettings.setMode(context, mode)
        // Connect race: onStarted (executor thread) can read the stored value
        // before the persist above lands, and the live-apply check ran while
        // the core was still down. If the core is up now, apply the
        // just-persisted value once or the core keeps the previous mode until
        // the next reconnect.
        val applied = appliedLive ||
            (com.miku.ray.MikuCoreBridge.isRunning() && MihomoCore.setMode(value))
        if (applied) notifyTunnelRechosen(context)
        return true
    }

    override fun exit(name: String): Boolean {
        val profile = MihomoProfileStore.selected(context) ?: return false
        if (state().options.none { it.name == name }) return false
        val runningAtCheck = profile.id == activeProfileId && com.miku.ray.MikuCoreBridge.isRunning()
        if (runningAtCheck && !RoutingMode.selectGlobalExit(name)) return false
        val stored = prefs(context).edit().putString("exit:${profile.id}", name).commit()
        // Same connect race as mode(): re-select once when the core is up now
        // but the live selection above did not happen.
        val applied = runningAtCheck ||
            (profile.id == activeProfileId && com.miku.ray.MikuCoreBridge.isRunning() && RoutingMode.selectGlobalExit(name))
        if (applied) notifyTunnelRechosen(context)
        return stored
    }

    /**
     * A live switch moves traffic to a different exit without touching the
     * core's counters, so nothing else tells the notification: it kept quoting
     * the previous node's exit until the five-minute TTL ran out. The refresh
     * drops the cached reading and re-probes; the home screen's own measure
     * path is already re-run by its selection-changed hook.
     */
    private fun notifyTunnelRechosen(context: Context) {
        com.mikubox.mihomo.service.VpnController.refresh(context)
    }

    /** Called on the service's serialized core executor, before publishing connected state. */
    fun onStarted(context: Context, profileId: String?) {
        MihomoCoreSettings.mode(context).value?.let { MihomoCore.setMode(it) }
        activeProfileId = profileId
        if (profileId == null) return
        val name = prefs(context).getString("exit:$profileId", null) ?: return
        if (!RoutingMode.selectGlobalExit(name)) {
            // Removed/changed provider nodes must not leave a stale apparent selection.
            prefs(context).edit().remove("exit:$profileId").apply()
            LogUtil.w(message = "Saved global exit is no longer available: $name")
        }
    }
}
