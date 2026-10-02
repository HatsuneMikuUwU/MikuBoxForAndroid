package com.miku.ray.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

object ThemeShareManager {
    const val MIME_TYPE = "application/vnd.mikuray.theme+json"
    const val FILE_EXTENSION = ".mikutheme"

    private const val FORMAT = "mikuray-ui-theme"
    private const val FORMAT_VERSION = 1
    private const val MAX_THEME_FILE_BYTES = 50L * 1024L * 1024L
    private const val MAX_ASSET_BYTES = 12L * 1024L * 1024L

    private data class AssetSpec(
        val id: String,
        val preferenceKey: String,
        val filePrefix: String
    )

    private val bannerAssets = listOf(
        AssetSpec("homeBanner", AppConfig.PREF_CUSTOM_HOME_BANNER_URI, "home_banner_"),
        AssetSpec("sheetBanner", AppConfig.PREF_CUSTOM_SHEET_BANNER_URI, "sheet_banner_"),
        AssetSpec("selectedBanner", AppConfig.PREF_SELECTED_BANNER_URI, "selected_banner_"),
        AssetSpec("profileBanner", AppConfig.PREF_PROFILE_BANNER_URI, "profile_banner_"),
        AssetSpec("themeBanner", AppConfig.PREF_CUSTOM_THEME_BANNER_URI, "theme_banner_"),
        AssetSpec("settingsCharacter", AppConfig.PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI, "settings_character_")
    )

    private val soundAssets = listOf(
        AssetSpec("connectSound", AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI, "connect_sound_"),
        AssetSpec("disconnectSound", AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI, "disconnect_sound_")
    )

    private val booleanKeys = setOf(
        AppConfig.PREF_TRAFFIC_ENABLED,
        AppConfig.PREF_SPEED_ENABLED,
        AppConfig.PREF_HIDE_DIRECT_TRAFFIC_NOTIFICATION,
        AppConfig.PREF_SHOW_GROUP_NAME_NOTIFICATION,
        AppConfig.PREF_NETWORK_SECURITY_ENABLED,
        AppConfig.PREF_DISABLE_SENSOR_TEXT,
        AppConfig.PREF_SHOW_ISP_INFO,
        AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP,
        AppConfig.PREF_CONFIRM_REMOVE,
        AppConfig.PREF_START_SCAN_IMMEDIATE,
        AppConfig.PREF_GROUP_ALL_DISPLAY,
        AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
        AppConfig.PREF_HIDE_SCROLL_BUTTONS,
        AppConfig.PREF_COMPACT_LIST_ACTIONS,
        AppConfig.PREF_FAB_EXTENDED,
        AppConfig.PREF_DYNAMIC_COLOR,
        AppConfig.PREF_DYNAMIC_COLOR_BANNER,
        AppConfig.PREF_TRUE_BLACK,
        AppConfig.PREF_USE_CUSTOM_COLOR,
        AppConfig.PREF_SHOW_SPLASH,
        AppConfig.PREF_APP_FONT_USE_CUSTOM,
        AppConfig.PREF_SEARCH_CHIP_GRADIENT,
        AppConfig.PREF_ENABLE_BLUR,
        AppConfig.PREF_USE_SYSTEM_BLUR,
        AppConfig.PREF_BLUR_BOTTOM_STATUS,
        AppConfig.PREF_BLUR_BOTTOM_BLOB_ANIM,
        AppConfig.PREF_DISABLE_HOME_BANNER,
        AppConfig.PREF_ENABLE_PARTICLES_SHEET,
        AppConfig.PREF_SHOW_QUICK_ACTIONS,
        AppConfig.PREF_ENABLE_SNOWFLAKES,
        AppConfig.PREF_SELECTED_BANNER_STYLE_ENABLED,
        AppConfig.PREF_SOUND_ON_CONNECT,
        AppConfig.PREF_TOOLBAR_CENTER_SUBTITLE_MODE
    )

