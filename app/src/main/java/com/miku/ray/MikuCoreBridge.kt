package com.miku.ray

/**
 * Where the vendored MikuRay layer meets MikuBox's core.
 *
 * The vendored code is compiled inside `:mikuray-ui`, which cannot see the app
 * module where the mihomo core and its VPN service live. So the layer it needs
 * is declared here as an interface and the app registers its implementation at
 * startup — the direction of the dependency, not the shape of the code, is what
 * this exists to fix.
 *
 * The default implementation is inert, which keeps the UI previewable (and
 * testable) before the app has registered anything; a real start is refused
 * rather than pretended.
 */
object MikuCoreBridge {

    interface Impl {
        fun isRunning(): Boolean
        fun connectionElapsedMillis(): Long = 0L
        fun startOnBoot(): Boolean = false

        /**
         * True while the service is starting (CONNECTING), before [isRunning]
         * flips. Screens that gate a "reload now" action on [isRunning] treat
         * this as live too — the bridge queues the reload either way.
         */
        fun isConnecting(): Boolean = false

        /** Starts the tunnel with [config]; false when it could not start. */
        fun start(config: String): Boolean

        fun stop(): Boolean

        /** Requests an asynchronous reload of the existing service. */
        fun restart(): Boolean = false

        /** Version string of the embedded core, for the about screen. */
        fun version(): String

        /** Delay in milliseconds for the server described by [config], -1 when unknown. */
        fun measureDelay(config: String, testUrl: String): Long

        /**
         * Delay of the node the running tunnel carries traffic through, -1 when
         * there is no tunnel. This is what "the connection's delay" means for a
         * profile that is a whole configuration.
         */
        fun currentNodeDelay(testUrl: String): Long

        /** `tag,direction,bytes` lines, newest totals per proxy. */
        fun outboundTrafficStats(): String
    }

    private object Inert : Impl {
        override fun isRunning(): Boolean = false
        override fun start(config: String): Boolean = false
        override fun stop(): Boolean = false
        override fun version(): String = ""
        override fun measureDelay(config: String, testUrl: String): Long = -1L
        override fun currentNodeDelay(testUrl: String): Long = -1L
        override fun outboundTrafficStats(): String = ""
    }

    @Volatile
    private var impl: Impl = Inert

    /** Called by the application once, before any ported screen runs. */
    fun install(implementation: Impl) {
        impl = implementation
    }

    fun startOnBoot(): Boolean = impl.startOnBoot()

    fun isRunning(): Boolean = impl.isRunning()

    fun isConnecting(): Boolean = impl.isConnecting()

    fun connectionElapsedMillis(): Long = impl.connectionElapsedMillis()

    fun start(config: String): Boolean = impl.start(config)

    fun stop(): Boolean = impl.stop()

    fun restart(): Boolean = impl.restart()

    fun version(): String = impl.version()

    fun measureDelay(config: String, testUrl: String): Long = impl.measureDelay(config, testUrl)

    fun currentNodeDelay(testUrl: String): Long = impl.currentNodeDelay(testUrl)

    fun outboundTrafficStats(): String = impl.outboundTrafficStats()
}
