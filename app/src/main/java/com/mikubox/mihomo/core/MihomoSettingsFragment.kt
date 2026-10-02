package com.mikubox.mihomo.core

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.*
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.ui.preference.SearchPreferenceHighlighter

/** Native settings retain the existing Core / VPN organization. */
class MihomoSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_core_settings)
        MihomoPreferenceBindings(this).apply { bindCore(); styleTitles() }
        addAutoConnectSwitch()
        CategoryStyleHelper.applyToFragment(this)
    }

    /**
     * MikuBox's own switch, built in code so the vendored preference XML stays
     * byte-for-byte with upstream MikuRay — the same pattern as the VPN
     * screen's background-protection category.
     */
    private fun addAutoConnectSwitch() {
        val autoConnect = androidx.preference.SwitchPreferenceCompat(requireContext()).apply {
            key = "autoconnect_on_start"
            isPersistent = false
            title = getString(R.string.mihomo_autoconnect_title)
            summary = getString(R.string.mihomo_autoconnect_summary)
            isChecked = MihomoCoreSettings.autoConnectOnStart(requireContext())
            setOnPreferenceChangeListener { _, newValue ->
                MihomoCoreSettings.setAutoConnectOnStart(requireContext(), newValue as Boolean)
                true
            }
        }
        preferenceScreen.addPreference(autoConnect)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SearchPreferenceHighlighter.applyFromIntent(this)
        applyEdgeToEdgeListInsets()
    }
}

class MihomoVpnSettingsFragment : com.miku.ray.ui.preference.activity.VpnSettingsActivity.VpnSettingsFragment() {
    override fun onResume() {
        super.onResume()
        AutomationPreferences.refreshNetwork(this)
        val power = requireContext().getSystemService(android.os.PowerManager::class.java)
        findPreference<Preference>("background_battery_status")?.summary =
            if (power.isIgnoringBatteryOptimizations(requireContext().packageName)) getString(R.string.mihomo_vpn_battery_ok)
            else getString(R.string.mihomo_vpn_battery_off)
        val service = com.miku.ray.core.CoreServiceManager.serviceControl as? android.net.VpnService
        findPreference<Preference>("background_always_on")?.summary = when {
            android.os.Build.VERSION.SDK_INT >= 29 && service?.isAlwaysOn == true ->
                getString(R.string.mihomo_vpn_always_on_enabled) +
                    if (service.isLockdownEnabled) getString(R.string.mihomo_vpn_lockdown_on)
                    else getString(R.string.mihomo_vpn_lockdown_off)
            else -> getString(R.string.mihomo_vpn_always_on_hint)
        }
        findPreference<Preference>("background_recovery")?.summary =
            if (com.mikubox.mihomo.service.TunnelGuard.recoveryPaused(requireContext()))
                getString(R.string.mihomo_vpn_recovery_paused)
            else getString(R.string.mihomo_vpn_recovery_summary)

    }
    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        super.onCreatePreferences(bundle, rootKey)
        MihomoPreferenceBindings(this).apply { bindVpn(); styleTitles() }
        val category = PreferenceCategory(requireContext()).apply { title = getString(R.string.mihomo_vpn_bg_category) }
        preferenceScreen.addPreference(category)
        category.addPreference(Preference(requireContext()).apply {
            key = "background_always_on"
            title = getString(R.string.mihomo_vpn_always_on_title)
            setOnPreferenceClickListener {
                openSystemSettings(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                true
            }
        })
        category.addPreference(Preference(requireContext()).apply {
            key = "background_battery_status"
            title = getString(R.string.mihomo_vpn_battery_title)
            setOnPreferenceClickListener {
                val power = requireContext().getSystemService(android.os.PowerManager::class.java)
                val intent = if (power.isIgnoringBatteryOptimizations(requireContext().packageName))
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                else android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${requireContext().packageName}"))
                openSystemSettings(intent)
                true
            }
        })
        category.addPreference(Preference(requireContext()).apply {
            title = getString(if (android.os.Build.MANUFACTURER.lowercase() in setOf("oneplus", "oppo", "realme"))
                R.string.mihomo_vpn_oem_title_coloros else R.string.mihomo_vpn_oem_title)
            summary = getString(R.string.mihomo_vpn_oem_summary)
            setOnPreferenceClickListener { openSystemSettings(appDetailsIntent()); true }
        })
        category.addPreference(Preference(requireContext()).apply {
            key = "background_recovery"
            title = getString(R.string.mihomo_vpn_recovery_title)
            isSelectable = false
        })
    }

