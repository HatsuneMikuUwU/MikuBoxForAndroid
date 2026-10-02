package com.mikubox.mihomo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.launch

/**
 * The application class.
 *
 * It extends the vendored `AngApplication` rather than `Application`: MikuRay's
 * screens ask for it by name — its view models are `AndroidViewModel`s typed to
 * that class, and its activity lifecycle callbacks are what apply the theme, the
 * font and the locale — so inheriting from it is what lets the vendored code run
 * unchanged. `super.onCreate()` brings up its settings store and theme; MikuBox's
 * own setup follows.
 */
class MikuApp : com.miku.ray.AngApplication() {

    override fun onCreate() {
        super.onCreate()
        // Hands the vendored MikuRay layer the mihomo side of its core seam, so
        // its connect button drives MikuBox's own tunnel.
        com.mikubox.mihomo.core.MikuRayBridgeContext.attach(this)
        com.miku.ray.MikuDiagnostics.impl = com.mikubox.mihomo.core.NativeProfileProbe
        com.miku.ray.MikuCoreBridge.install(com.mikubox.mihomo.core.MikuRayCoreBridge)
        // The vendored subscription screens read and write this app's profiles
        // through the same seam.
        com.miku.ray.MikuSubscriptions.install(com.mikubox.mihomo.core.MikuRaySubscriptions)
        com.miku.ray.MikuProfiles.install(com.mikubox.mihomo.core.MikuRayProfiles)
        com.miku.ray.MikuRouting.impl = com.mikubox.mihomo.core.MikuRayRoutingMode
        com.miku.ray.MikuSettings.impl = object : com.miku.ray.MikuSettings.Impl {
            override fun selectedProfileId() = com.mikubox.mihomo.profile.MihomoProfileStore.selected(this@MikuApp)?.id
            override fun vpnFragment() = com.mikubox.mihomo.core.MihomoVpnSettingsFragment()
            override fun advancedFragment() = com.mikubox.mihomo.core.MihomoAdvancedSettingsFragment()
            override fun coreFragment() = com.mikubox.mihomo.core.MihomoSettingsFragment()
            override fun proxyCredentials(): Pair<String, String>? = com.mikubox.mihomo.core.CoreOverrides.proxyCredentials(this@MikuApp)
            override fun mixedPort() = com.mikubox.mihomo.core.MihomoCoreSettings.listeningPort(this@MikuApp)
        }
        if (com.mikubox.mihomo.core.NativeProfileProbe.isProbeProcess(this)) return
        // Alarms do not survive a reboot, so a tunnel that was up before it —
        // or before the process was killed and never brought back — would stay
        // down with the expectation still recorded. Re-arming here (every
        // process start, from any entry point) hands it back to the guard,
        // which restores the connection if it really is missing. A disconnect
        // clears the expectation, so nothing here resurrects a stopped tunnel.
        runCatching {
            if (com.mikubox.mihomo.service.TunnelGuard.isExpected(this) &&
                !com.mikubox.mihomo.service.MikuVpnService.running
            ) {
                com.mikubox.mihomo.service.TunnelGuard.schedule(this)
            }
        }
        // Opt-in cold-start auto connect. It only fires when the user both
        // enabled the switch and granted VPN consent earlier, and the tunnel is
        // neither running nor already owed by the guard — the guard stays the
        // sole owner of resurrecting an expected tunnel. connect() swallows a
        // rejected background start itself, so a process spun up from the
        // background (sync job, broadcast) degrades to a log line, not a crash.
        runCatching {
            if (com.mikubox.mihomo.core.MihomoCoreSettings.autoConnectOnStart(this) &&
                android.net.VpnService.prepare(this) == null &&
                !com.mikubox.mihomo.service.MikuVpnService.running &&
                !com.mikubox.mihomo.service.TunnelGuard.isExpected(this)
            ) {
                com.mikubox.mihomo.service.VpnController.connect(this)
            }
        }
        // Scheduling does not gate the first frame. The home screen refreshes
        // its persisted profile mirror on IO when resumed, instead of parsing
        // every configuration here and repeating it on the main thread there.
        Thread({
            com.mikubox.mihomo.profile.MihomoSubscriptionUpdater.reconfigure(this)
        }, "mihomo-subscription-init").start()
        // Section artwork: MikuRay's own default is the `gradient` style, which is
        // also the only one drawn as a shape badge with a tinted glyph — the other
        // seventeen are character artwork. Seeded once so a fresh install matches
        // the target; a later choice in Settings → UI sticks.
        runCatching {
            val marker = "category_style_seeded"
            if (!com.miku.ray.handler.MmkvManager.decodeSettingsBool(marker, false)) {
                com.miku.ray.handler.MmkvManager.encodeSettings(com.miku.ray.AppConfig.PREF_CATEGORY_STYLE, "gradient")
                com.miku.ray.handler.MmkvManager.encodeSettings(marker, true)
            }
        }
        // The row's traffic line is behind its own switch: the numbers are there
        // to be shown, so it is seeded on as well. Its own marker rather than the
        // one above, which belonged to the section artwork and would have shipped
        // this default to nobody.
        runCatching {
            val marker = "traffic_line_seeded"
            if (!com.miku.ray.handler.MmkvManager.decodeSettingsBool(marker, false)) {
                com.miku.ray.handler.MmkvManager.encodeSettings(com.miku.ray.AppConfig.PREF_TRAFFIC_ENABLED, true)
                com.miku.ray.handler.MmkvManager.encodeSettings(marker, true)
            }
        }
        // The splash the reference shows on every start is behind its own switch,
        // which defaults to off; a fresh install gets it on, and the switch in
        // the UI settings can turn it off again.
        runCatching {
            val marker = "splash_seeded"
            if (!com.miku.ray.handler.MmkvManager.decodeSettingsBool(marker, false)) {
                com.miku.ray.handler.MmkvManager.encodeSettings(com.miku.ray.AppConfig.PREF_SHOW_SPLASH, true)
                com.miku.ray.handler.MmkvManager.encodeSettings(marker, true)
            }
        }
        // Night mode and the language are MikuRay's own settings and it applies
        // both itself — `AngApplication.onCreate` sets the night mode before this
        // runs, and every ported activity wraps its context with the chosen
        // locale. This app kept a second copy of both, which is what the app's
        // settings store used to hold; it is gone.
        // Update auto-check: opt-in from About & updates (off by default). The
        // process-wide scope outlives SplashActivity, whose own lifecycle would
        // cancel a per-activity launch before the request could finish.
        if (com.miku.ray.handler.MmkvManager.decodeSettingsBool(com.miku.ray.AppConfig.PREF_AUTO_CHECK_UPDATE, false)) {
            val checkScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
            checkScope.launch {
                val result = runCatching {
                    com.miku.ray.handler.UpdateCheckerManager.checkForUpdate(
                        this@MikuApp,
                        com.miku.ray.handler.MmkvManager.decodeSettingsBool(
                            com.miku.ray.AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false
                        ),
                    )
                }.getOrNull()
                if (result?.hasUpdate == true) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@MikuApp,
                            getString(com.mikubox.mihomo.R.string.update_available_toast, result.latestVersion),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // The palette is MikuRay's business: its own base activity applies the
        // chosen family, dynamic colour or custom seed. This app used to theme
        // every activity from its own manager first, and the two of them fought
        // over the same activity.

        // The MikuRay font setting applies to the whole app: the global typeface
        // override covers what the platform creates from here on, and the view-tree
        // pass covers what this activity already inflated. This is the same pair
        // AngApplication applies from its own activity callbacks.
        com.miku.ray.AngApplication.getCustomTypeface(this)?.let { typeface ->
            com.miku.ray.util.CustomFontManager.applyGlobalOverride(typeface)
            com.miku.ray.util.CustomFontManager.applyToViewTree(typeface, activity.window.decorView)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) {
        if (com.mikubox.mihomo.core.OnDemandSettings.enabled(this)) {
            runCatching { com.mikubox.mihomo.service.OnDemandService.refresh(activity) }
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
