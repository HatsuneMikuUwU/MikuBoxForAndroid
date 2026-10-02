package com.miku.ray

/** Routing capability implemented by the host core, including offline choices. */
object MikuRouting {
    data class Exit(
        val name: String,
        val group: Boolean,
        /** Live round-trip latency in ms from the core; negative when unknown (offline, untested). */
        val delay: Int = -1,
        /** Core type string, e.g. "Selector" or "Shadowsocks"; empty offline. */
        val type: String = "",
    )
    data class State(val mode: String, val exit: String?, val options: List<Exit>)
    interface Impl {
        fun state(): State
        fun mode(value: String): Boolean
        fun exit(name: String): Boolean
    }
    var impl: Impl? = null
}