    private val intKeys = setOf(
        AppConfig.PREF_CUSTOM_DPI,
        AppConfig.PREF_BLUR_RADIUS,
        AppConfig.PREF_BLUR_ROUNDS,
        AppConfig.PREF_BLUR_BOTTOM_ALPHA,
        AppConfig.PREF_HOME_BANNER_HEIGHT,
        AppConfig.PREF_HEADER_TOP_ROW_PADDING,
        AppConfig.PREF_SHEET_BANNER_DIM,
        AppConfig.PREF_SELECTED_BANNER_DIM,
        AppConfig.PREF_SNOWFLAKES_COUNT,
        AppConfig.PREF_BLUR_INTENSITY,
        AppConfig.PREF_BLUR_BOTTOM_INTENSITY,
        AppConfig.PREF_BANNER_COLOR,
        AppConfig.PREF_CUSTOM_COLOR
    )

    private val floatKeys = setOf(
        AppConfig.PREF_APP_FONT_SIZE,
        AppConfig.PREF_SNOWFLAKES_SPEED,
        AppConfig.PREF_SNOWFLAKES_SIZE,
        AppConfig.PREF_SNOWFLAKES_OPACITY,
        AppConfig.PREF_SNOWFLAKES_WIND,
        AppConfig.PREF_SNOWFLAKES_LIFE,
        AppConfig.PREF_PARTICLES_FRAME_DELAY,
        AppConfig.PREF_PARTICLES_LINE_LENGTH,
        AppConfig.PREF_PARTICLES_LINE_THICKNESS,
        AppConfig.PREF_PARTICLES_RADIUS_MAX,
        AppConfig.PREF_PARTICLES_RADIUS_MIN,
        AppConfig.PREF_PARTICLES_DENSITY,
        AppConfig.PREF_PARTICLES_SPEED_FACTOR,
        AppConfig.PREF_BANNER_CHARACTER_WIDTH,
        AppConfig.PREF_BANNER_CHARACTER_HEIGHT,
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_TOP,
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_BOTTOM,
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_END,
        AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LAT,
        AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LON,
        AppConfig.PREF_BLUR_BOTTOM_RADIUS
    )

    private val stringKeys = setOf(
        AppConfig.PREF_LANGUAGE,
        AppConfig.PREF_APP_THEME,
        AppConfig.PREF_UI_MODE_NIGHT,
        AppConfig.PREF_BANNER_SETTINGS_CHARACTER,
        AppConfig.PREF_WEATHER_CUSTOM_LOCATION_NAME,
        AppConfig.PREF_ICON_SHAPE,
        AppConfig.PREF_ARROW_SHAPE,
        AppConfig.PREF_APP_ICON,
        AppConfig.PREF_CUSTOM_APP_NAME,
        AppConfig.PREF_CATEGORY_STYLE,
        AppConfig.PREF_GROUP_ALL_TAB_ICON,
        AppConfig.PREF_TAB_BADGE_LIMIT,
        AppConfig.PREF_APP_FONT,
        AppConfig.PREF_SEARCH_BAR_CHIP,
        AppConfig.PREF_WEATHER_USE_CELSIUS,
        AppConfig.PREF_WEATHER_CUSTOM_LOCATION,
        AppConfig.PREF_INDICATOR_STYLE,
        AppConfig.PREF_CUSTOM_PROFILE_NAME,
        AppConfig.PREF_PROFILE_BANNER_SHAPE,
        AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI,
        AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI,
        AppConfig.PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI
    )

    sealed class ImportResult {
        data class Success(val importedAssetCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    @Throws(IOException::class)
    fun exportTo(context: Context, destination: Uri) {
        val document = JSONObject().apply {
            put("format", FORMAT)
            put("version", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("settings", exportSettings())
            put("assets", exportAssets(context))
        }
        context.contentResolver.openOutputStream(destination)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(document.toString())
        } ?: throw IOException("Unable to open the destination file.")
    }

    fun importFrom(context: Context, source: Uri): ImportResult {
        return try {
            val rawJson = context.contentResolver.openInputStream(source)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText().also { text ->
                    if (text.toByteArray(Charsets.UTF_8).size > MAX_THEME_FILE_BYTES) {
                        throw IOException("The theme file exceeds the 50 MB size limit.")
                    }
                }
            } ?: return ImportResult.Error("Unable to read the theme file.")

            val document = JSONObject(rawJson)
            if (document.optString("format") != FORMAT || document.optInt("version") != FORMAT_VERSION) {
                return ImportResult.Error("This file is not a supported MikuRay theme.")
            }

            val assets = document.optJSONArray("assets") ?: JSONArray()
            validateAssets(assets)
            importSettings(document.optJSONObject("settings") ?: JSONObject())
            val importedAssets = importAssets(context, assets)
            applyImportedTheme(context)
            ImportResult.Success(importedAssets)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "The theme file is invalid or could not be imported.")
        }
    }

