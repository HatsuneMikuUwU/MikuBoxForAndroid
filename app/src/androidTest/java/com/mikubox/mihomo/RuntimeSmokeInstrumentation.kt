package com.mikubox.mihomo

import android.app.Instrumentation
import android.os.Bundle
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.mikubox.mihomo.core.MihomoCoreSettings
import com.mikubox.mihomo.profile.MihomoProfileStore
import com.mikubox.mihomo.service.VpnController

/** Opt-in device regression: requires VPN app-op and tools/runtime-network-fixture.py. */
class RuntimeSmokeInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        waitForIdleSync()
        val output = Bundle()
        if (arguments.getString("lifecycle") == "true") {
            try { finish(android.app.Activity.RESULT_OK, LifecycleSmoke.run(this)) }
            catch (error: Throwable) { output.putString("stream", android.util.Log.getStackTraceString(error)); finish(android.app.Activity.RESULT_CANCELED, output) }
            return
        }
        if (arguments.getString("profile_features") == "true") {
            try { finish(android.app.Activity.RESULT_OK, ProfileFeaturesSmoke.run(this)) }
            catch (error: Throwable) { output.putString("stream", android.util.Log.getStackTraceString(error)); finish(android.app.Activity.RESULT_CANCELED, output) }
            return
        }
        if (arguments.getString("quick_actions") == "true") {
            try { finish(android.app.Activity.RESULT_OK, QuickActionsSmoke.run(this)) }
            catch (error: Throwable) { output.putString("error", android.util.Log.getStackTraceString(error)); finish(android.app.Activity.RESULT_CANCELED, output) }
            return
        }
        try {
            val context = targetContext
            MmkvManager.encodeSettings("pref_mikubox_welcome_completed", true)
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "2")
            MmkvManager.encodeSettings(AppConfig.PREF_SHOW_SPLASH, true)
            MmkvManager.encodeSettings(AppConfig.PREF_BLUR_BOTTOM_STATUS, true)
            // A previous smoke run may have selected global mode; rules only
            // apply when the mode follows the profile, so always reset it.
            MihomoCoreSettings.setMode(context, MihomoCoreSettings.ProxyMode.FOLLOW)
            val profile = MihomoProfileStore.create(context, "Runtime regression", """
                mixed-port: 10808
                mode: rule
                log-level: debug
                dns:
                  enable: true
                  enhanced-mode: fake-ip
                  nameserver: [system]
                  direct-nameserver: [system]
                  proxy-server-nameserver: [system]
                proxies:
                  - {name: QA-RELAY, type: http, server: 10.0.2.2, port: 18083}
                rules:
                  - DST-PORT,18082,QA-RELAY
                  - MATCH,DIRECT
            """.trimIndent().let { if (arguments.getString("script") == "true" || arguments.getString("library") == "true") it.replace("MATCH,DIRECT", "MATCH,REJECT") else it })
            MihomoProfileStore.select(context, profile.id)
            MihomoCoreSettings.setTunStack(context, MihomoCoreSettings.TunStack.valueOf(arguments.getString("stack", "MIXED")))
            MihomoCoreSettings.setLogLevel(context, MihomoCoreSettings.LogLevel.DEBUG)
            val libraryTest = arguments.getString("library") == "true"
            val scripted = arguments.getString("script") == "true" || libraryTest
            com.mikubox.mihomo.core.CoreOverrides.setExtra(context, "script.enabled", scripted.toString())
            if (scripted) {
                val process = arguments.getString("process") == "true"
                if (process) com.mikubox.mihomo.core.CoreOverrides.setFindProcess(context, com.mikubox.mihomo.core.CoreOverrides.FindProcess.ALWAYS)
                val firstRule = if (process) "AND,((PROCESS-NAME,com.mikubox.mihomo.test),(DST-PORT,18081)),DIRECT" else "DST-PORT,18081,DIRECT"
                val source = "function main(c) { c.rules.unshift('$firstRule'); return c; }"
                com.mikubox.mihomo.core.CoreOverrides.setExtra(context, "script.source", source)
                val preview = com.mikubox.mihomo.core.MihomoCore.evaluateScript(profile.config, source)
                check(preview.getJSONArray("rules").getString(0) == firstRule)
                check(runCatching { com.mikubox.mihomo.core.MihomoCore.evaluateScript(profile.config, "function main(){while(true){}}") }.isFailure)
                output.putString("script", "JNI preview, timeout and actual routing transform")
                if (libraryTest) {
                    val library = com.mikubox.mihomo.core.ScriptLibrary
                    val item = library.save(context, null, "Bound route ${System.nanoTime()}", source)
                    library.bind(context, profile.id, item.id)
                    library.save(context, item.id, item.name + " renamed", source)
                    com.mikubox.mihomo.core.CoreOverrides.setExtra(context, "script.source", "function main(){throw Error('unexpected global fallback')}")
                    val backup = com.mikubox.mihomo.core.BackupManager.export(context)
                    library.delete(context, item.id)
                    com.mikubox.mihomo.core.BackupManager.import(context, backup)
                    check(library.source(context, profile.id) == source)
                    output.putString("library", "create, bind, rename, delete and backup restore; dedicated script overrides failing global script")
                }
            }
            val automatic = arguments.getString("on-demand") == "true"
            // A previous smoke run (or failed run) may still own the tunnel;
            // probing during its teardown would leak traffic past the new TUN.
            if (VpnController.isRunning) {
                runOnMainSync { VpnController.disconnect(context) }
                val cleanDeadline = System.nanoTime() + 15_000_000_000L
                while (com.mikubox.mihomo.service.ConnectionStatus.phase.value != com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED && System.nanoTime() < cleanDeadline) Thread.sleep(100)
                check(com.mikubox.mihomo.service.ConnectionStatus.phase.value == com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED) { "Leftover VPN never stopped" }
            }
            if (automatic) {
                com.mikubox.mihomo.core.OnDemandSettings.prefs(context).edit().clear().putBoolean("enabled", true).commit()
                runOnMainSync { com.mikubox.mihomo.service.OnDemandService.refresh(context) }
            } else runOnMainSync { VpnController.connect(context) }
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!VpnController.isRunning && System.nanoTime() < deadline) Thread.sleep(100)
            check(VpnController.isRunning) { "VPN did not connect" }
            fun probeNetwork() {
                val completed = java.util.concurrent.CountDownLatch(1)
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                        intent.extras?.let(output::putAll)
                        completed.countDown()
                    }
                }
                androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                    android.content.IntentFilter("com.mikubox.mihomo.QA_NETWORK_RESULT"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
                try {
                    runOnMainSync {
                        context.startActivity(android.content.Intent().setClassName("com.mikubox.mihomo.test", "com.mikubox.mihomo.NetworkProbeActivity")
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("remote", arguments.getString("remote", "false") == "true"))
                    }
                    check(completed.await(75, java.util.concurrent.TimeUnit.SECONDS)) { "External UID probe timed out" }
                    check(!output.containsKey("failure")) { "probe failed: ${output.getString("failure")} | legs: ${output.keySet().filter { it.startsWith("port_") || it.startsWith("direct_") }.joinToString { "$it=${output.getString(it)}" }}" }
                } finally { context.unregisterReceiver(receiver) }
            }
            probeNetwork()
            if (libraryTest) {
                fun waitPhase(phase: com.mikubox.mihomo.service.ConnectionStatus.Phase) {
                    val end = System.nanoTime() + 30_000_000_000L
                    while (com.mikubox.mihomo.service.ConnectionStatus.phase.value != phase && System.nanoTime() < end) Thread.sleep(100)
                    check(com.mikubox.mihomo.service.ConnectionStatus.phase.value == phase) { "Profile switch did not reach $phase" }
                }
                runOnMainSync { VpnController.disconnect(context) }
                waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED)
                val other = MihomoProfileStore.create(context, "No script", profile.config)
                com.mikubox.mihomo.core.ScriptLibrary.bind(context, other.id, com.mikubox.mihomo.core.ScriptLibrary.NONE)
                MihomoProfileStore.select(context, other.id)
                runOnMainSync { VpnController.connect(context) }
                waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.CONNECTED)
                check(com.mikubox.mihomo.core.MihomoCore.rules().none { it.type.equals("AND", true) })
                runOnMainSync { VpnController.disconnect(context) }
                waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED)
                MihomoProfileStore.select(context, profile.id)
                runOnMainSync { VpnController.connect(context) }
                waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.CONNECTED)
                check(com.mikubox.mihomo.core.MihomoCore.rules().any { it.type.equals("AND", true) || it.payload == "18081" })
                // Repeated starts often reuse the same Android descriptor. Prove
                // that each session owns a live tunnel, not just a CONNECTED flag.
                repeat(3) {
                    runOnMainSync { VpnController.disconnect(context) }
                    waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED)
                    runOnMainSync { VpnController.connect(context) }
                    waitPhase(com.mikubox.mihomo.service.ConnectionStatus.Phase.CONNECTED)
                    probeNetwork()
                }
                output.putString("reconnect", "three stop/start cycles with independent UID DIRECT and relay traffic PASS")
                output.putString("profile_switch", "bound script -> disabled script -> bound script PASS")
            }
            if (automatic) {
                fun waitState(connected: Boolean) {
                    val end = System.nanoTime() + 30_000_000_000L
                    val phase = if (connected) com.mikubox.mihomo.service.ConnectionStatus.Phase.CONNECTED else com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED
                    while (com.mikubox.mihomo.service.ConnectionStatus.phase.value != phase && System.nanoTime() < end) Thread.sleep(100)
                    check(com.mikubox.mihomo.service.ConnectionStatus.phase.value == phase) { "On-demand state did not become $phase" }
                }
                val prefs = com.mikubox.mihomo.core.OnDemandSettings.prefs(context)
                prefs.edit().apply { com.mikubox.mihomo.core.OnDemandSettings.Transport.entries.forEach { putBoolean(it.name, false) } }.commit()
                waitState(false)
                prefs.edit().apply { com.mikubox.mihomo.core.OnDemandSettings.Transport.entries.forEach { putBoolean(it.name, true) } }.commit()
                waitState(true)
                runOnMainSync { VpnController.disconnect(context) }
                waitState(false)
                Thread.sleep(3000)
                check(!VpnController.isRunning) { "Manual stop was reversed by monitor" }
                com.mikubox.mihomo.core.OnDemandSettings.setEnabled(context, false)
                runOnMainSync { com.mikubox.mihomo.service.OnDemandService.refresh(context) }
                output.putString("on_demand", "automatic connect, pause, resume and manual-stop persistence PASS")
            }
            runOnMainSync { VpnController.disconnect(context) }
            val stopDeadline = System.nanoTime() + 10_000_000_000L
            while (com.mikubox.mihomo.service.ConnectionStatus.phase.value != com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED && System.nanoTime() < stopDeadline) Thread.sleep(100)
            check(com.mikubox.mihomo.service.ConnectionStatus.phase.value == com.mikubox.mihomo.service.ConnectionStatus.Phase.DISCONNECTED) { "VPN did not stop" }
            if (arguments.getString("prepare-boot") == "true") {
                MmkvManager.setSelectServer(profile.id)
                MmkvManager.encodeStartOnBoot(true)
                MmkvManager.encodeSettings(AppConfig.PREF_FAB_EXTENDED, true)
                com.mikubox.mihomo.core.OnDemandSettings.prefs(context).edit().putBoolean("enabled", true).commit()
                runOnMainSync { com.mikubox.mihomo.service.OnDemandService.refresh(context) }
                output.putString("boot", "on-demand and boot connection enabled for actual emulator reboot")
            }
            output.putString("stream", output.keySet().joinToString("\n") { "$it=${output.getString(it)}" })
            finish(android.app.Activity.RESULT_OK, output)
        } catch (failure: Throwable) {
            com.mikubox.mihomo.core.OnDemandSettings.setEnabled(targetContext, false)
            runOnMainSync { com.mikubox.mihomo.service.OnDemandService.refresh(targetContext) }
            runOnMainSync { VpnController.disconnect(targetContext) }
            output.putString("stream", failure.stackTraceToString())
            finish(android.app.Activity.RESULT_CANCELED, output)
        }
    }
}
