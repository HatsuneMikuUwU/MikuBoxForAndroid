package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import libcore.Libcore
import libcore.TestBox

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    private var box: TestBox? = null

    suspend fun doTest(): Int = withContext(Dispatchers.IO) {
        processes = GuardedProcessPool {
            Logs.w(it)
        }
        try {
            init()
            launch()
            if (processes.processCount > 0) {
                // wait for plugin start
                delay(500)
            }
            Libcore.urlTest(box, link, timeout)
        } finally {
            runCatching { close() }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        SagerNet.application.awaitAssetsReady()
        super.loadConfig()
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newTestBox(config.config)
    }

    override fun startCore() {
        box?.start()
    }

    override fun closeCore() {
        runCatching { box?.close() }
        box = null
    }

}
