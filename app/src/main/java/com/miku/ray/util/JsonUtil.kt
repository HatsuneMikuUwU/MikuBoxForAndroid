package com.miku.ray.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import com.miku.ray.AppConfig
import java.lang.reflect.Type

object JsonUtil {
    private var gson = Gson()

    fun toJson(src: Any?): String {
        return gson.toJson(src)
    }

    fun <T> fromJson(src: String, cls: Class<T>): T? {
        return gson.fromJson(src, cls)
    }

    fun <T> fromJsonSafe(src: String, cls: Class<T>): T? {
        return try {
            gson.fromJson(src, cls)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse JSON", e)
            null
        }
    }

    fun toJsonPretty(src: Any?): String? {
        if (src == null) return null
        val gsonPre = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(
            object : TypeToken<Double>() {}.type,
            JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? ->
                JsonPrimitive(
                    src?.toInt()
                )
            }
        )
        .create()
        return gsonPre.toJson(src)
    }

    fun parseString(src: String?): JsonObject? {
        if (src == null) return null
        try {
            return JsonParser.parseString(src).getAsJsonObject()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse JSON string", e)
            return null
        }
    }

    fun parseHeadersToMap(headersJson: String?): Map<String, String> {
        val headerMap = mutableMapOf<String, String>()
        val jsonObject = parseString(headersJson) ?: return headerMap
        try {
            for ((key, jsonElement) in jsonObject.entrySet()) {
                if (key.isNullOrBlank() || jsonElement == null || jsonElement.isJsonNull) {
                    continue
                }
                val value = if (jsonElement.isJsonPrimitive) jsonElement.asString else jsonElement.toString()
                if (value.isNotBlank()) {
                    headerMap[key] = value
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse headers JSON", e)
        }
        return headerMap
    }
}
