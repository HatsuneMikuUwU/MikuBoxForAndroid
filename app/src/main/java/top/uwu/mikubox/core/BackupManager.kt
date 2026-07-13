package top.uwu.mikubox.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports/imports every MikuBox preference store (profiles, app settings, VPN
 * settings, per-profile traffic) as a single JSON document, so a full backup
 * survives a reinstall or moves to another device.
 */
object BackupManager {

    private const val VERSION = 1
    private val STORES = listOf(
        "mihomo_profiles",
        "miku_app_settings",
        "mihomo_vpn_settings",
        "mihomo_traffic",
    )

    fun export(context: Context): String {
        val stores = JSONObject()
        for (name in STORES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val entries = JSONObject()
            for ((key, value) in prefs.all) {
                val entry = JSONObject()
                when (value) {
                    is String -> entry.put("t", "s").put("v", value)
                    is Int -> entry.put("t", "i").put("v", value)
                    is Long -> entry.put("t", "l").put("v", value)
                    is Boolean -> entry.put("t", "b").put("v", value)
                    is Float -> entry.put("t", "f").put("v", value.toDouble())
                    is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                    else -> continue
                }
                entries.put(key, entry)
            }
            stores.put(name, entries)
        }
        return JSONObject().put("version", VERSION).put("stores", stores).toString(2)
    }

    fun import(context: Context, json: String) {
        val stores = JSONObject(json).getJSONObject("stores")
        for (name in stores.keys()) {
            if (name !in STORES) continue
            val entries = stores.getJSONObject(name)
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (key in entries.keys()) {
                val entry = entries.getJSONObject(key)
                when (entry.getString("t")) {
                    "s" -> editor.putString(key, entry.getString("v"))
                    "i" -> editor.putInt(key, entry.getInt("v"))
                    "l" -> editor.putLong(key, entry.getLong("v"))
                    "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                    "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                    "ss" -> {
                        val arr = entry.getJSONArray("v")
                        val set = HashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.getString(i))
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.commit()
        }
    }
}
