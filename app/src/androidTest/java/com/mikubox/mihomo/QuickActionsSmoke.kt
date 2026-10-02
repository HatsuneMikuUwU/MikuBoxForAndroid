package com.mikubox.mihomo

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.main.MainActivity
import com.miku.ray.ui.main.MainViewModel
import com.mikubox.mihomo.core.*
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.service.VpnController

/** Opt-in test on a disposable emulator; requires the local CONNECT fixture on port 18091. */
object QuickActionsSmoke {
    fun run(test: Instrumentation): Bundle {
        val ctx = test.targetContext
        val report = Bundle()
        fun waitFor(label: String, seconds: Int = 35, predicate: () -> Boolean) {
            val deadline = System.currentTimeMillis() + seconds * 1000
            while (!predicate()) {
                check(System.currentTimeMillis() < deadline) { "Timed out: $label" }
                Thread.sleep(100)
            }
        }
        fun ipUrl(country: String): String = "http://10.0.2.2:18081/ip/${country.lowercase()}"
        MmkvManager.encodeSettings("pref_mikubox_welcome_completed", true)
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_SPLASH, false)
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_QUICK_ACTIONS, true)
        MmkvManager.encodeSettings(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, false)
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "2")
        MmkvManager.encodeSettings(AppConfig.PREF_IP_API_URL, ipUrl("HK"))
        MmkvManager.encodeSettings(AppConfig.PREF_DELAY_TEST_URL, "http://10.0.2.2:18081/delay")
        // A previously failed run may have left global mode behind; FOLLOW makes
        // the profile's own `mode: rule` win again so the switch step is fresh.
        MihomoCoreSettings.setMode(ctx, MihomoCoreSettings.ProxyMode.FOLLOW)
        CoreOverrides.setExtra(ctx, "script.enabled", "false")
        val config = """
            mode: rule
            proxies:
              - {name: QA-HK, type: http, server: 10.0.2.2, port: 18091}
              - {name: QA-JP, type: http, server: 10.0.2.2, port: 18091}
            proxy-groups:
              - {name: Test, type: select, proxies: [QA-HK, QA-JP]}
            rules:
              - MATCH,Test
        """.trimIndent()
        // Publish the config on the fixture so the subscription download and
        // every later step stay on the local network, no public service needed.
        val upload = java.net.URL("http://10.0.2.2:18081/sub/qa")
            .openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
        upload.requestMethod = "POST"; upload.doOutput = true
        upload.setFixedLengthStreamingMode(config.toByteArray().size)
        upload.outputStream.use { it.write(config.toByteArray()) }
        check(upload.responseCode == 200) { "Fixture config registration failed: ${upload.responseCode}" }
        upload.disconnect()
        val url = "http://10.0.2.2:18081/sub/qa"
        // Previous runs leave their profiles behind; the quick buttons test
        // every profile, so stale ones delay this run's result past the waits.
        MihomoProfileStore.profiles(ctx).forEach { MihomoProfileStore.remove(ctx, it.id) }
        val profile = MihomoProfileStore.createSubscription(ctx, "Quick action QA", url, 0)
        // Let the quick-update button do the download; an empty config must become usable.
        MikuRayProfiles.sync()
        val activity = test.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        test.waitForIdleSync()
        fun click(id: Int) { test.runOnMainSync { check(activity.findViewById<View>(id).performClick()) } }
        test.runOnMainSync { androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java].ensureServerCacheReady() }
        click(com.miku.ray.R.id.btn_quick_sub_update)
        waitFor("subscription download") { MihomoProfileStore.profiles(ctx).first { it.id == profile.id }.updatedAtMillis > 0 }
        report.putString("subscription", "quick button downloads config with schedule disabled")
        test.runOnMainSync { androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java].reloadServerList() }
        Thread.sleep(800)
        click(com.miku.ray.R.id.btn_quick_tcping)
        waitFor("TCP result") { (MmkvManager.decodeServerAffiliationInfo(profile.id)?.testDelayMillis ?: 0) > 0 }
        report.putString("tcp", MmkvManager.decodeServerAffiliationInfo(profile.id)?.testDelayMillis.toString())
        Thread.sleep(1200)
        MmkvManager.encodeServerTestDelayMillis(profile.id, 0)
        click(com.miku.ray.R.id.btn_quick_real_ping)
        waitFor("URL-test result") { (MmkvManager.decodeServerAffiliationInfo(profile.id)?.testDelayMillis ?: 0) > 0 }
        report.putString("url_test", MmkvManager.decodeServerAffiliationInfo(profile.id)?.testDelayMillis.toString())
        Thread.sleep(1200)
        click(com.miku.ray.R.id.btn_quick_country_code)
        waitFor("country result") { MmkvManager.decodeServerAffiliationInfo(profile.id)?.countryCode == "HK" }
        report.putString("country", "HK from local fixture through profile proxy")
        Thread.sleep(1000)
        MihomoProfileStore.select(ctx, profile.id)
        VpnController.connect(ctx)
        waitFor("VPN connected") { VpnController.isRunning }
        fun ipText(): String { var result = ""; test.runOnMainSync { result = activity.findViewById<TextView>(com.miku.ray.R.id.tv_ip_state).text.toString() }; return result }
        waitFor("initial IP") { ipText().contains("103.30.78.42") }
        MmkvManager.encodeSettings(AppConfig.PREF_IP_API_URL, ipUrl("JP"))
        test.runOnMainSync {
            val root = activity.findViewById<android.view.ViewGroup>(com.miku.ray.R.id.routing_mode)
            fun find(v: View): TextView? {
                if (v is TextView && v.text == ctx.getString(com.miku.ray.R.string.mihomo_mode_global)) return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
                return null
            }
            check(find(root)?.performClick() == true)
        }
        waitFor("mode switch IP") { ipText().contains("45.143.235.225") }
        report.putString("mode_ip", "HK to JP refreshed through mode button")
        Thread.sleep(1000)
        MmkvManager.encodeSettings(AppConfig.PREF_IP_API_URL, ipUrl("HK"))
        test.runOnMainSync {
            val root = activity.findViewById<android.view.ViewGroup>(com.miku.ray.R.id.routing_mode)
            val column = root.getChildAt(0) as android.view.ViewGroup
            check(column.getChildAt(1).performClick())
        }
        test.waitForIdleSync()
        waitFor("exit picker") { test.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("QA-JP")?.isNotEmpty() == true }
        // Alert-list rows are not accessibility-clickable (the list controller
        // owns the click), so tap the row's coordinates like a finger would.
        var node = test.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("QA-JP").first()
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        fun tapEvent(action: Int, downTime: Long) = android.view.MotionEvent.obtain(
            downTime, System.currentTimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val downTime = System.currentTimeMillis()
        check(test.uiAutomation.injectInputEvent(tapEvent(android.view.MotionEvent.ACTION_DOWN, downTime), true))
        Thread.sleep(80)
        check(test.uiAutomation.injectInputEvent(tapEvent(android.view.MotionEvent.ACTION_UP, downTime), true))
        waitFor("node switch IP") { ipText().contains("103.30.78.42") }
        report.putString("node_ip", "JP to HK refreshed through global exit picker")
        check(VpnController.isRunning)
        MmkvManager.encodeServerTestDelayMillis(profile.id, 0)
        click(com.miku.ray.R.id.btn_quick_real_ping)
        waitFor("connected batch probe") { (MmkvManager.decodeServerAffiliationInfo(profile.id)?.testDelayMillis ?: 0) > 0 }
        check(VpnController.isRunning) { "Probe stopped the active VPN" }
        report.putString("isolation", "batch probe succeeded while VPN remained connected")
        return report
    }
}
