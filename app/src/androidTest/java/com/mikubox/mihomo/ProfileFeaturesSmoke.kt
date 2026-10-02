package com.mikubox.mihomo

import android.app.Instrumentation
import android.os.Bundle
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.mikubox.mihomo.core.MikuRayProfileSync
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.profile.ProfileGroups
import com.mikubox.mihomo.profile.ProfileRouting
import com.mikubox.mihomo.service.ConnectionStatus
import com.mikubox.mihomo.service.VpnController

/** Device checks exercise the MMKV mirror and the running native rule table. */
object ProfileFeaturesSmoke {
    fun run(test: Instrumentation): Bundle {
        val context = test.targetContext
        MmkvManager.encodeSettings("pref_mikubox_welcome_completed", true)
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_SPLASH, false)
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "2")
        val group = ProfileGroups.create(context, "QA ${System.currentTimeMillis()}")
        val profile = MihomoProfileStore.create(context, "可视化编辑测试", """
            mixed-port: 10808
            mode: rule
            dns: {enable: true, nameserver: [system]}
            proxies: [{name: QA-RELAY, type: http, server: 10.0.2.2, port: 18083}]
            proxy-groups: [{name: Proxy, type: select, proxies: [QA-RELAY, DIRECT]}]
            rules: ['DOMAIN,example.com,REJECT', 'DST-PORT,18082,Proxy', 'MATCH,DIRECT']
            x-test: {preserved: true}
        """.trimIndent())
        MikuRayProfileSync.sync(context)
        check(profile.id in MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID))
        MihomoProfileStore.update(context, profile.copy(groupId = group.id))
        repeat(2) { MikuRayProfileSync.sync(context) }
        check(profile.id in MmkvManager.decodeServerList(group.id))
        check(profile.id !in MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID))
        check(MmkvManager.decodeSubscription(group.id)?.remarks == group.name)
        MihomoProfileStore.select(context, profile.id)
        ProfileRouting.setPolicy(context, profile.id, "REJECT", false)
        fun waitPhase(phase: ConnectionStatus.Phase) {
            val end = System.nanoTime() + 30_000_000_000L
            while (ConnectionStatus.phase.value != phase && System.nanoTime() < end) Thread.sleep(100)
            check(ConnectionStatus.phase.value == phase) { "Expected $phase, got ${ConnectionStatus.phase.value}" }
        }
        test.runOnMainSync { VpnController.connect(context) }
        waitPhase(ConnectionStatus.Phase.CONNECTED)
        val rules = com.mikubox.mihomo.core.MihomoCore.rules()
        check(rules.none { it.target == "REJECT" })
        check(rules.any { it.target == "Proxy" })
        // A deliberate disconnect must clear recovery intent immediately.
        test.runOnMainSync { VpnController.disconnect(context) }
        waitPhase(ConnectionStatus.Phase.DISCONNECTED)
        check(!com.mikubox.mihomo.service.TunnelGuard.isExpected(context))
        ProfileRouting.setEnabled(context, profile.id, false)
        test.runOnMainSync { VpnController.connect(context) }
        waitPhase(ConnectionStatus.Phase.CONNECTED)
        check(com.mikubox.mihomo.core.MihomoCore.rules().all { it.target == "DIRECT" })
        test.runOnMainSync { VpnController.disconnect(context) }
        waitPhase(ConnectionStatus.Phase.DISCONNECTED)
        ProfileRouting.setEnabled(context, profile.id, true)
        ProfileRouting.setPolicy(context, profile.id, "REJECT", true)
        check(MihomoProfileStore.selected(context)?.config == profile.config)
        com.mikubox.mihomo.core.MikuRayProfiles.deleteGroup(group.id)
        repeat(2) { MikuRayProfileSync.sync(context) }
        check(MmkvManager.decodeSubscription(group.id) == null)
        check(profile.id in MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID))
        check(MihomoProfileStore.selected(context)?.id == profile.id)
        check(MihomoProfileStore.selected(context)?.config == profile.config)
        val subscription = MihomoProfileStore.createSubscription(context, "Bootstrap QA", "http://10.0.2.2:18081/sub/qa", 0, updateThroughProxy = true)
        com.mikubox.mihomo.profile.MihomoSubscriptionUpdater.update(context, subscription)
        check(MihomoProfileStore.profiles(context).first { it.id == subscription.id }.let { it.config.isNotBlank() && it.updateThroughProxy })
        MikuRayProfileSync.sync(context)
        return Bundle().apply {
            putString("profileId", profile.id)
            putString("groupId", group.id)
            putString("stream", "PASS: MMKV group migration/idempotency, live policy and file rule switches, original YAML preservation, manual disconnect clears recovery, group deletion preserves profiles, initial proxy subscription bootstrap")
        }
    }
}
