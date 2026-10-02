package com.mikubox.mihomo.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the app-side Mihomo DNS block and how it combines with the active
 * profile's own `dns:` section.
 *
 * [DnsSource.AUTO] (the default) keeps the DNS configuration a Clash-format
 * profile brings in and only falls back to the app block when the profile has
 * no `dns:` mapping; [DnsSource.APP] always replaces it; [DnsSource.CONFIG]
 * always keeps it.
 *
 * This is a read-only store at runtime: the user-facing DNS controls are the
 * structured [DnsOverrides] panel (pref_core_settings.xml `dns.*`), and the
 * per-call write API (setSource/setYaml/resetYaml/resetAll) was removed as
 * dead code — nothing wired a source-mode or custom-YAML editor to it.
 *
 * The YAML stored here represents the content inside Mihomo's top-level
 * `dns:` mapping. Callers are responsible for inserting it under `dns:`.
 */
object MihomoDnsSettings {

    private const val PREFS_NAME = "miku_dns_settings"

    private const val KEY_SOURCE = "dns_source"

    /** Legacy boolean replaced by [KEY_SOURCE]; read once for migration. */
    private const val KEY_OVERRIDE_ENABLED = "override_enabled"

    private const val KEY_OVERRIDE_YAML = "override_yaml"

    private const val LEGACY_OVERRIDE_ENABLED = true

    /**
     * How the app DNS block combines with the profile's `dns:` section.
     */
    enum class DnsSource {
        /** Use the profile's `dns:` when it declares one, else the app block. */
        AUTO,

        /** Always replace the profile's `dns:` with the app block. */
        APP,

        /** Always keep the profile's `dns:` (Mihomo defaults when absent). */
        CONFIG,
    }

    /**
     * Region-neutral DNS defaults for Mihomo TUN mode.
     *
     * Design goals:
     * - Avoid dependence on a single regional DNS provider.
     * - Keep proxy-node hostname resolution independent from proxy routing.
     * - Preserve compatibility with private networks and captive portals.
     * - Avoid returning Fake-IP addresses for LAN, connectivity-check, NTP,
     *   STUN and other address-sensitive services.
     * - Prefer encrypted DNS for normal public-domain resolution.
     */
    val DEFAULT_YAML: String = """
        enable: true
        ipv6: false
        enhanced-mode: fake-ip

        cache-algorithm: arc
        use-hosts: true
        use-system-hosts: true
        prefer-h3: true
        respect-rules: false

        fake-ip-filter:
          # Private and local network domains
          - "*.lan"
          - "*.local"
          - "*.localdomain"
          - "*.home.arpa"
          - "+.home.arpa"

          # Network login and captive-portal detection
          - "captive.apple.com"
          - "connectivitycheck.gstatic.com"
          - "connectivitycheck.android.com"
          - "clients3.google.com"
          - "www.msftconnecttest.com"
          - "www.msftncsi.com"

          # Time synchronization
          - "time.*.com"
          - "time.*.edu"
          - "time.android.com"
          - "time.apple.com"
          - "+.pool.ntp.org"

          # STUN and real-address-sensitive services
          - "stun.*"
          - "stun.*.*"
          - "+.stun.*"
          - "+.stun.*.*"

          # Common device discovery services
          - "+.m2m"
          - "+.bogon"
          - "+.invalid"

        # Bootstrap resolvers used to resolve encrypted DNS server hostnames.
        # These entries must be IP addresses.
        default-nameserver:
          - 119.29.29.29
          - 1.1.1.1
          - 8.8.8.8
          - 9.9.9.9

        # Public-domain resolution.
        # Multiple independent providers improve regional availability.
        nameserver:
          - https://cloudflare-dns.com/dns-query
          - https://doh.pub/dns-query
          - https://dns.google/dns-query
          - https://dns.quad9.net/dns-query

        # Resolve proxy-server hostnames outside the proxy routing path to
        # prevent bootstrap loops when a node address is a domain name.
        proxy-server-nameserver:
          - 119.29.29.29
          - 1.1.1.1
          - 8.8.8.8
          - 9.9.9.9

        # Direct connections may use the local network resolver first. This
        # improves compatibility with private DNS zones, office networks,
        # hotels, schools and captive portals.
        direct-nameserver:
          - system
          - 101.101.101.101
          - 119.29.29.29

        direct-nameserver-follow-policy: false
    """.trimIndent()