    private fun appDetailsIntent() = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.parse("package:${requireContext().packageName}"))

    private fun openSystemSettings(intent: android.content.Intent) {
        runCatching { startActivity(intent) }.recoverCatching { startActivity(appDetailsIntent()) }
            .onFailure { Toast.makeText(requireContext(), R.string.mihomo_vpn_open_settings_failed, Toast.LENGTH_LONG).show() }

    }
}

class MihomoAdvancedSettingsFragment : com.miku.ray.ui.preference.activity.AdvancedSettingsActivity.AdvancedSettingsFragment() {
    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        super.onCreatePreferences(bundle, rootKey)
        MihomoPreferenceBindings(this).apply { bindAdvanced(); styleTitles() }
    }
}

private class MihomoPreferenceBindings(private val fragment: PreferenceFragmentCompat) {
    private val ctx get() = fragment.requireContext()
    private fun getString(id: Int) = fragment.getString(id)

    fun styleTitles() {
        if (androidx.core.os.ConfigurationCompat.getLocales(ctx.resources.configuration)[0]?.language == "en") return
        fun visit(group: PreferenceGroup) {
            for (i in 0 until group.preferenceCount) {
                val preference = group.getPreference(i)
                val title = preference.title?.toString().orEmpty()
                val split = title.indexOf(" · ")
                if (split > 0) {
                    val main = title.substring(0, split)
                    val detail = title.substring(split + 3)
                    val styled = android.text.SpannableString("$main\n$detail")
                    styled.setSpan(android.text.style.RelativeSizeSpan(14f / 17f), main.length + 1, styled.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val color = com.google.android.material.color.MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.GRAY)
                    styled.setSpan(android.text.style.ForegroundColorSpan(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 128)), main.length + 1, styled.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    preference.title = styled
                    preference.isSingleLineTitle = false
                    if (preference is DialogPreference) preference.dialogTitle = main
                }
                if (preference is PreferenceGroup) visit(preference)
            }
        }
        visit(fragment.preferenceScreen)
    }

