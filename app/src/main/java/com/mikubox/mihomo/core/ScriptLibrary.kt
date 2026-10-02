package com.mikubox.mihomo.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** IDs survive renames; scripts and bindings are committed as one document. */
object ScriptLibrary {
    const val KEY = "script.library"
    const val NONE = "@none"
    data class Script(val id: String, val name: String, val source: String)
    data class Library(val scripts: List<Script>, val bindings: Map<String, String>)

    fun decode(text: String): Library {
        if (text.isBlank()) return Library(emptyList(), emptyMap())
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported script library version" }
        val array = root.getJSONArray("scripts")
        val scripts = (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            Script(entry.getString("id"), entry.getString("name"), entry.getString("source")).also {
                require(it.id.isNotBlank() && it.id != NONE && it.id.length <= 128)
                validateContent(it.name, it.source)
            }
        }
        require(scripts.map { it.id }.distinct().size == scripts.size) { "Duplicate script ID" }
        require(scripts.map { it.name }.distinct().size == scripts.size) { "Duplicate script name" }
        val bindings = root.getJSONObject("bindings").let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        require(bindings.keys.all(String::isNotBlank))
        require(bindings.values.all { it == NONE || scripts.any { script -> script.id == it } }) { "Unknown bound script" }
        return Library(scripts, bindings)
    }
    private fun validateContent(name: String, source: String) {
        require(name.isNotBlank() && name.length <= 80) { "Script name must contain 1–80 characters" }
        require(source.isNotBlank() && source.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "Script must contain 1–262144 UTF-8 bytes" }
    }
    @Synchronized fun read(context: Context) = decode(CoreOverrides.extra(context, KEY))
    private fun write(context: Context, library: Library) {
        val scripts = JSONArray(library.scripts.map { JSONObject().put("id", it.id).put("name", it.name).put("source", it.source) })
        val text = JSONObject().put("version", 1).put("scripts", scripts).put("bindings", JSONObject(library.bindings)).toString()
        decode(text)
        CoreOverrides.setExtra(context, KEY, text)
    }
    @Synchronized fun save(context: Context, id: String?, name: String, source: String): Script {
        val library = read(context)
        val cleanName = name.trim()
        validateContent(cleanName, source)
        require(library.scripts.none { it.id != id && it.name == cleanName }) { "Script name already exists" }
        if (id != null) require(library.scripts.any { it.id == id }) { "Script was deleted" }
        val script = Script(id ?: UUID.randomUUID().toString(), cleanName, source)
        val scripts = if (id == null) library.scripts + script else library.scripts.map { if (it.id == id) script else it }
        write(context, library.copy(scripts = scripts))
        return script
    }
    @Synchronized fun delete(context: Context, id: String) {
        val library = read(context)
        // Deleted bindings explicitly disable scripts instead of falling through
        // to a different global script and silently changing routing behavior.
        write(context, Library(library.scripts.filterNot { it.id == id }, library.bindings.mapValues { if (it.value == id) NONE else it.value }))
    }
    @Synchronized fun bind(context: Context, profileId: String, scriptId: String?) {
        require(profileId.isNotBlank())
        val library = read(context)
        require(scriptId == null || scriptId == NONE || library.scripts.any { it.id == scriptId })
        val bindings = library.bindings.toMutableMap()
        if (scriptId == null) bindings.remove(profileId) else bindings[profileId] = scriptId
        write(context, library.copy(bindings = bindings))
    }
    @Synchronized fun removeProfile(context: Context, profileId: String) {
        val library = read(context)
        if (profileId in library.bindings) write(context, library.copy(bindings = library.bindings - profileId))
    }
    fun source(context: Context, profileId: String?): String? {
        val library = read(context)
        return when (val binding = library.bindings[profileId]) {
            NONE -> null
            null -> CoreOverrides.extra(context, "script.source").takeIf {
                CoreOverrides.extra(context, "script.enabled") == "true" && it.isNotBlank()
            }
            else -> library.scripts.first { it.id == binding }.source
        }
    }
}
