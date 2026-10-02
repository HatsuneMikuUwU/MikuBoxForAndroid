package com.miku.ray.util

import java.util.concurrent.TimeUnit

/** java.lang.Process timed wait is unavailable on Android 7 (our minimum API). */
fun java.lang.Process.waitForCompat(timeout: Long, unit: TimeUnit): Boolean {
    val deadline = System.nanoTime() + unit.toNanos(timeout)
    while (true) {
        try { exitValue(); return true } catch (_: IllegalThreadStateException) { }
        if (System.nanoTime() >= deadline) return false
        Thread.sleep(20)
    }
}