    fun bindCore() {
        toggle("dns.append-system", CoreOverrides.extra(ctx, "dns.append-system") == "true") { CoreOverrides.setExtra(ctx, "dns.append-system", it.toString()) }
        text("dns.fallback-filter.geoip-code", CoreOverrides.extra(ctx, "dns.fallback-filter.geoip-code"), { it.isBlank() || it.matches(Regex("[A-Za-z]{2}")) }) { CoreOverrides.setExtra(ctx, "dns.fallback-filter.geoip-code", it.uppercase()) }
        listOf("geosite", "domain").forEach { field ->
            val key = "dns.fallback-filter.$field"
            text(key, CoreOverrides.extra(ctx, key), { true }) { CoreOverrides.setExtra(ctx, key, it) }
        }

        text("hosts", CoreOverrides.extra(ctx, "hosts"), { it.isBlank() || runCatching { org.json.JSONObject(it) }.isSuccess }) { CoreOverrides.setExtra(ctx, "hosts", it) }
        text("dns.nameserver-policy", CoreOverrides.extra(ctx, "dns.nameserver-policy"), { it.isBlank() || runCatching { org.json.JSONObject(it) }.isSuccess }) { CoreOverrides.setExtra(ctx, "dns.nameserver-policy", it) }
        text("dns.fallback-filter.ipcidr", DnsOverrides.fallbackIpCidr(ctx), { runCatching { VpnRoutes.parse(it) }.isSuccess }) { DnsOverrides.setFallbackIpCidr(ctx, it) }
        choice("dns.fallback-filter.geoip", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.fallbackGeoIp(ctx).toString()) { DnsOverrides.setFallbackGeoIp(ctx, it.toInt()) }
        text("global-ua", CoreOverrides.extra(ctx, "global-ua"), { !it.contains('\n') && !it.contains('\r') }) { CoreOverrides.setExtra(ctx, "global-ua", it) }
        text("authentication", CoreOverrides.extra(ctx, "authentication"), { it.isBlank() || it.lines().filter(String::isNotBlank).all { ':' in it && it.substringBefore(':').isNotBlank() && it.substringAfter(':').isNotBlank() } }) { CoreOverrides.setExtra(ctx, "authentication", it) }
        fragment.findPreference<EditTextPreference>("authentication")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { if (it.text.isNullOrBlank()) getString(R.string.mihomo_follow_profile) else "••••••" }

        choice("dns.enhanced-mode", DnsOverrides.EnhancedMode.entries.map { it.name }, DnsOverrides.EnhancedMode.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, DnsOverrides.enhancedMode(ctx).name) { DnsOverrides.setEnhancedMode(ctx, DnsOverrides.EnhancedMode.valueOf(it)) }
        // F43: the bridge force-enables the DNS module in VPN mode (the
        // profile's dns.enable=false cannot turn it off there), and the only
        // way out of DNS hijacking is an empty TUN dns-hijack list. Say both
        // here, or a disabled-in-profile DNS reads as broken. The string is an
        // app-layer key: this file's R is the vendored layer's, so qualify.
        // ListPreference is a DialogPreference, which carries dialogMessage.
        fragment.findPreference<ListPreference>("dns.enhanced-mode")?.apply {
            dialogMessage = getString(com.mikubox.mihomo.R.string.mihomo_dns_vpn_forced_hint)
        }
        text("dns.fake-ip-range", DnsOverrides.fakeIpRange(ctx), { true }) { DnsOverrides.setFakeIpRange(ctx, it) }
        text("dns.fake-ip-filter", DnsOverrides.fakeIpFilter(ctx), { true }) { DnsOverrides.setFakeIpFilter(ctx, it) }
        choice("dns.ipv6", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.ipv6(ctx).toString()) { DnsOverrides.setIpv6(ctx, it.toInt()) }
        choice("mode", MihomoCoreSettings.ProxyMode.entries.map { it.name }, MihomoCoreSettings.ProxyMode.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, MihomoCoreSettings.mode(ctx).name) {
            MihomoCoreSettings.setMode(ctx, MihomoCoreSettings.ProxyMode.valueOf(it))
            com.mikubox.mihomo.service.VpnController.restart(ctx)
        }

        toggle(com.miku.ray.AppConfig.PREF_DYNAMIC_SOCKS_PORT,
            com.miku.ray.handler.MmkvManager.decodeSettingsBool(com.miku.ray.AppConfig.PREF_DYNAMIC_SOCKS_PORT, false)) {
            com.miku.ray.handler.MmkvManager.encodeSettings(com.miku.ray.AppConfig.PREF_DYNAMIC_SOCKS_PORT, it)
        }
        fragment.findPreference<ListPreference>(com.miku.ray.AppConfig.PREF_GEO_FILES_SOURCES)?.apply {
            isPersistent = false
            value = com.miku.ray.handler.MmkvManager.decodeSettingsString(com.miku.ray.AppConfig.PREF_GEO_FILES_SOURCES)
                ?: com.miku.ray.AppConfig.GEO_FILES_SOURCES.first()
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                com.miku.ray.handler.MmkvManager.encodeSettings(com.miku.ray.AppConfig.PREF_GEO_FILES_SOURCES, newValue as String)
                // Every other core setting funnels through applyChange so a
                // connected tunnel rebuilds with the new value; this key was
                // the one holdover that silently waited for the next reconnect.
                applyChange(com.miku.ray.AppConfig.PREF_GEO_FILES_SOURCES)
                true
            }
        }

