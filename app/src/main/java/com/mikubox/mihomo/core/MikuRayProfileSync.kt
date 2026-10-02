package com.mikubox.mihomo.core

import android.content.Context
import com.miku.ray.AppConfig
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.profile.MihomoTrafficStore

/**
 * Keeps the vendored profile list and MikuBox's own store in step.
 *
 * MikuRay's home screen lists entries from its own store (`MmkvManager`), while
 * the profiles this app actually connects with live in [MihomoProfileStore].
 * Rather than teach either side about the other, the profiles are mirrored into
 * MikuRay's table under their own ids: the id is the storage key there, so a
 * selection made on MikuRay's screen is already a MikuBox profile id — which is
 * what lets the connect button start the tunnel the user picked.
 *
 * The mirrored entries are MikuRay's "custom config" type: MikuBox profiles are
 * whole mihomo configurations, not per-protocol server records, and the core
 * that consumes them is MikuBox's, not the one MikuRay would have started.
 */
object MikuRayProfileSync {

    @Synchronized
    fun sync(context: Context) {
        val profiles = MihomoProfileStore.profiles(context)
        com.mikubox.mihomo.profile.ProfileGroups.list(context).forEach { group ->
            val previous = MmkvManager.decodeSubscription(group.id)
            MmkvManager.encodeSubscription(group.id, (previous ?: com.miku.ray.dto.entities.SubscriptionItem()).copy(remarks = group.name))
        }
        val groups = com.mikubox.mihomo.profile.ProfileGroups.list(context).map { it.id }.toSet()
        val known = profiles.map { it.id }.toSet()

        profiles.forEach { profile ->
            val existing = MmkvManager.decodeServerConfig(profile.id)
            // A configuration holding one proxy is a server, and the row describes
            // it as one: the protocol badge, the address and the transport chips all
            // come from per-server fields. Anything else stays a custom config.
            val node = MikuRayProfileDescription.singleNode(profile.config)
            val entry = (existing ?: ProfileItem(configType = EConfigType.CUSTOM)).copy(
                remarks = profile.name,
                subscriptionId = profile.groupId,
                configType = node?.configType ?: EConfigType.CUSTOM,
                server = node?.server,
                serverPort = node?.port,
                network = node?.network,
                security = node?.security,
                insecure = node?.insecure,
            )
            if (existing == null) entry.addedTime = System.currentTimeMillis()
            // encodeServerConfig also files the id into the list it belongs to; it
            // only rewrites when the entry actually changed.
            // ProfileItem.equals intentionally ignores display metadata for node
            // deduplication, so it cannot detect a profile rename by itself.
            if (existing != entry || existing.remarks != entry.remarks ||
                existing.configType != entry.configType ||
                existing.subscriptionId != entry.subscriptionId ||
                existing.insecure != entry.insecure
            ) {
                if (existing != null && existing.subscriptionId != entry.subscriptionId) {
                    val oldGroup = existing.subscriptionId ?: AppConfig.DEFAULT_SUBSCRIPTION_ID
                    MmkvManager.encodeServerList(MmkvManager.decodeServerList(oldGroup).filterNot { it == profile.id }.toMutableList(), oldGroup)
                }
                MmkvManager.encodeServerConfig(profile.id, entry)
            }
            MmkvManager.encodeServerRaw(profile.id, profile.config)
        }

        // Profiles have already moved; removing the empty mirror cannot delete them.
        MmkvManager.decodeSubscriptions().filter { it.guid !in groups }.forEach { stale ->
            if (MmkvManager.decodeServerList(stale.guid).isEmpty()) MmkvManager.removeSubscription(stale.guid)
        }

        // Entries whose profile is gone.
        MmkvManager.decodeAllServerList().orEmpty()
            .filter { it.isNotBlank() && it !in known }
            .forEach { stale -> MmkvManager.removeServer(stale) }

        MihomoProfileStore.selected(context)?.let { MmkvManager.setSelectServer(it.id) }
        refreshRowDetails(context)
        SettingsChangeManager.notifyUiCustomizationChanged()
    }

    /**
     * Fills the delay on the row of the profile the tunnel is running.
     *
     * The rows read it from the affiliation store, so what the core reports is
     * copied into that store. The delay belongs to the node the core is carrying
     * traffic through, which is what "this profile's latency" means when the
     * profile is a whole configuration; with the core stopped there is nothing to
     * measure, and the row shows no number rather than a stale one. Only that one
     * row is written — the reading is the running node's, so putting it on every
     * row would claim a measurement the others have not had.
     *
     * The up/down totals in that same record are not written here: the vendored
     * traffic loop adds what the core moves into them while the tunnel runs, and
     * an absolute value written from a second accounting would fight it.
     */
    fun refreshRowDetails(context: Context) {
        val selected = MihomoProfileStore.selected(context)?.id ?: return
        if (!com.miku.ray.MikuCoreBridge.isRunning()) return
        // Follow the endpoint the home card uses (pref_delay_test_url) when the
        // user set one. The fallback stays the app's cp.cloudflare.com default —
        // deliberately NOT SettingsManager.getDelayTestUrl()'s gstatic fallback —
        // so users who configured neither keep today's measurement conditions.
        val testUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL)
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: MihomoCoreSettings.DEFAULT_TEST_URL
        // The measurement is a request through the core, so it stays off the
        // caller's thread — this is reached from the service's checkpoint handler.
        Thread {
            val delay = runCatching {
                com.miku.ray.MikuCoreBridge.currentNodeDelay(testUrl)
            }.getOrDefault(-1L)
            if (delay > 0L) {
                MmkvManager.encodeServerTestDelayMillis(selected, delay)
                // The row was bound before the number existed, so the list has to
                // be told to read it again.
                SettingsChangeManager.makeRefreshDisplayPrefs()
            }
        }.start()
    }

    /**
     * Re-reads the up/down totals on the running profile's row.
     *
     * The totals come from this app's own accounting ([MihomoTrafficStore]), which
     * counts what the core moved in full; MikuRay's own loop derived them from the
     * connections it could sample, which misses the short ones a browsing session
     * is made of. The rows are told to refresh through the message they already
     * listen for.
     */
    fun refreshRowTraffic(context: Context) {
        val selected = MihomoProfileStore.selected(context)?.id ?: return
        val totals = MihomoTrafficStore.liveTotals(context, selected)
        if (totals.upload == 0L && totals.download == 0L) return
        MmkvManager.encodeServerTraffic(selected, totals.upload, totals.download)
        runCatching {
            com.miku.ray.util.MessageUtil.sendMsg2UI(
                context,
                com.miku.ray.AppConfig.MSG_TRAFFIC_UPDATED,
                selected,
            )
        }
    }

    /** The MikuBox profile MikuRay's list currently has selected, if any. */
    fun selectedProfileId(): String? = MmkvManager.getSelectServer()?.takeIf { it.isNotBlank() }
}