    private fun exportSettings(): JSONObject = JSONObject().apply {
        booleanKeys.forEach { key ->
            putTyped(key, "boolean", MmkvManager.decodeSettingsBool(key, defaultBooleanValue(key)))
        }
        intKeys.forEach { key -> putTyped(key, "int", MmkvManager.decodeSettingsInt(key, defaultIntValue(key))) }
        floatKeys.forEach { key -> putTyped(key, "float", MmkvManager.decodeSettingsFloat(key, defaultFloatValue(key))) }
        stringKeys.forEach { key ->
            MmkvManager.decodeSettingsString(key)?.let { putTyped(key, "string", it) }
        }
    }

    private fun defaultBooleanValue(key: String): Boolean = when (key) {
        AppConfig.PREF_CONFIRM_REMOVE -> true
        AppConfig.PREF_SOUND_ON_CONNECT -> true
        else -> false
    }

    private fun defaultIntValue(key: String): Int = when (key) {
        AppConfig.PREF_BLUR_RADIUS -> AppConfig.DEFAULT_BLUR_RADIUS
        AppConfig.PREF_BLUR_ROUNDS -> AppConfig.DEFAULT_BLUR_ROUNDS
        AppConfig.PREF_BLUR_BOTTOM_ALPHA -> AppConfig.DEFAULT_BLUR_BOTTOM_ALPHA
        AppConfig.PREF_HOME_BANNER_HEIGHT -> AppConfig.HOME_BANNER_HEIGHT_DEFAULT
        AppConfig.PREF_HEADER_TOP_ROW_PADDING -> AppConfig.HEADER_TOP_ROW_PADDING_DEFAULT
        AppConfig.PREF_SHEET_BANNER_DIM -> AppConfig.SHEET_BANNER_DIM_DEFAULT
        AppConfig.PREF_SELECTED_BANNER_DIM -> AppConfig.SELECTED_BANNER_DIM_DEFAULT
        AppConfig.PREF_SNOWFLAKES_COUNT -> AppConfig.SNOWFLAKES_COUNT_DEFAULT
        else -> 0
    }

    private fun defaultFloatValue(key: String): Float = when (key) {
        AppConfig.PREF_APP_FONT_SIZE -> AppConfig.FONT_SIZE_DEFAULT
        AppConfig.PREF_SNOWFLAKES_SPEED -> AppConfig.SNOWFLAKES_SPEED_DEFAULT
        AppConfig.PREF_SNOWFLAKES_SIZE -> AppConfig.SNOWFLAKES_SIZE_DEFAULT
        AppConfig.PREF_SNOWFLAKES_OPACITY -> AppConfig.SNOWFLAKES_OPACITY_DEFAULT
        AppConfig.PREF_SNOWFLAKES_WIND -> AppConfig.SNOWFLAKES_WIND_DEFAULT
        AppConfig.PREF_SNOWFLAKES_LIFE -> AppConfig.SNOWFLAKES_LIFE_DEFAULT
        AppConfig.PREF_PARTICLES_FRAME_DELAY -> AppConfig.PARTICLES_FRAME_DELAY_DEFAULT
        AppConfig.PREF_PARTICLES_LINE_LENGTH -> AppConfig.PARTICLES_LINE_LENGTH_DEFAULT
        AppConfig.PREF_PARTICLES_LINE_THICKNESS -> AppConfig.PARTICLES_LINE_THICKNESS_DEFAULT
        AppConfig.PREF_PARTICLES_RADIUS_MAX -> AppConfig.PARTICLES_RADIUS_MAX_DEFAULT
        AppConfig.PREF_PARTICLES_RADIUS_MIN -> AppConfig.PARTICLES_RADIUS_MIN_DEFAULT
        AppConfig.PREF_PARTICLES_DENSITY -> AppConfig.PARTICLES_DENSITY_DEFAULT
        AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LON -> AppConfig.PARTICLES_SPEED_FACTOR_DEFAULT
        AppConfig.PREF_BLUR_BOTTOM_RADIUS -> AppConfig.DEFAULT_BLUR_BOTTOM_RADIUS
        AppConfig.PREF_BANNER_CHARACTER_WIDTH -> AppConfig.BANNER_CHARACTER_WIDTH_DEFAULT
        AppConfig.PREF_BANNER_CHARACTER_HEIGHT -> AppConfig.BANNER_CHARACTER_HEIGHT_DEFAULT
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_TOP -> AppConfig.BANNER_CHARACTER_MARGIN_TOP_DEFAULT
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_BOTTOM -> AppConfig.BANNER_CHARACTER_MARGIN_BOTTOM_DEFAULT
        AppConfig.PREF_BANNER_CHARACTER_MARGIN_END -> AppConfig.BANNER_CHARACTER_MARGIN_END_DEFAULT
        else -> 0f
    }