        toggle("allow-lan", MihomoCoreSettings.allowLan(ctx)) { MihomoCoreSettings.setAllowLan(ctx, it) }
        text("mixed-port", MihomoCoreSettings.mixedPort(ctx).toString(), { it.toIntOrNull() in 1..65535 }) { CoreOverrides.setMixedPort(ctx, it.toIntOrNull() ?: 0) }
        choice("log-level", MihomoCoreSettings.LogLevel.entries.map { it.name }, MihomoCoreSettings.LogLevel.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, MihomoCoreSettings.logLevel(ctx).name) { MihomoCoreSettings.setLogLevel(ctx, MihomoCoreSettings.LogLevel.valueOf(it)) }
        choice("sniffer.enable", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), CoreOverrides.sniffing(ctx).toString()) { CoreOverrides.setSniffing(ctx, it.toInt()) }
        choice("sniffer.override-destination", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), CoreOverrides.sniffOverrideDestination(ctx).toString()) { CoreOverrides.setSniffOverrideDestination(ctx, it.toInt()) }
        choice("dns.cache-algorithm", DnsOverrides.CacheAlgorithm.entries.map { it.name }, DnsOverrides.CacheAlgorithm.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, DnsOverrides.cacheAlgorithm(ctx).name) { DnsOverrides.setCacheAlgorithm(ctx, DnsOverrides.CacheAlgorithm.valueOf(it)) }
        text("dns.nameserver", DnsOverrides.nameserver(ctx), { true }) { DnsOverrides.setNameserver(ctx, it) }
        text("dns.default-nameserver", DnsOverrides.defaultNameserver(ctx), { true }) { DnsOverrides.setDefaultNameserver(ctx, it) }
        text("dns.proxy-server-nameserver", DnsOverrides.proxyServerNameserver(ctx), { true }) { DnsOverrides.setProxyServerNameserver(ctx, it) }
        text("dns.direct-nameserver", DnsOverrides.directNameserver(ctx), { true }) { DnsOverrides.setDirectNameserver(ctx, it) }
        text("dns.fallback", DnsOverrides.fallback(ctx), { true }) { DnsOverrides.setFallback(ctx, it) }
        choice("dns.respect-rules", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.respectRules(ctx).toString()) { DnsOverrides.setRespectRules(ctx, it.toInt()) }
        choice("dns.use-hosts", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.useHosts(ctx).toString()) { DnsOverrides.setUseHosts(ctx, it.toInt()) }
        choice("dns.use-system-hosts", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.useSystemHosts(ctx).toString()) { DnsOverrides.setUseSystemHosts(ctx, it.toInt()) }
        choice("dns.prefer-h3", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.preferH3(ctx).toString()) { DnsOverrides.setPreferH3(ctx, it.toInt()) }
        choice("dns.direct-nameserver-follow-policy", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), DnsOverrides.directFollowPolicy(ctx).toString()) { DnsOverrides.setDirectFollowPolicy(ctx, it.toInt()) }
    }

    fun bindVpn() {
        AutomationPreferences.bindNetwork(fragment)
        toggle("vpn.allow-bypass", AndroidVpnSettings.allowBypass(ctx)) { CoreOverrides.setExtra(ctx, "vpn.allow-bypass", it.toString()) }
        toggle("vpn.ipv6-inbound", AndroidVpnSettings.ipv6Inbound(ctx)) { CoreOverrides.setExtra(ctx, "vpn.ipv6-inbound", it.toString()) }
        text("vpn.proxy-exclusions", CoreOverrides.extra(ctx, "vpn.proxy-exclusions"), { !it.contains("://") }) { CoreOverrides.setExtra(ctx, "vpn.proxy-exclusions", it) }

        listOf("pref_vpn_bypass_lan", "pref_vpn_dns", "pref_vpn_mtu", "pref_vpn_interface_address_config_index", "pref_append_http_proxy").forEach { key ->
            fragment.findPreference<Preference>(key)?.apply {
                val previous = onPreferenceChangeListener
                setOnPreferenceChangeListener { preference, value ->
                    if (previous?.onPreferenceChange(preference, value) == false) false
                    else { applyChange(key); true }
                }
            }
        }

        text("vpn.route-address", VpnRoutes.custom(ctx), { runCatching { VpnRoutes.parse(it) }.isSuccess }) { VpnRoutes.setCustom(ctx, it) }
        fragment.findPreference<ListPreference>(com.miku.ray.AppConfig.PREF_VPN_BYPASS_LAN)?.apply {
            entries = arrayOf(getString(R.string.mihomo_route_profile), getString(R.string.mihomo_route_public), getString(R.string.mihomo_route_all), getString(R.string.mihomo_route_custom))
            entryValues = arrayOf("0", "1", "2", "3")
            title = getString(R.string.mihomo_vpn_route_mode)
        }

        text("tun.dns-hijack", CoreOverrides.dnsHijack(ctx), { value -> value.isBlank() || value.split(Regex("[\\s,]+")).all { it.matches(Regex("(?:tcp://|udp://)?(?:any|[0-9.]+|\\[[0-9a-fA-F:]+]):[0-9]{1,5}")) && it.substringAfterLast(":").toIntOrNull() in 1..65535 } }) { CoreOverrides.setDnsHijack(ctx, it) }
        fragment.findPreference<EditTextPreference>("tun.dns-hijack")?.apply {
            dialogMessage = getString(R.string.mihomo_dns_hijack_hint)
            summaryProvider = Preference.SummaryProvider<EditTextPreference> { it.text?.takeIf(String::isNotBlank) ?: getString(R.string.mihomo_disabled) }
        }
        text("tun.udp-timeout", CoreOverrides.udpTimeout(ctx).takeIf { it > 0 }?.toString().orEmpty(), { it.isEmpty() || it.toIntOrNull() in 1..86400 }) { CoreOverrides.setUdpTimeout(ctx, it.toIntOrNull() ?: 0) }
        text("tun.icmp-timeout", CoreOverrides.icmpTimeout(ctx).takeIf { it > 0 }?.toString().orEmpty(), { it.isEmpty() || it.toIntOrNull() in 1..86400 }) { CoreOverrides.setIcmpTimeout(ctx, it.toIntOrNull() ?: 0) }

        toggle("ipv6", MihomoCoreSettings.ipv6(ctx)) { MihomoCoreSettings.setIpv6(ctx, it) }
        choice("tun.stack", MihomoCoreSettings.TunStack.entries.map { it.name }, MihomoCoreSettings.TunStack.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, MihomoCoreSettings.tunStack(ctx).name) { MihomoCoreSettings.setTunStack(ctx, MihomoCoreSettings.TunStack.valueOf(it)) }
        toggle(com.miku.ray.AppConfig.PREF_KEEP_AWAKE, AndroidVpnSettings.keepAwake(ctx)) {
            AndroidVpnSettings.setKeepAwake(ctx, it)
        }
    }


    fun bindAdvanced() {
        AutomationPreferences.bindScript(fragment)
        choice("external-controller", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), CoreOverrides.controller(ctx).toString()) { CoreOverrides.setController(ctx, it.toInt()) }
        text("external-controller.address", CoreOverrides.controllerAddress(ctx), { it.substringAfterLast(':').toIntOrNull() in 1..65535 && it.substringBeforeLast(':').isNotBlank() }) { CoreOverrides.setControllerAddress(ctx, it) }
        text("external-controller.secret", CoreOverrides.controllerSecret(ctx), { it.isNotBlank() }) { CoreOverrides.setControllerSecret(ctx, it) }
        fragment.findPreference<EditTextPreference>("external-controller.secret")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { "••••••" }

        toggle("unified-delay", MihomoCoreSettings.unifiedDelay(ctx)) { MihomoCoreSettings.setUnifiedDelay(ctx, it) }
        toggle("tcp-concurrent", MihomoCoreSettings.tcpConcurrent(ctx)) { MihomoCoreSettings.setTcpConcurrent(ctx, it) }
        choice("find-process-mode", CoreOverrides.FindProcess.entries.map { it.name }, listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_process_always), getString(R.string.mihomo_process_strict), getString(R.string.mihomo_disabled)), CoreOverrides.findProcess(ctx).name) { CoreOverrides.setFindProcess(ctx, CoreOverrides.FindProcess.valueOf(it)) }
        choice("geodata-loader", CoreOverrides.GeodataLoader.entries.map { it.name }, listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_geo_standard), getString(R.string.mihomo_geo_low_memory)), CoreOverrides.geodataLoader(ctx).name) { CoreOverrides.setGeodataLoader(ctx, CoreOverrides.GeodataLoader.valueOf(it)) }
        choice("geodata-mode", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), CoreOverrides.geodataMode(ctx).toString()) { CoreOverrides.setGeodataMode(ctx, it.toInt()) }
        text("keep-alive-idle", CoreOverrides.keepAliveIdle(ctx).takeIf { it > 0 }?.toString().orEmpty(),
            { it.isEmpty() || it.toIntOrNull() in 1..86400 }) { CoreOverrides.setKeepAliveIdle(ctx, it.toIntOrNull() ?: 0) }
        choice("disable-keep-alive", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_enabled), getString(R.string.mihomo_disabled)), CoreOverrides.disableKeepAlive(ctx).toString()) { CoreOverrides.setDisableKeepAlive(ctx, it.toInt()) }
        text("keep-alive-interval", CoreOverrides.keepAliveInterval(ctx).takeIf { it > 0 }?.toString().orEmpty(), { it.isEmpty() || it.toIntOrNull() in 1..86400 }) { CoreOverrides.setKeepAliveInterval(ctx, it.toIntOrNull() ?: 0) }
        if (android.os.Build.VERSION.SDK_INT < 29) fragment.findPreference<Preference>("find-process-mode")?.apply {
            isEnabled = false
            summary = getString(R.string.mihomo_process_requirement)
        }
        choice("global-client-fingerprint", CoreOverrides.ClientFingerprint.entries.map { it.name }, CoreOverrides.ClientFingerprint.entries.map { it.value ?: getString(R.string.mihomo_follow_profile) }, CoreOverrides.clientFingerprint(ctx).name) { CoreOverrides.setClientFingerprint(ctx, CoreOverrides.ClientFingerprint.valueOf(it)) }
        choice("tls-verification", listOf("-1", "1", "0"), listOf(getString(R.string.mihomo_follow_profile), getString(R.string.mihomo_verify_tls), getString(R.string.mihomo_skip_tls)), CoreOverrides.tlsVerification(ctx).toString()) { CoreOverrides.setTlsVerification(ctx, it.toInt()) }
    }

    private fun applyChange(key: String) {
        if (key != com.miku.ray.AppConfig.PREF_KEEP_AWAKE) com.mikubox.mihomo.service.VpnController.restart(ctx)
    }

    private fun toggle(key: String, value: Boolean, save: (Boolean) -> Unit) {
        requireNotNull(fragment.findPreference<SwitchPreferenceCompat>(key)).apply {
            isPersistent = false
            isChecked = value
            setOnPreferenceChangeListener { _, newValue -> save(newValue as Boolean); applyChange(key); true }
        }
    }

    private fun choice(key: String, values: List<String>, labels: List<String>, current: String, save: (String) -> Unit) {
        requireNotNull(fragment.findPreference<ListPreference>(key)).apply {
            isPersistent = false
            entries = labels.toTypedArray()
            entryValues = values.toTypedArray()
            value = current
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue -> save(newValue as String); applyChange(key); true }
        }
    }

    private fun text(key: String, current: String, valid: (String) -> Boolean, save: (String) -> Unit) {
        requireNotNull(fragment.findPreference<EditTextPreference>(key)).apply {
            isPersistent = false
            text = current
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                it.text?.takeIf(String::isNotBlank) ?: getString(R.string.mihomo_follow_profile)
            }
            dialogMessage = getString(R.string.mihomo_settings_hint) + when (key) {
                "hosts" -> "\n{\"example.com\": \"1.2.3.4\"}"
                "dns.nameserver-policy" -> "\n{\"+.example.com\": \"https://dns.example/dns-query\"}"
                else -> ""
            }
            setOnBindEditTextListener { edit ->
                edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                if (key == "authentication" || key == "external-controller.secret") {
                    edit.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as String).trim()
                if (valid(value)) { save(value); applyChange(key); true } else {
                    Toast.makeText(context, R.string.mihomo_invalid_value, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

}