    /**
     * The active combination mode. Migrates from the legacy override boolean
     * on first read: enabled maps to [DnsSource.AUTO], disabled to
     * [DnsSource.CONFIG].
     */
    fun source(context: Context): DnsSource {
        val stored = preferences(context).getString(KEY_SOURCE, null)
        if (stored != null) {
            return runCatching { DnsSource.valueOf(stored) }.getOrDefault(DnsSource.AUTO)
        }
        val legacyEnabled = preferences(context).getBoolean(KEY_OVERRIDE_ENABLED, LEGACY_OVERRIDE_ENABLED)
        return if (legacyEnabled) DnsSource.AUTO else DnsSource.CONFIG
    }

    /**
     * Returns the user-defined YAML or [DEFAULT_YAML] when no valid custom
     * configuration is stored.
     */
    fun yaml(context: Context): String {
        val storedYaml = preferences(context).getString(KEY_OVERRIDE_YAML, null)

        return storedYaml
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: DEFAULT_YAML
    }

    /**
     * Returns the DNS YAML to inject during core startup, or an empty string to
     * keep the profile's own `dns:` section, based on the active
     * [DnsSource] and the profile configuration.
     */
    fun effectiveOverride(context: Context, configYaml: String): String = when (source(context)) {
        DnsSource.APP -> yaml(context)
        DnsSource.CONFIG -> ""
        DnsSource.AUTO -> if (configHasUsableDns(configYaml)) "" else yaml(context)
    }

    /**
     * Whether the profile brings a DNS block the core can answer with. A block
     * that is switched off does not count: the VPN's TUN captures every resolver
     * query, so the core would hijack them and never reply, which reaches the
     * user as a connection without internet.
     */
    fun configHasUsableDns(config: String): Boolean = configHasDns(config) && !configDnsDisabled(config)

    /**
     * Whether the profile's `dns:` block is explicitly disabled (`enable: false`
     * and its YAML spellings). Line-based like [configHasDns], and it only
     * considers keys at the block's own indentation level so a nested `enable`
     * cannot be mistaken for the block switch.
     */
    fun configDnsDisabled(config: String): Boolean {
        val lines = config.lines()
        val headerIndex = lines.indexOfFirst { it.startsWith("dns:") }
        if (headerIndex < 0) return false

        var childIndent = -1
        for (index in headerIndex + 1 until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val indent = line.indexOfFirst { !it.isWhitespace() }
            if (indent == 0) return false // a new top-level key ends the block
            if (childIndent < 0) childIndent = indent
            if (indent != childIndent) continue
            if (!trimmed.startsWith("enable:")) continue
            val value = trimmed.removePrefix("enable:").trim().substringBefore(' ').lowercase()
            return value in FALSE_VALUES
        }
        return false
    }

    private val FALSE_VALUES = setOf("false", "no", "off", "0")

    /**
     * Whether a Mihomo configuration declares a top-level `dns:` mapping with
     * content. Line-based on purpose: the app never re-serializes profile YAML,
     * so a structural scan for this single key stays reliable.
     */
    fun configHasDns(config: String): Boolean {
        val lines = config.lines()
        val headerIndex = lines.indexOfFirst { it.startsWith("dns:") }
        if (headerIndex < 0) return false

        val inline = lines[headerIndex].substringAfter(':').trim()
        if (inline.isNotEmpty()) return inline !in setOf("null", "~", "{}", "{ }")

        // Block form: a mapping exists only when an indented child key appears
        // before the next top-level key.
        for (line in lines.listIterator(headerIndex + 1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            return line.startsWith(" ") || line.startsWith("\t")
        }
        return false
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}