    private fun defaultStringValue(key: String): String? = when (key) {
        AppConfig.PREF_ICON_SHAPE -> AppConfig.PREF_ICON_SHAPE_DEFAULT
        AppConfig.PREF_ARROW_SHAPE -> AppConfig.PREF_ARROW_SHAPE_DEFAULT
        AppConfig.PREF_PROFILE_BANNER_SHAPE -> AppConfig.PREF_PROFILE_BANNER_SHAPE_DEFAULT
        AppConfig.PREF_BANNER_SETTINGS_CHARACTER -> AppConfig.PREF_BANNER_SETTINGS_CHARACTER_DEFAULT
        else -> null
    }

    private fun JSONObject.putTyped(key: String, type: String, value: Any) {
        put(key, JSONObject().apply {
                put("type", type)
                put("value", value)
        })
    }

    @Throws(IOException::class)
    private fun exportAssets(context: Context): JSONArray = JSONArray().apply {
        (bannerAssets + soundAssets).forEach { spec ->
            assetFromUri(context, spec.id, spec.preferenceKey, spec.filePrefix)?.let(::put)
        }
        CustomFontManager.getFontFile(context)?.takeIf { it.exists() }?.let { file ->
            if (file.length() <= MAX_ASSET_BYTES) {
                put(JSONObject().apply {
                        put("id", "customFont")
                        put("displayName", CustomFontManager.getFontDisplayName() ?: file.name)
                        put("fileName", file.name)
                        put("base64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                })
            }
        }
    }

    @Throws(IOException::class)
    private fun assetFromUri(
        context: Context,
        id: String,
        preferenceKey: String,
        filePrefix: String
    ): JSONObject? {
        val uriString = MmkvManager.decodeSettingsString(preferenceKey).orEmpty()
        if (uriString.isBlank()) return null
        val uri = Uri.parse(uriString)
        val bytes = when (uri.scheme) {
            "file" -> File(uri.path.orEmpty()).takeIf { it.exists() }?.readBytes()
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return null
        if (bytes.size > MAX_ASSET_BYTES) return null
        return JSONObject().apply {
            put("id", id)
            put("fileName", "$filePrefix${System.currentTimeMillis()}.${if (id.endsWith("Sound")) "bin" else "jpg"}")
            put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
    }

    @Throws(IOException::class)
    private fun validateAssets(assets: JSONArray) {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: throw IOException("The theme asset at index $index is invalid.")
            val id = asset.optString("id")
            if (id !in (bannerAssets + soundAssets).map(AssetSpec::id) && id != "customFont") continue
            if (decodeAsset(asset) == null) {
                throw IOException("The theme asset '$id' is corrupt or exceeds the size limit.")
            }
        }
    }

    private fun importSettings(settings: JSONObject) {
        // For every known setting key, apply the value from the theme file when present;
        // otherwise fall back to the default value instead of leaving the old value untouched.
        booleanKeys.forEach { key ->
            val typedValue = settings.optJSONObject(key)
            val value = if (typedValue != null && typedValue.optString("type") == "boolean") {
                typedValue.optBoolean("value")
            } else {
                defaultBooleanValue(key)
            }
            MmkvManager.encodeSettings(key, value)
        }
        intKeys.forEach { key ->
            val typedValue = settings.optJSONObject(key)
            val value = if (typedValue != null && typedValue.optString("type") == "int") {
                typedValue.optInt("value")
            } else {
                defaultIntValue(key)
            }
            MmkvManager.encodeSettings(key, value)
        }
        floatKeys.forEach { key ->
            val typedValue = settings.optJSONObject(key)
            val value = if (typedValue != null && typedValue.optString("type") == "float") {
                typedValue.optDouble("value").toFloat()
            } else {
                defaultFloatValue(key)
            }
            MmkvManager.encodeSettings(key, value)
        }
        stringKeys.forEach { key ->
            val typedValue = settings.optJSONObject(key)
            val value = if (typedValue != null && typedValue.optString("type") == "string") {
                typedValue.optString("value")
            } else {
                defaultStringValue(key)
            }
            MmkvManager.encodeSettings(key, value)
        }
    }

    @Throws(IOException::class)
    private fun importAssets(context: Context, assets: JSONArray): Int {
        clearExistingAssets(context)
        val bannerDirectory = File(context.filesDir, "banners").apply { mkdirs() }
        val soundDirectory = File(context.filesDir, "sounds").apply { mkdirs() }
        var importedCount = 0
        var importedFont = false

        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val bytes = decodeAsset(asset) ?: continue
            when (asset.optString("id")) {
                "customFont" -> {
                    val extension = asset.optString("fileName").substringAfterLast('.', "ttf").lowercase()
                    if (extension !in setOf("ttf", "otf", "ttc")) continue
                    val temporary = File(context.cacheDir, "theme_import_font.$extension")
                    temporary.writeBytes(bytes)
                    val restored = CustomFontManager.restoreFontFile(
                        context,
                        temporary,
                        asset.optString("displayName").ifBlank { null }
                    )
                    temporary.delete()
                    if (restored != null) {
                        MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, true)
                        importedFont = true
                        importedCount++
                    }
                }
                else -> {
                    val spec = (bannerAssets + soundAssets).firstOrNull { it.id == asset.optString("id") } ?: continue
                    val oldUri = MmkvManager.decodeSettingsString(spec.preferenceKey)
                    deleteLocalFile(oldUri)
                    val isSound = spec in soundAssets
                    val extension = asset.optString("fileName").substringAfterLast('.', if (isSound) "bin" else "jpg")
                    .lowercase().takeIf { if (isSound) it.length in 1..8 else it in setOf("jpg", "jpeg", "png", "webp") }
                    ?: if (isSound) "bin" else "jpg"
                    val destinationDirectory = if (isSound) soundDirectory else bannerDirectory
                    val destination = File(destinationDirectory, "${spec.filePrefix}${System.currentTimeMillis()}.$extension")
                    destination.writeBytes(bytes)
                    MmkvManager.encodeSettings(spec.preferenceKey, Uri.fromFile(destination).toString())
                    importedCount++
                }
            }
        }

        if (!importedFont) {
            MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            CustomFontManager.clearFont(context)
        }
        return importedCount
    }

    private fun clearExistingAssets(context: Context) {
        (bannerAssets + soundAssets).forEach { spec ->
            deleteLocalFile(MmkvManager.decodeSettingsString(spec.preferenceKey))
            MmkvManager.encodeSettings(spec.preferenceKey, "")
        }
        CustomFontManager.clearFont(context)
    }

    private fun decodeAsset(asset: JSONObject): ByteArray? {
        return try {
            val encoded = asset.optString("base64")
            if (encoded.isBlank()) null else {
                Base64.decode(encoded, Base64.DEFAULT).takeIf { it.size <= MAX_ASSET_BYTES }
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun deleteLocalFile(uriString: String?) {
        val uri = uriString?.let(Uri::parse) ?: return
        if (uri.scheme == "file") {
            File(uri.path.orEmpty()).takeIf { it.exists() }?.delete()
        }
    }

    private fun applyImportedTheme(context: Context) {
        CustomFontManager.getFontFile(context)?.let { _ ->
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                CustomFontManager.applyGlobalOverride(context)
            }
        }
        // Writing PREF_APP_ICON / PREF_CUSTOM_APP_NAME alone doesn't change what's shown on the
        // launcher -- the actual icon/label come from an activity-alias that must be toggled via
        // LauncherAliasSwitcher, otherwise the imported icon/name silently has no visible effect.
        LauncherAliasSwitcher.applyAliases(
            context,
            LauncherAliasSwitcher.currentIconVariant(),
            LauncherAliasSwitcher.currentNameVariant()
        )
        SettingsChangeManager.notifyUiCustomizationChanged()
        SelectedProfileBannerController.notifyChanged(context)
    }
}
