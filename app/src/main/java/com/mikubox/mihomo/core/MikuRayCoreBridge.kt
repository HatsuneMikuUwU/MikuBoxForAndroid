package com.mikubox.mihomo.core

import com.miku.ray.MikuCoreBridge
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.profile.MihomoSubscriptionUpdater
import com.mikubox.mihomo.service.VpnController

/**
 * MikuBox's side of the bridge the vendored MikuRay layer talks to.
 *
 * The vendored screens call `CoreServiceManager`/`CoreNativeManager`, which now
 * route here through [MikuCoreBridge]; this is where those calls meet the mihomo
 * core and the app's own VPN service. It is installed once from `MikuApp`.
 */
object MikuRayCoreBridge : MikuCoreBridge.Impl {
    override fun connectionElapsedMillis(): Long = com.mikubox.mihomo.service.ConnectionStatus.elapsedMillis()

    override fun startOnBoot(): Boolean {
        val context = MikuRayBridgeContext.application ?: return false
        if (OnDemandSettings.enabled(context)) {
            com.mikubox.mihomo.service.OnDemandService.refresh(context)
            return true
        }
        // Boot receivers cannot display the system consent activity. Wait for the
        // user to connect from the UI when consent was revoked or never granted.
        if (android.net.VpnService.prepare(context) != null) return false
        MikuRayProfileSync.selectedProfileId()?.let { MihomoProfileStore.select(context, it) }
        if (MihomoProfileStore.selected(context) == null) return false
        // BOOT_COMPLETED is a background foreground-service-start context: the
        // platform can refuse the start outright, and an exception here would
        // crash the process inside the boot broadcast (every other caller of
        // MikuVpnService.start wraps this). Fall back to arming the tunnel
        // guard, which retries in the next window the platform allows.
        runCatching { com.mikubox.mihomo.service.MikuVpnService.start(context) }
            .onFailure {
                com.miku.ray.util.LogUtil.w(
                    message = "startOnBoot could not start the VPN service; falling back to the tunnel guard",
                    throwable = it,
                )
                com.mikubox.mihomo.service.TunnelGuard.schedule(context)
            }
        return true
    }

    override fun isRunning(): Boolean = VpnController.isRunning

    override fun isConnecting(): Boolean = com.mikubox.mihomo.service.MikuVpnService.starting

    /**
     * Starts the tunnel. The Xray config MikuRay would pass is not used: MikuBox
     * starts the core from the profile the user selected, and the Android VPN
     * consent/foreground-service flow belongs to MikuBox's own service — running
     * it from here would be a second tunnel.
     */
    override fun start(config: String): Boolean {
        val context = MikuRayBridgeContext.application ?: return false
        // A new tunnel means new counters in the core, so the deltas reported to
        // the traffic loop start over with them.
        MihomoTrafficDelta.reset()
        // The selection made on MikuRay's list is a MikuBox profile id (see
        // MikuRayProfileSync), so the profile the user tapped is the one that
        // gets connected.
        MikuRayProfileSync.selectedProfileId()?.let { guid ->
            MihomoProfileStore.select(context, guid)
        }
        VpnController.connect(context)
        return true // Request accepted; the service broadcasts the eventual result.
    }

    override fun stop(): Boolean {
        val context = MikuRayBridgeContext.application ?: return false
        MihomoTrafficDelta.reset()
        VpnController.disconnect(context)
        return true
    }

    override fun restart(): Boolean {
        val context = MikuRayBridgeContext.application ?: return false
        // Fully disconnected: nothing to reload, and the next start re-reads the
        // settings anyway. CONNECTING is accepted — VpnController queues the
        // reload and the service replays it once the tunnel is up, so the
        // routing screen's "apply now" behaves like the settings pages'.
        if (!VpnController.isRunning && !com.mikubox.mihomo.service.MikuVpnService.starting) return false
        // Reloading re-reads the selected profile, so the row the user picked on
        // MikuRay's list has to reach MikuBox's store first — without this the
        // tunnel restarts from the previous configuration and the switch looks
        // like it never happened.
        MikuRayProfileSync.selectedProfileId()?.let { guid ->
            MihomoProfileStore.select(context, guid)
        }
        // Restart produces new counters in the core just like start/stop, so
        // the delta tracker must start over or the next tick reads stale values.
        MihomoTrafficDelta.reset()
        VpnController.restart(context)
        return true
    }

    override fun version(): String = runCatching { MihomoCore.version() }.getOrDefault("")

    /**
     * MikuRay measures a delay from a *server config*; the mihomo core measures
     * one from a proxy name, and a profile here is a whole configuration rather
     * than one server. So an arbitrary config cannot be measured without
     * connecting it, and this reports an unavailable measurement (-1). The UI disables
     * batch probes of whole configurations.
     */
    override fun measureDelay(config: String, testUrl: String): Long = -1L

