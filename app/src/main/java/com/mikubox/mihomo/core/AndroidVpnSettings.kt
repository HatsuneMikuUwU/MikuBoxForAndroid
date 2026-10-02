package com.mikubox.mihomo.core

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.Utils

/** Android VpnService interface options; no core configuration translation. */
object AndroidVpnSettings {
    fun allowBypass(context: Context): Boolean = CoreOverrides.extra(context, "vpn.allow-bypass").toBooleanStrictOrNull() ?: true
    fun ipv6Inbound(context: Context): Boolean = CoreOverrides.extra(context, "vpn.ipv6-inbound").toBooleanStrictOrNull() ?: MihomoCoreSettings.ipv6(context)
    fun proxyExclusions(context: Context): List<String> = CoreOverrides.extra(context, "vpn.proxy-exclusions").split(Regex("[\\s,]+")).filter(String::isNotBlank)

    /** CPU wake lock belongs to Android, independent of the proxy core. */
    fun keepAwake(context: Context): Boolean = MmkvManager.decodeSettingsBool(
        AppConfig.PREF_KEEP_AWAKE, CoreOverrides.wakeLock(context) == CoreOverrides.ON,
    )

    fun setKeepAwake(context: Context, enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_KEEP_AWAKE, enabled)
        if (com.miku.ray.core.CoreServiceManager.isRunning()) {
            // Package-qualified: the service's onStartCommand matches the full
            // action, so the bare suffix would be dropped without a word.
            context.startService(android.content.Intent(context, com.mikubox.mihomo.service.MikuVpnService::class.java)
                .setAction(com.mikubox.mihomo.service.MikuVpnService.vpnAction(
                    context.packageName, com.mikubox.mihomo.service.MikuVpnService.ACTION_REFRESH)))
        }
    }


    // ---------------------------------------------------------------- tunnel

    /** `pref_vpn_mtu`; MikuRay stores it as text. */
    fun mtu(context: Context): Int = mtuFrom(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_MTU))

    /**
     * `pref_vpn_dns` — the resolver the tunnel advertises to the apps inside it.
     *
     * The core hijacks port 53 regardless, so this only decides which address the
     * apps are told to ask. An empty setting keeps the tunnel's own address.
     */
    fun tunDnsServers(context: Context): List<String> =
        SettingsManager.getVpnDnsServers().filter { Utils.isPureIpAddress(it) }

    /**
     * `pref_vpn_bypass_lan` — "0" routes the LAN through the tunnel, anything else
     * leaves it on the underlying network. The reference client does that by
     * installing only the public ranges as routes instead of a default route, and
     * that is what [publicRoutes] is for.
     */
    fun bypassLan(context: Context): Boolean =
        bypassLanFrom(MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN))

    /** The stored text behind [bypassLan], for the tunnel's own log line. */
    fun bypassLanRaw(context: Context): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.DEFAULT_VPN_BYPASS_LAN)
            ?: "unset"

    /** The routes to install when the LAN is left alone: everything but private space. */
    fun publicRoutes(): List<String> = AppConfig.ROUTED_IP_LIST

    /**
     * `pref_vpn_interface_address_config_index` — which address pair the tunnel
     * takes. The presets are MikuRay's own table, read through its own accessor so
     * the two cannot drift apart.
     */
    fun interfaceAddress(context: Context): com.miku.ray.enums.VpnInterfaceAddressConfig =
        SettingsManager.getCurrentVpnInterfaceAddressConfig()

    /**
     * The parse behind [bypassLan], separate so it can be checked without the
     * settings store (which is native and unavailable off-device).
     *
     * "1" is the only value that means bypass: "2" is an explicit *do not*, and
     * "0" — "follow config" — leaves the decision to the profile, which in this
     * app means the default route stays and the config's own rules decide where
     * private addresses go. That is how MikuRay reads the same setting in
     * `SettingsManager.routingRulesetsBypassLan`.
     */
    internal fun bypassLanFrom(stored: String?): Boolean = stored == "1"

    /** [mtu], clamped to what a TUN accepts. */
    internal fun mtuFrom(stored: String?): Int =
        (stored?.trim()?.toIntOrNull() ?: DEFAULT_MTU).coerceIn(MIN_MTU, MAX_MTU)


    /**
     * The per-app choice: `pref_per_app_proxy` gates the feature,
     * `pref_bypass_apps` says which way round the list is, and
     * `pref_per_app_proxy_set` holds the packages.
     */
    fun perAppMode(context: Context): PerAppMode = perAppModeFrom(
        enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false),
        bypassSelected = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false),
    )

    /** The mapping behind [perAppMode], on its own for the same reason as [bypassLanFrom]. */
    internal fun perAppModeFrom(enabled: Boolean, bypassSelected: Boolean): PerAppMode = when {
        !enabled -> PerAppMode.ALL
        bypassSelected -> PerAppMode.BYPASS_SELECTED
        else -> PerAppMode.ONLY_SELECTED
    }

    fun perAppPackages(context: Context): Set<String> =
        MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toSet().orEmpty()

    /**
     * `pref_append_http_proxy` hands the core's HTTP port to the platform, which
     * honours it on Android 10 and later.
     */
    fun appendHttpProxy(context: Context): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY, false)

    enum class PerAppMode { ALL, ONLY_SELECTED, BYPASS_SELECTED }

    private const val MIN_MTU = 1280
    private const val MAX_MTU = 9_000
    private const val DEFAULT_MTU = 1500
}
