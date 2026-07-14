package top.uwu.mikubox.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the user-configurable Mihomo DNS override.
 *
 * When enabled, this DNS mapping replaces the `dns:` section supplied by the
 * active profile. The default configuration is designed for TUN mode and for
 * users across multiple Asian regions without depending on a single country's
 * DNS infrastructure.
 *
 * The YAML stored here represents the content inside Mihomo's top-level
 * `dns:` mapping. Callers are responsible for inserting it under `dns:`.
 */
object MihomoDnsSettings {

    private const val PREFS_NAME = "miku_dns_settings"

    private const val KEY_OVERRIDE_ENABLED = "override_enabled"
    private const val KEY_OVERRIDE_YAML = "override_yaml"

    private const val DEFAULT_OVERRIDE_ENABLED = true

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
     * Whether the app-provided DNS block replaces the profile's DNS section.
     */
    fun overrideEnabled(context: Context): Boolean =
        preferences(context).getBoolean(
            KEY_OVERRIDE_ENABLED,
            DEFAULT_OVERRIDE_ENABLED,
        )

    /**
     * Enables or disables the DNS override.
     *
     * Uses [SharedPreferences.Editor.apply] because no caller needs to block
     * until the preference has been synchronously written to disk.
     */
    fun setOverrideEnabled(context: Context, enabled: Boolean) {
        preferences(context)
            .edit()
            .putBoolean(KEY_OVERRIDE_ENABLED, enabled)
            .apply()
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
     * Stores a custom Mihomo DNS mapping.
     *
     * Blank content is treated as a request to restore the default mapping.
     */
    fun setYaml(context: Context, yaml: String) {
        val normalizedYaml = yaml.trim()

        preferences(context).edit().apply {
            if (normalizedYaml.isEmpty()) {
                remove(KEY_OVERRIDE_YAML)
            } else {
                putString(KEY_OVERRIDE_YAML, normalizedYaml)
            }
        }.apply()
    }

    /**
     * Removes the custom YAML while preserving the current enabled state.
     */
    fun resetYaml(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_OVERRIDE_YAML)
            .apply()
    }

    /**
     * Restores all DNS settings to their application defaults.
     */
    fun resetAll(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_OVERRIDE_ENABLED)
            .remove(KEY_OVERRIDE_YAML)
            .apply()
    }

    /**
     * Returns the DNS YAML to inject during core startup.
     *
     * An empty string means that the original profile DNS section should be
     * retained without modification.
     */
    fun effectiveOverride(context: Context): String =
        if (overrideEnabled(context)) yaml(context) else ""

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}
