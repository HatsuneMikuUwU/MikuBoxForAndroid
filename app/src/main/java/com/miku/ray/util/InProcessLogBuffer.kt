package com.miku.ray.util

import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object InProcessLogBuffer {
    private const val MAX_LINES = 2000

    private val buffer: LinkedList<String> = LinkedList()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(priority: Int, tag: String, message: String) {
        val level = LogPriority.levelChar(priority)
        val threadName = Thread.currentThread().name
        val line = "${fmt.format(Date())} $level/$tag(${Process.myPid()}/$threadName): $message"
        buffer.addLast(line)
        // A VPN session runs for days with verbose logging on and every line
        // lands here forever; a crash report also embeds the whole buffer, so
        // an unbounded list is both a slow memory leak and a growing stall
        // before the dying process can even show the share sheet. The newest
        // MAX_LINES are plenty for diagnosing a crash.
        while (buffer.size > MAX_LINES) buffer.removeFirst()
    }

    @Synchronized
    fun getAll(): List<String> = buffer.toList().reversed()

    @Synchronized
    fun clear() = buffer.clear()
}
