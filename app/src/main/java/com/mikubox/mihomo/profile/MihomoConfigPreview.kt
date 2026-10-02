package com.mikubox.mihomo.profile

import com.mikubox.mihomo.core.MihomoCore

/**
 * Best-effort reader for the sections of a Mihomo YAML profile, used to show
 * nodes, groups and rules while the core is not running.
 *
 * Deliberately line-based: the app never re-serializes profile YAML, so a
 * tolerant scan of the conventional block style is enough for a read-only
 * preview. Flow-style entries and provider-backed groups stay invisible here;
 * once connected, the live data comes from the core instead.
 */
object MihomoConfigPreview {

    data class Node(val name: String, val type: String)

    data class Group(val name: String, val type: String, val members: List<String>)

    fun nodes(config: String): Map<String, Node> {
        val nodes = LinkedHashMap<String, Node>()
        var name: String? = null
        var type = ""

        fun flush() {
            name?.let { nodes[it] = Node(it, type) }
            name = null
            type = ""
        }

        for (line in sectionLines(config, "proxies")) {
            val trimmed = line.trim()
            val indent = line.length - line.trimStart().length
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> Unit
                indent == 2 && trimmed.startsWith("- ") -> {
                    flush()
                    val entry = trimmed.removePrefix("- ")
                    // Flow-style entries stay invisible, as documented; reading
                    // them line-wise would only mint garbage names.
                    if (!entry.startsWith("{")) {
                        name = scalar(entry.substringAfter("name:"))
                    }
                }
                indent == 4 && trimmed.startsWith("type:") -> type = scalar(trimmed.removePrefix("type:"))
            }
        }
        flush()
        return nodes
    }

    fun groups(config: String): List<Group> {
        val groups = mutableListOf<Group>()
        var name: String? = null
        var type = ""
        var members = mutableListOf<String>()
        var inProxies = false

        fun flush() {
            name?.let { groups += Group(it, type, members) }
            name = null
            type = ""
            members = mutableListOf()
            inProxies = false
        }

        for (line in sectionLines(config, "proxy-groups")) {
            val trimmed = line.trim()
            val indent = line.length - line.trimStart().length
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> Unit
                indent == 2 && trimmed.startsWith("- ") -> {
                    flush()
                    name = scalar(trimmed.removePrefix("- ").substringAfter("name:"))
                }
                indent == 4 && trimmed.startsWith("type:") -> {
                    type = scalar(trimmed.removePrefix("type:"))
                    inProxies = false
                }
                indent == 4 -> inProxies = trimmed == "proxies:"
                indent >= 6 && inProxies && trimmed.startsWith("- ") -> {
                    val member = scalar(trimmed.removePrefix("- "))
                    if (member.isNotEmpty()) members += member
                }
            }
        }
        flush()
        return groups
    }

    fun rules(config: String): List<MihomoCore.Rule> =
        sectionLines(config, "rules")
            .map(String::trim)
            .filter { it.startsWith("- ") }
            .mapNotNull(::parseRule)

    /**
     * Splits one `TYPE,PAYLOAD,TARGET[,flag]` rule. Sub-rules (AND/OR/NOT) can
     * contain commas inside their expression; the payload then keeps them all
     * while type and target still resolve correctly.
     */
    private fun parseRule(entry: String): MihomoCore.Rule? {
        val parts = scalar(entry.removePrefix("- ")).split(',').map(String::trim)
        val withoutFlags = parts.dropLastWhile { it.lowercase() in RULE_FLAGS }
        if (withoutFlags.size < 2) return null
        return MihomoCore.Rule(
            type = withoutFlags.first(),
            payload = withoutFlags.subList(1, withoutFlags.size - 1).joinToString(","),
            target = withoutFlags.last(),
        )
    }

    /** Raw lines of one top-level `key:` section, up to the next top-level key. */
    private fun sectionLines(config: String, key: String): List<String> {
        val lines = config.lines()
        val start = lines.indexOfFirst { it.startsWith("$key:") }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { line ->
            line.isBlank() || line.startsWith(" ") || line.startsWith("\t") || line.trimStart().startsWith("#")
        }
    }

    private fun scalar(value: String): String =
        value.trim().removeSurrounding("'").removeSurrounding("\"")

    private val RULE_FLAGS = setOf("no-resolve", "src")
}
