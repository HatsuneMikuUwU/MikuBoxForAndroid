package com.miku.ray.ui.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.AppNameHelper
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.LauncherAliasSwitcher
import com.miku.ray.util.LogUtil
import com.miku.ray.util.SelectedProfileBannerController
import com.tencent.mmkv.MMKV
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

object BackupManager {
    const val MIME_TYPE = "application/vnd.mikuray.backup+json"
    const val FILE_EXTENSION = ".mikubackup"
    private const val FORMAT = "mikuray-backup"
    private const val FORMAT_VERSION = 1
    private const val MAX_BACKUP_FILE_BYTES = 256L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 128L * 1024L * 1024L

    sealed class ImportResult {
        data class Success(val restoredCount: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    @Throws(IOException::class)
    fun exportTo(context: Context, destination: Uri) {
        val tempRoot = File(context.cacheDir, "mikubackup_export_${System.nanoTime()}").also { it.mkdirs() }
        try {
            val backupDir = File(tempRoot, "data").also { it.mkdirs() }
            val count = MMKV.backupAllToDirectory(backupDir.absolutePath)
            if (count <= 0) {
                throw IOException("MMKV backup produced no data.")
            }

            backupBannerImages(context, backupDir)
            backupCustomFont(context, backupDir)
            backupCustomSounds(context, backupDir)

            val filesArray = JSONArray()
            collectFilesAsBase64(backupDir, "", filesArray)

            val document = JSONObject().apply {
                put("format", FORMAT)
                put("version", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("mmkvCount", count)
                put("files", filesArray)
                com.miku.ray.MikuProfiles.impl?.let { put("mihomo", it.exportBackup()) }
            }

            context.contentResolver.openOutputStream(destination)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(document.toString())
            } ?: throw IOException("Unable to open the destination file.")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    fun exportToCacheFile(context: Context): Pair<Boolean, File?> {
        return try {
            val dateFormatted = java.text.SimpleDateFormat(
                "yyyy-MM-dd-HH-mm-ss",
                java.util.Locale.getDefault()
            ).format(System.currentTimeMillis())
            val outFile = File(
                context.cacheDir,
                "${AppNameHelper.getDisplayName(context)}_$dateFormatted$FILE_EXTENSION"
            )
            val tempRoot = File(context.cacheDir, "mikubackup_export_${System.nanoTime()}").also { it.mkdirs() }
            try {
                val backupDir = File(tempRoot, "data").also { it.mkdirs() }
                val count = MMKV.backupAllToDirectory(backupDir.absolutePath)
                if (count <= 0) {
                    return Pair(false, null)
                }

                backupBannerImages(context, backupDir)
                backupCustomFont(context, backupDir)
                backupCustomSounds(context, backupDir)

                val filesArray = JSONArray()
                collectFilesAsBase64(backupDir, "", filesArray)

                val document = JSONObject().apply {
                    put("format", FORMAT)
                    put("version", FORMAT_VERSION)
                    put("exportedAt", System.currentTimeMillis())
                    put("mmkvCount", count)
                    put("files", filesArray)
                com.miku.ray.MikuProfiles.impl?.let { put("mihomo", it.exportBackup()) }
                }

                outFile.writeText(document.toString(), Charsets.UTF_8)
                Pair(true, outFile)
            } finally {
                tempRoot.deleteRecursively()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "BackupManager.exportToCacheFile failed", e)
            Pair(false, null)
        }
    }

    fun importFrom(context: Context, source: Uri): ImportResult {
        return try {
            val rawJson = context.contentResolver.openInputStream(source)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_BACKUP_FILE_BYTES) {
                    throw IOException("The backup file exceeds the size limit.")
                }
                String(bytes, Charsets.UTF_8)
            } ?: return ImportResult.Error("Unable to read the backup file.")

            importFromJson(context, rawJson)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "The backup file is invalid or could not be imported.")
        }
    }

    fun importFromFile(context: Context, file: File): ImportResult {
        return try {
            if (file.length() > MAX_BACKUP_FILE_BYTES) {
                return ImportResult.Error("The backup file exceeds the size limit.")
            }
            val rawJson = file.readText(Charsets.UTF_8)
            importFromJson(context, rawJson)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "The backup file is invalid or could not be imported.")
        }
    }

    private fun importFromJson(context: Context, rawJson: String): ImportResult {
        val document = JSONObject(rawJson)
        if (document.optString("format") != FORMAT || document.optInt("version") != FORMAT_VERSION) {
            return ImportResult.Error("This file is not a supported MikuRay backup.")
        }

        val nativeBackup = document.optString("mihomo").takeIf { it.isNotBlank() }
        if (com.miku.ray.MikuProfiles.impl != null && nativeBackup == null) {
            return ImportResult.Error("This backup does not contain mihomo profiles.")
        }
        nativeBackup?.let { com.miku.ray.MikuProfiles.impl?.validateBackup(it) }
        val files = document.optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) {
            return ImportResult.Error("Backup contains no data.")
        }

