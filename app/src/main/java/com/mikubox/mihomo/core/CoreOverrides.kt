package com.mikubox.mihomo.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured overrides for the core's TUN section and for the core-wide knobs
 * the desktop clients group under "core" / "advanced": interface stack, timeouts,
 * process matching, geodata loading, TLS fingerprint and keep-alive.
 *
 * Options are [UNSET] by default and only appear in the JSON handed to the
 * bridge once the user changes them, so an imported profile keeps deciding
 * everything else.
 */
object CoreOverrides {
    fun proxyCredentials(context: Context): Pair<String, String>? {
        val configured = extra(context, "authentication").lineSequence().firstOrNull(String::isNotBlank)
        val entry = configured ?: runCatching {
            val root = org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(org.yaml.snakeyaml.LoaderOptions()))
                .load<Any>(com.mikubox.mihomo.profile.MihomoProfileStore.selected(context)?.config.orEmpty()) as? Map<*, *>
            (root?.get("authentication") as? List<*>)?.firstOrNull()?.toString()
        }.getOrNull() ?: return null
        if (!entry.contains(':')) return null
        return entry.substringBefore(':') to entry.substringAfter(':')
    }


    const val UNSET = -1
    const val OFF = 0
    const val ON = 1

    enum class FindProcess(val value: String?) {
        FOLLOW(null), ALWAYS("always"), STRICT("strict"), OFF_MODE("off")
    }

    /** mihomo's geodata loader: memory conservative trades speed for RAM. */
    enum class GeodataLoader(val value: String?) {
        FOLLOW(null), STANDARD("standard"), MEMORY("memconservative")
    }

    /** A single client fingerprint for every outgoing TLS handshake. */
    enum class ClientFingerprint(val value: String?) {
        FOLLOW(null),
        CHROME("chrome"),
        FIREFOX("firefox"),
        SAFARI("safari"),
        IOS("ios"),
        ANDROID("android"),
        RANDOM("random"),
        NONE("none"),
    }

    private const val PREFS = "miku_core_overrides"

    private const val KEY_FIND_PROCESS = "find_process"
    private const val KEY_GEODATA_MODE = "geodata_mode"
    private const val KEY_GEODATA_LOADER = "geodata_loader"
    private const val KEY_FINGERPRINT = "client_fingerprint"
    private const val KEY_KEEP_ALIVE = "keep_alive_interval"
    private const val KEY_DISABLE_KEEP_ALIVE = "disable_keep_alive"
    private const val KEY_MIXED_PORT = "mixed_port"
    private const val KEY_UDP_TIMEOUT = "udp_timeout"
    private const val KEY_ICMP_TIMEOUT = "icmp_timeout"
    private const val KEY_ENDPOINT_NAT = "endpoint_independent_nat"
    private const val KEY_DISABLE_ICMP = "disable_icmp_forwarding"
    private const val KEY_SNIFFING = "sniffing"
    private const val KEY_SNIFF_OVERRIDE = "sniff_override_destination"
    private const val KEY_WAKE_LOCK = "wake_lock"
    private const val KEY_CONTROLLER = "controller"
    private const val KEY_CONTROLLER_ADDRESS = "controller_address"
    private const val KEY_CONTROLLER_SECRET = "controller_secret"

    /** The address a fresh controller listens on: this device only. */
    const val DEFAULT_CONTROLLER_ADDRESS = "127.0.0.1:9090"

    fun findProcess(context: Context): FindProcess =
        enumOr(prefs(context).getString(KEY_FIND_PROCESS, null), FindProcess.FOLLOW)
    fun setFindProcess(context: Context, value: FindProcess) =
        edit(context) { putString(KEY_FIND_PROCESS, value.name) }

    fun geodataLoader(context: Context): GeodataLoader =
        enumOr(prefs(context).getString(KEY_GEODATA_LOADER, null), GeodataLoader.FOLLOW)
    fun setGeodataLoader(context: Context, value: GeodataLoader) =
        edit(context) { putString(KEY_GEODATA_LOADER, value.name) }

    /** Whether the core is told to use the .dat geodata files. */
    fun geodataMode(context: Context): Int = tri(context, KEY_GEODATA_MODE)
    fun setGeodataMode(context: Context, value: Int) = putTri(context, KEY_GEODATA_MODE, value)

    fun clientFingerprint(context: Context): ClientFingerprint =
        enumOr(prefs(context).getString(KEY_FINGERPRINT, null), ClientFingerprint.FOLLOW)
    fun setClientFingerprint(context: Context, value: ClientFingerprint) =
        edit(context) { putString(KEY_FINGERPRINT, value.name) }

    /** Seconds an idle connection is kept; 0 leaves the core's default. */
    fun keepAliveIdle(context: Context): Int = number(context, "keep_alive_idle")
    fun setKeepAliveIdle(context: Context, value: Int) = edit(context) {
        if (value <= 0) remove("keep_alive_idle") else putInt("keep_alive_idle", value)
    }

    fun keepAliveInterval(context: Context): Int = number(context, KEY_KEEP_ALIVE)
    fun setKeepAliveInterval(context: Context, value: Int) = edit(context) {
        if (value <= 0) remove(KEY_KEEP_ALIVE) else putInt(KEY_KEEP_ALIVE, value)
    }

    fun disableKeepAlive(context: Context): Int = tri(context, KEY_DISABLE_KEEP_ALIVE)
    fun setDisableKeepAlive(context: Context, value: Int) = putTri(context, KEY_DISABLE_KEEP_ALIVE, value)

    /** Mixed HTTP/SOCKS port used by the app's non-VPN proxy mode. */
    fun mixedPort(context: Context): Int = number(context, KEY_MIXED_PORT)
    fun setMixedPort(context: Context, value: Int) = edit(context) {
        if (value <= 0 || value > 65535) remove(KEY_MIXED_PORT) else putInt(KEY_MIXED_PORT, value)
    }

    /** Seconds a UDP association is kept in the tunnel. */
    fun udpTimeout(context: Context): Int = number(context, KEY_UDP_TIMEOUT)
    fun setUdpTimeout(context: Context, value: Int) = edit(context) {
        if (value <= 0) remove(KEY_UDP_TIMEOUT) else putInt(KEY_UDP_TIMEOUT, value)
    }

    fun icmpTimeout(context: Context): Int = number(context, KEY_ICMP_TIMEOUT)
    fun setIcmpTimeout(context: Context, value: Int) = edit(context) {
        if (value <= 0) remove(KEY_ICMP_TIMEOUT) else putInt(KEY_ICMP_TIMEOUT, value)
    }

    fun endpointIndependentNat(context: Context): Int = tri(context, KEY_ENDPOINT_NAT)
    fun setEndpointIndependentNat(context: Context, value: Int) = putTri(context, KEY_ENDPOINT_NAT, value)

    fun disableIcmpForwarding(context: Context): Int = tri(context, KEY_DISABLE_ICMP)
    fun setDisableIcmpForwarding(context: Context, value: Int) = putTri(context, KEY_DISABLE_ICMP, value)

    /** Domain sniffing, which recovers the hostname a tunnel connection is for. */
    fun sniffing(context: Context): Int = tri(context, KEY_SNIFFING)
    fun setSniffing(context: Context, value: Int) = putTri(context, KEY_SNIFFING, value)

    /** Let the sniffed hostname replace the destination IP the rule matched on. */
    fun sniffOverrideDestination(context: Context): Int = tri(context, KEY_SNIFF_OVERRIDE)
    fun setSniffOverrideDestination(context: Context, value: Int) = putTri(context, KEY_SNIFF_OVERRIDE, value)

    /**
     * Keeps the CPU awake while the tunnel runs. Without it the system can
     * suspend the process during long idle periods, which stalls forwarding until
     * something else wakes the device.
     */
    fun wakeLock(context: Context): Int = tri(context, KEY_WAKE_LOCK)
    fun setWakeLock(context: Context, value: Int) = putTri(context, KEY_WAKE_LOCK, value)

    // region external controller

    /** On, off, or whatever the profile decides. */
    fun controller(context: Context): Int = tri(context, KEY_CONTROLLER)

    /**
     * Enabling the controller also settles its address and secret: it listens on
     * this device only unless the user says otherwise, and it always has a secret
     * so an open port is never an unauthenticated one.
     */
    fun setController(context: Context, value: Int) = edit(context) {
        if (value == UNSET) remove(KEY_CONTROLLER) else putInt(KEY_CONTROLLER, value)
        if (value != ON) return@edit
        if (prefs(context).getString(KEY_CONTROLLER_ADDRESS, null).isNullOrBlank()) {
            putString(KEY_CONTROLLER_ADDRESS, DEFAULT_CONTROLLER_ADDRESS)
        }
        if (prefs(context).getString(KEY_CONTROLLER_SECRET, null).isNullOrBlank()) {
            putString(KEY_CONTROLLER_SECRET, newSecret())
        }
    }

    fun controllerAddress(context: Context): String =
        prefs(context).all[KEY_CONTROLLER_ADDRESS]?.toString()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CONTROLLER_ADDRESS

    fun setControllerAddress(context: Context, value: String) = edit(context) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) remove(KEY_CONTROLLER_ADDRESS) else putString(KEY_CONTROLLER_ADDRESS, trimmed)
    }

    /** Sets the secret by hand; a blank value falls back to a generated one. */
    fun setControllerSecret(context: Context, value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed == controllerSecret(context)) return
        edit(context) { putString(KEY_CONTROLLER_SECRET, trimmed) }
    }

    /**
     * The controller's shared secret. It is generated on the device and never a
     * value baked into the app, so every install has its own.
     */
    fun controllerSecret(context: Context): String =
        prefs(context).all[KEY_CONTROLLER_SECRET]?.toString().orEmpty()

    fun regenerateControllerSecret(context: Context): String {
        val secret = newSecret()
        edit(context) { putString(KEY_CONTROLLER_SECRET, secret) }
        return secret
    }

    private fun newSecret(): String {
        val bytes = ByteArray(18)
        java.security.SecureRandom().nextBytes(bytes)
        // android.util.Base64 works on every supported release; the java.util
        // one needs API 26 (or desugaring) and would crash on Android 7.
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    /** The controller block as config keys; absent while it follows the profile. */
    fun controllerJson(context: Context): JSONObject = JSONObject().apply {
        when (controller(context)) {
            ON -> {
                put("external-controller", controllerAddress(context))
                controllerSecret(context).takeIf { it.isNotBlank() }?.let { put("secret", it) }
            }
            // An empty address is how the core is told to keep no listener open.
            OFF -> put("external-controller", "")
        }
    }

    // endregion

    fun extra(context: Context, key: String): String = prefs(context).getString("extra.$key", "").orEmpty()
    fun setExtra(context: Context, key: String, value: String) = edit(context) { putString("extra.$key", value) }

    fun dnsHijack(context: Context): String = prefs(context).getString("dns_hijack", null) ?: "any:53"
    fun setDnsHijack(context: Context, value: String) = edit(context) { putString("dns_hijack", value.trim()) }
    fun tlsVerification(context: Context): Int = tri(context, "tls_verification")
    fun setTlsVerification(context: Context, value: Int) = putTri(context, "tls_verification", value)

    /** Core-wide keys, merged over the profile's own settings. */
    fun coreJson(context: Context): JSONObject = JSONObject().apply {
        extra(context, "hosts").takeIf(String::isNotBlank)?.let { put("hosts", JSONObject(it)) }
        extra(context, "global-ua").takeIf(String::isNotBlank)?.let { put("global-ua", it) }
        extra(context, "authentication").takeIf(String::isNotBlank)?.let { put("authentication", JSONArray(it.lines().filter(String::isNotBlank))) }
        putFlag(this, "miku-tls-verify", tlsVerification(context))
        findProcess(context).value?.let { put("find-process-mode", it) }
        geodataLoader(context).value?.let { put("geodata-loader", it) }
        clientFingerprint(context).value?.let { put("global-client-fingerprint", it) }
        putFlag(this, "geodata-mode", geodataMode(context))
        putFlag(this, "disable-keep-alive", disableKeepAlive(context))
        keepAliveIdle(context).takeIf { it > 0 }?.let { put("keep-alive-idle", it) }
        keepAliveInterval(context).takeIf { it > 0 }?.let { put("keep-alive-interval", it) }
        controllerJson(context).also { controller ->
            controller.keys().forEach { key -> put(key, controller.get(key)) }
        }
    }

    /** Keys merged into the profile's `tun:` section. */
    fun tunJson(context: Context): JSONObject = JSONObject().apply {
        put("dns-hijack", JSONArray(dnsHijack(context).split(Regex("[\\s,]+" )).filter(String::isNotBlank)))
        udpTimeout(context).takeIf { it > 0 }?.let { put("udp-timeout", it) }
        icmpTimeout(context).takeIf { it > 0 }?.let { put("icmp-timeout", it) }
        putFlag(this, "endpoint-independent-nat", endpointIndependentNat(context))
        putFlag(this, "disable-icmp-forwarding", disableIcmpForwarding(context))
    }

    /**
     * The `sniffer:` section. mihomo ships it with an empty protocol map, so
     * flipping `enable` alone would sniff nothing at all; enabling it here also
     * writes the standard HTTP/TLS/QUIC entries the documentation describes.
     */
    fun snifferJson(context: Context): JSONObject = JSONObject().apply {
        when (sniffing(context)) {
            ON -> {
                put("enable", true)
                put(
                    "sniff",
                    JSONObject()
                        .put(
                            "HTTP",
                            JSONObject()
                                .put("ports", JSONArray().put("80").put("8080-8880")),
                        )
                        .put("TLS", JSONObject())
                        .put("QUIC", JSONObject()),
                )
            }
            OFF -> put("enable", false)
        }
        putFlag(this, "override-destination", sniffOverrideDestination(context))
    }

    /** Writes a tri-state as a real boolean, never as a boxed value. */
    private fun putFlag(target: JSONObject, key: String, state: Int) {
        if (state == ON) {
            target.put(key, true)
        } else if (state == OFF) {
            target.put(key, false)
        }
    }

    // region storage

    private fun tri(context: Context, key: String): Int = coerceTri(prefs(context).all[key])

    /** Reads a stored number without trusting its type; 0 means "not set". */
    private fun number(context: Context, key: String): Int = when (val stored = prefs(context).all[key]) {
        is Int -> stored
        is Long -> stored.toInt()
        is String -> stored.toIntOrNull() ?: 0
        is Boolean -> if (stored) 1 else 0
        else -> 0
    }

    private fun putTri(context: Context, key: String, value: Int) = edit(context) {
        if (value == UNSET) remove(key) else putInt(key, value)
    }

    /**
     * Reads a stored tri-state without trusting its type: a value written by an
     * older build must not become a cast exception while the core is starting.
     */
    private fun coerceTri(stored: Any?): Int = when (stored) {
        is Int -> stored
        is Boolean -> if (stored) ON else OFF
        is String -> stored.toIntOrNull() ?: when (stored.lowercase()) {
            "true", "on" -> ON
            "false", "off" -> OFF
            else -> UNSET
        }
        else -> UNSET
    }

    private inline fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(block).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(fallback)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // endregion
}
