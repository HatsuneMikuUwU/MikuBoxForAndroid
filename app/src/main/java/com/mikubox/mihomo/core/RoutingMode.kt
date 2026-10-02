package com.mikubox.mihomo.core

import android.content.Context

/**
 * The three routing modes a Clash client exposes, applied to the running core
 * straight away and remembered for the next start.
 *
 * Global mode sends every connection to the core's built-in `GLOBAL` selector,
 * whose members are all nodes and all proxy groups of the profile, so the exit
 * for that mode can be a node or a whole group. Direct mode stops proxying
 * altogether; only rule mode evaluates the profile's rules.
 */
object RoutingMode {

    /** Modes the home switch offers, in the order it shows them. */
    val SELECTABLE = listOf(
        MihomoCoreSettings.ProxyMode.RULE,
        MihomoCoreSettings.ProxyMode.GLOBAL,
        MihomoCoreSettings.ProxyMode.DIRECT,
    )

    /** The mode the user picked; [MihomoCoreSettings.ProxyMode.FOLLOW] means the profile decides. */
    fun stored(context: Context): MihomoCoreSettings.ProxyMode = MihomoCoreSettings.mode(context)

    /**
     * The mode to show as active. An explicit choice wins; otherwise the
     * profile's own `mode:` decides, which is what the core was started with
     * (the app only overrides that key when the user picked one).
     */
    fun effective(context: Context, configYaml: String): MihomoCoreSettings.ProxyMode {
        val stored = stored(context)
        if (stored != MihomoCoreSettings.ProxyMode.FOLLOW) return stored
        return fromValue(profileMode(configYaml)) ?: MihomoCoreSettings.ProxyMode.RULE
    }

    /**
     * Persists [mode] and hands it to the core, which switches over immediately.
     * Selecting a mode while the core is stopped is not wasted: the value is
     * stored and the next start applies it.
     */
    fun select(context: Context, mode: MihomoCoreSettings.ProxyMode): Boolean {
        MihomoCoreSettings.setMode(context, mode)
        val value = mode.value ?: return true
        return MihomoCore.setMode(value)
    }

    /**
     * Exit used by global mode: the member currently selected in the built-in
     * `GLOBAL` selector, or null when the core is stopped.
     */
    fun globalExit(): String? =
        MihomoCore.globalGroup()?.now?.takeIf { it.isNotBlank() && it != MihomoCore.GLOBAL_GROUP }

    /** Members offered by the global-exit picker: every group first, then every node. */
    fun globalExitOptions(): List<MihomoCore.Proxy> {
        val proxies = MihomoCore.proxies()
        val members = proxies[MihomoCore.GLOBAL_GROUP]?.all.orEmpty()
            .filter { it != MihomoCore.GLOBAL_GROUP }
            .mapNotNull { proxies[it] }
        return members.filter { it.isGroup } + members.filterNot { it.isGroup }
    }

    /** Points global mode at [name] — a node or a group — and persists the choice. */
    fun selectGlobalExit(name: String): Boolean = MihomoCore.selectProxy(MihomoCore.GLOBAL_GROUP, name)

    private fun fromValue(value: String?): MihomoCoreSettings.ProxyMode? = when (value) {
        "rule" -> MihomoCoreSettings.ProxyMode.RULE
        "global" -> MihomoCoreSettings.ProxyMode.GLOBAL
        "direct" -> MihomoCoreSettings.ProxyMode.DIRECT
        else -> null
    }

    /**
     * The profile's `mode:` value, read from the YAML because a stopped core has
     * no answer to give. Line-based like the DNS settings: the app never
     * re-serializes profile YAML.
     */
    private fun profileMode(configYaml: String): String? = configYaml.lineSequence()
        .firstOrNull { it.startsWith("mode:") }
        ?.substringAfter(':')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
}