    /**
     * The delay of the node the tunnel is carrying traffic through — the reading
     * that means something while the core is running, and what the rows and the
     * connect bar show.
     */
    override fun currentNodeDelay(testUrl: String): Long {
        if (!VpnController.isRunning) return -1L
        val port = com.miku.ray.handler.SettingsManager.getHttpPort()
        if (port <= 0) return -1L
        return runCatching {
            // The mixed inbound applies the real routing rules. Picking the first
            // group (usually GLOBAL/DIRECT) can report success while PROXY is broken.
            val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress("127.0.0.1", port))
            val connection = java.net.URL(testUrl).openConnection(proxy) as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.useCaches = false
                val start = android.os.SystemClock.elapsedRealtime()
                if (connection.responseCode in 200..399)
                    (android.os.SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
                else -1L
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(-1L)
    }

    /**
     * `tag,direction,bytes` lines. MikuRay's traffic loop adds what it reads to
     * the totals behind its rows, so these are deltas — see [MihomoTrafficDelta].
     */
    override fun outboundTrafficStats(): String = MihomoTrafficDelta.take()
}

/** The application instance the bridge needs to reach the service. */
object MikuRayBridgeContext {
    @Volatile
    var application: android.content.Context? = null
        private set

    fun attach(context: android.content.Context) {
        application = context.applicationContext
    }
}

/**
 * The subscription table the vendored subscription screens read and write,
 * backed by MikuBox's own profile store.
 *
 * This is what makes those screens honest: the row a user adds there is the
 * profile the tunnel uses, with the same URL, schedule and proxy flag — not a
 * copy in a second store that nothing connects with.
 */
object MikuRaySubscriptions : com.miku.ray.MikuSubscriptions.Impl {

    private val store: MihomoProfileStore get() = MihomoProfileStore

    override fun list(): List<com.miku.ray.MikuSubscriptions.Subscription> {
        val context = MikuRayBridgeContext.application ?: return emptyList()
        return store.profiles(context)
            .filter { it.isSubscription }
            .map { profile ->
                com.miku.ray.MikuSubscriptions.Subscription(
                    id = profile.id,
                    name = profile.name,
                    url = profile.subscriptionUrl.orEmpty(),
                    autoUpdate = profile.updateIntervalMinutes > 0,
                    intervalMinutes = profile.updateIntervalMinutes,
                    throughProxy = profile.updateThroughProxy,
                    updateWhenConnectedOnly = profile.updateWhenConnectedOnly,
                    lastUpdatedMillis = profile.updatedAtMillis,
                )
            }
    }

    override fun upsert(
        id: String?,
        name: String,
        url: String,
        autoUpdate: Boolean,
        intervalMinutes: Long,
        throughProxy: Boolean,
        updateWhenConnectedOnly: Boolean,
    ): String? {
        val context = MikuRayBridgeContext.application ?: return null
        // "Auto update off" is stored as no interval: the scheduler keys off it.
        val minutes = if (autoUpdate) intervalMinutes.coerceAtLeast(15) else 0L
        val existing = id?.let { wanted -> store.profiles(context).firstOrNull { it.id == wanted } }
        val saved = if (existing == null) {
            runCatching {
                store.createSubscription(
                    context = context,
                    name = name,
                    url = url,
                    intervalMinutes = minutes,
                    updateWhenConnectedOnly = updateWhenConnectedOnly,
                    updateThroughProxy = throughProxy,
                ).id
            }.getOrNull()
        } else {
            store.update(
                context,
                existing.copy(
                    name = name.ifBlank { existing.name },
                    subscriptionUrl = url,
                    // A changed source still needs a first fetch, including after
                    // failure/retry; retain the old config until replacement succeeds.
                    updatedAtMillis = if (existing.subscriptionUrl == url) existing.updatedAtMillis else 0L,
                    updateIntervalMinutes = minutes,
                    updateThroughProxy = throughProxy,
                    updateWhenConnectedOnly = updateWhenConnectedOnly,
                ),
            )
            MihomoSubscriptionUpdater.reconfigure(context)
            existing.id
        }
        MikuRayProfiles.sync()
        return saved
    }

    override fun remove(id: String) {
        val context = MikuRayBridgeContext.application ?: return
        store.remove(context, id)
        MihomoSubscriptionUpdater.reconfigure(context)
        MikuRayProfiles.sync()
    }

    override fun refresh(id: String): Boolean {
        val context = MikuRayBridgeContext.application ?: return false
        val profile = store.profiles(context).firstOrNull { it.id == id } ?: return false
        return runCatching { MihomoSubscriptionUpdater.update(context, profile); MikuRayProfiles.sync() }.isSuccess
    }
}
