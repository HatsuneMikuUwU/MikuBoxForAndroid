package com.miku.ray

/** Implementations must isolate probes from the application's connected core. */
object MikuDiagnostics {
    interface Impl {
        fun attach(service: android.app.Service) = Unit
        fun detach(service: android.app.Service) = Unit
        fun delay(config: String, url: String): Long
        fun tcp(config: String): Long
        fun country(config: String): String?
    }
    var impl: Impl? = null
}