        val restoreDir = File(context.cacheDir, "mikubackup_restore_${System.nanoTime()}").also { it.mkdirs() }
        // The restore is destructive from the moment MMKV is swapped, but the
        // native (SharedPreferences-side) backup import that follows can still
        // fail — leaving new MMKV data under old SP data. Snapshot the current
        // MMKV stores so that window can roll back. The native backup itself
        // was already fully parsed above (validateBackup runs before any of
        // this), so its own apply is the only step expected to fail here.
        val snapshotDir = File(context.cacheDir, "mikubackup_snapshot_${System.nanoTime()}").also { it.mkdirs() }
        // backupAllToDirectory reports the number of instances it persisted,
        // so "taken" means at least one store made it into the snapshot.
        val snapshotTaken: Boolean = try {
            MMKV.backupAllToDirectory(snapshotDir.absolutePath) > 0
        } catch (error: Throwable) {
            LogUtil.e(AppConfig.TAG, "MMKV snapshot before restore failed; continuing without rollback", error)
            false
        }
        try {
            for (i in 0 until files.length()) {
                val entry = files.optJSONObject(i) ?: continue
                val path = entry.optString("path").trim()
                if (path.isEmpty() || path.contains("..")) continue
                val encoded = entry.optString("base64")
                if (encoded.isBlank()) continue
                val bytes = try {
                    Base64.decode(encoded, Base64.DEFAULT).takeIf { it.size <= MAX_ENTRY_BYTES }
                } catch (_: IllegalArgumentException) {
                    null
                } ?: continue

                val dest = File(restoreDir, path)
                dest.parentFile?.mkdirs()
                dest.writeBytes(bytes)
            }

            val count = MMKV.restoreAllFromDirectory(restoreDir.absolutePath)
            if (count <= 0) return ImportResult.Error("MMKV restore produced no data.")
            try {
                nativeBackup?.let { com.miku.ray.MikuProfiles.impl?.restoreBackup(it) }
            } catch (error: Exception) {
                // Put the pre-restore MMKV stores back so the failure does not
                // leave a half-restored installation behind. The SP-side import
                // may have partially applied — that store has no snapshot — but
                // a restart flag still follows so the service rebuilds from a
                // coherent MMKV state.
                if (snapshotTaken) {
                    runCatching { MMKV.restoreAllFromDirectory(snapshotDir.absolutePath) }
                        .onFailure { LogUtil.e(AppConfig.TAG, "MMKV rollback after failed restore also failed", it) }
                }
                throw error
            }
            SettingsChangeManager.makeSetupGroupTab()
            SettingsChangeManager.makeRestartService()

            restoreBannerImages(context, restoreDir)
            SettingsManager.preloadAllBanners(context)
            restoreCustomFont(context, restoreDir)
            restoreCustomSounds(context, restoreDir)

            applyRestoredUi(context)
            SettingsManager.initApp(context)
            return ImportResult.Success(count.toInt())
        } finally {
            restoreDir.deleteRecursively()
            snapshotDir.deleteRecursively()
        }
    }


    private fun collectFilesAsBase64(dir: File, prefix: String, out: JSONArray) {
        dir.listFiles()?.forEach { file ->
            val relative = if (prefix.isEmpty()) file.name else "$prefix/${file.name}"
            if (file.isFile) {
                if (file.length() > MAX_ENTRY_BYTES) return@forEach
                val bytes = file.readBytes()
                out.put(JSONObject().apply {
                    put("path", relative)
                    put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                })
            } else if (file.isDirectory) {
                collectFilesAsBase64(file, relative, out)
            }
        }
    }


    private fun applyRestoredUi(context: Context) {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                CustomFontManager.getFontFile(context)?.let {
                    CustomFontManager.applyGlobalOverride(context)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to re-apply custom font after restore", e)
        }

        try {
            LauncherAliasSwitcher.applyAliases(
                context,
                LauncherAliasSwitcher.currentIconVariant(),
                LauncherAliasSwitcher.currentNameVariant()
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to apply launcher aliases after restore", e)
        }

        try {
            SettingsChangeManager.notifyUiCustomizationChanged()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to notify UI customization after restore", e)
        }

        try {
            SelectedProfileBannerController.notifyChanged(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to notify selected profile banner after restore", e)
        }
    }

    private fun backupBannerImages(context: Context, backupDir: File) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
            AppConfig.PREF_CUSTOM_THEME_BANNER_URI,
            AppConfig.PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI,
        )
        val bannersDir = File(backupDir, "banners").also { it.mkdirs() }
        for (key in bannerKeys) {
            val uriString = MmkvManager.decodeSettingsString(key) ?: continue
            if (uriString.isBlank()) continue
            try {
                val uri = Uri.parse(uriString)
                val srcFile = if (uri.scheme == "file") {
                    File(uri.path!!)
                } else {
                    val tmp = File(context.cacheDir, "banner_backup_tmp_$key.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp
                }
                if (srcFile.exists()) {
                    srcFile.copyTo(File(bannersDir, "$key.jpg"), overwrite = true)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to backup banner for key $key", e)
            }
        }
    }

    private fun backupCustomSounds(context: Context, backupDir: File) {
        val soundsDir = File(backupDir, "sounds").also { it.mkdirs() }
        val soundKeys = listOf(
            AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI,
            AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI
        )
        for (key in soundKeys) {
            val uriString = MmkvManager.decodeSettingsString(key).orEmpty()
            if (uriString.isBlank()) continue
            try {
                val uri = Uri.parse(uriString)
                val srcFile = if (uri.scheme == "file") {
                    File(uri.path.orEmpty())
                } else {
                    val tmp = File(context.cacheDir, "sound_backup_tmp_$key")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp
                }
                if (srcFile.exists()) {
                    srcFile.copyTo(File(soundsDir, "$key.bin"), overwrite = true)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to backup sound for key $key", e)
            }
        }
    }

    private fun backupCustomFont(context: Context, backupDir: File) {
        val srcFile = CustomFontManager.getFontFile(context) ?: return
        try {
            val fontsDir = File(backupDir, "fonts").also { it.mkdirs() }
            srcFile.copyTo(File(fontsDir, srcFile.name), overwrite = true)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to backup custom font", e)
        }
    }

    private fun restoreBannerImages(context: Context, backupDir: File) {
        val bannerKeys = listOf(
            AppConfig.PREF_CUSTOM_HOME_BANNER_URI,
            AppConfig.PREF_CUSTOM_SHEET_BANNER_URI,
            AppConfig.PREF_PROFILE_BANNER_URI,
            AppConfig.PREF_SELECTED_BANNER_URI,
            AppConfig.PREF_CUSTOM_THEME_BANNER_URI,
            AppConfig.PREF_CUSTOM_BANNER_SETTINGS_CHARACTER_URI,
        )
        val bannersDir = File(backupDir, "banners")
        if (!bannersDir.exists()) return

        for (key in bannerKeys) {
            val srcFile = File(bannersDir, "$key.jpg")
            if (!srcFile.exists()) {
                MmkvManager.encodeSettings(key, "")
                continue
            }
            try {
                val bannersOutDir = File(context.filesDir, "banners").apply { mkdirs() }
                val destFile = File(bannersOutDir, "${key}_${System.currentTimeMillis()}.jpg")
                srcFile.copyTo(destFile, overwrite = true)
                MmkvManager.encodeSettings(key, Uri.fromFile(destFile).toString())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to restore banner for key $key", e)
                MmkvManager.encodeSettings(key, "")
            }
        }
    }

    private fun restoreCustomFont(context: Context, backupDir: File) {
        val fontsDir = File(backupDir, "fonts")
        val srcFile = fontsDir.takeIf { it.exists() }?.listFiles()?.firstOrNull { it.isFile }

        if (srcFile == null) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
            return
        }

        val existingDisplayName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME)
        val restored = CustomFontManager.restoreFontFile(context, srcFile, existingDisplayName ?: srcFile.name)
        if (restored == null) {
            LogUtil.e(AppConfig.TAG, "Restored custom font file was invalid, falling back to default")
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
            }
        }
    }

    private fun restoreCustomSounds(context: Context, backupDir: File) {
        val soundsDir = File(backupDir, "sounds")
        val soundsOutDir = File(context.filesDir, "sounds").apply { mkdirs() }
        val soundKeys = listOf(
            AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI,
            AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI
        )
        for (key in soundKeys) {
            val srcFile = File(soundsDir, "$key.bin")
            val oldUri = MmkvManager.decodeSettingsString(key)
            if (!srcFile.exists()) {
                if (!oldUri.isNullOrBlank()) {
                    try {
                        File(Uri.parse(oldUri).path.orEmpty()).delete()
                    } catch (_: Exception) {
                    }
                }
                MmkvManager.encodeSettings(key, "")
                continue
            }
            try {
                val destFile = File(soundsOutDir, "${key}_${System.currentTimeMillis()}.bin")
                srcFile.copyTo(destFile, overwrite = true)
                MmkvManager.encodeSettings(key, Uri.fromFile(destFile).toString())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to restore sound for key $key", e)
                MmkvManager.encodeSettings(key, "")
            }
        }
    }
}
