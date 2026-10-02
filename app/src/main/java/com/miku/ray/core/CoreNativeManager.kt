package com.miku.ray.core

import android.content.Context
import com.miku.ray.MikuCoreBridge
import com.miku.ray.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core bootstrap, on mihomo instead of Xray.
 *
 * MikuRay's version drove the GoMobile bindings (`Seq.setContext`,
 * `Libv2ray.initCoreEnv` and friends). MikuBox's core is mihomo, embedded in the
 * app process and started by its own VPN service, so there is no separate native
 * environment to initialise — the calls are kept, and kept honest: they report
 * what actually happens rather than pretending to configure a core that is not
 * there.
 */
object CoreNativeManager {

    private val initialized = AtomicBoolean(false)

    fun initCoreEnv(context: Context?) {
        if (initialized.compareAndSet(false, true)) {
            // MikuBox prepares the core (home dir, geo databases, config) when the
            // tunnel starts, so nothing to do here beyond remembering that we ran.
            LogUtil.i(com.miku.ray.AppConfig.TAG, "core environment provided by the embedded mihomo core")
        }
    }

    fun reconcileBrowserDialer(dialerAddr: String) {
        // The browser dialer is an Xray feature; mihomo has no equivalent.
        LogUtil.d(com.miku.ray.AppConfig.TAG, "browser dialer is not part of the embedded core: $dialerAddr")
    }

    fun getLibVersion(): String = runCatching {
        MikuCoreBridge.version()
    }.getOrElse {
        LogUtil.e(com.miku.ray.AppConfig.TAG, "Failed to read core version", it)
        "Unknown"
    }

    fun measureOutboundDelay(config: String, testUrl: String): Long =
        MikuCoreBridge.measureDelay(config, testUrl)

    fun newCoreController(handler: CoreCallbackHandler): CoreController =
        MihomoCoreController(handler)
}
