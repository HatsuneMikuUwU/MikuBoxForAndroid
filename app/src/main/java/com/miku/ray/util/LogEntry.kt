package com.miku.ray.util

data class LogEntry(
    val timestamp: String,
    val level: Char,
    val tag: String,
    val meta: String,
    val message: String,
    val raw: String,
) {
    val priority: Int
    get() = LogPriority.fromLevelChar(level)

    companion object {

        private val PATTERN = Regex(
            """^(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(([^)]*)\):\s?(.*)$"""
        )

        fun parse(line: String): LogEntry {
            val match = PATTERN.matchEntire(line)
            return if (match != null) {
                val (ts, level, tag, meta, message) = match.destructured
                LogEntry(ts, level[0], tag.trim(), meta.trim(), message, line)
            } else {

                LogEntry(timestamp = "", level = 'I', tag = "", meta = "", message = line, raw = line)
            }
        }

        fun parseAll(lines: List<String>): List<LogEntry> = lines.map(::parse)
    }
}
