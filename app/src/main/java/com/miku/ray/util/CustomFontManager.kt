package com.miku.ray.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import java.io.File

object CustomFontManager {

    private const val TAG = "CustomFontManager"
    private const val FONT_DIR = "custom_font"
    private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf", "ttc")

    @Volatile
    private var cachedTypeface: Typeface? = null

    @Volatile
    private var cachedPath: String? = null

    private var originalDefault: Typeface? = null
    private var originalDefaultBold: Typeface? = null
    private var originalSansSerif: Typeface? = null
    private var originalSystemFontMap: Map<String, Typeface>? = null
    private var isOriginalCached = false

    private fun cacheOriginalFonts() {
        if (isOriginalCached) return
        isOriginalCached = true
        originalDefault = Typeface.DEFAULT
        originalDefaultBold = Typeface.DEFAULT_BOLD
        originalSansSerif = Typeface.SANS_SERIF
        try {
            val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as? Map<String, Typeface>
            if (map != null) {
                originalSystemFontMap = HashMap(map)
            }
        } catch (e: Exception) {
        }
    }

    fun saveFontFile(context: Context, uri: Uri, displayName: String?): File? {
        return try {
            val ext = displayName?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it in SUPPORTED_EXTENSIONS } ?: "ttf"

            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }

            val destFile = File(dir, "font.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val typeface = try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                null
            }
            if (typeface == null || typeface == Typeface.DEFAULT) {
                destFile.delete()
                return null
            }

            MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, displayName ?: destFile.name)

            cachedTypeface = typeface
            cachedPath = destFile.absolutePath
            destFile
        } catch (e: Exception) {
            null
        }
    }

    fun restoreFontFile(context: Context, srcFile: File, displayName: String?): File? {
        return try {
            val ext = srcFile.extension.lowercase().takeIf { it in SUPPORTED_EXTENSIONS } ?: "ttf"

            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }

            val destFile = File(dir, "font.$ext")
            srcFile.copyTo(destFile, overwrite = true)

            val typeface = try {
                Typeface.createFromFile(destFile)
            } catch (e: Exception) {
                null
            }
            if (typeface == null || typeface == Typeface.DEFAULT) {
                destFile.delete()
                return null
            }

            MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, displayName ?: destFile.name)

            cachedTypeface = typeface
            cachedPath = destFile.absolutePath
            destFile
        } catch (e: Exception) {
            null
        }
    }

    fun getFontFile(context: Context): File? {
        val dir = File(context.filesDir, FONT_DIR)
        val file = dir.listFiles()?.firstOrNull { it.isFile }
        return file?.takeIf { it.exists() }
    }

    fun clearFont(context: Context) {
        File(context.filesDir, FONT_DIR).listFiles()?.forEach { it.delete() }
        MmkvManager.encodeSettings(AppConfig.PREF_APP_FONT_CUSTOM_NAME, "")
        cachedTypeface = null
        cachedPath = null
        restoreGlobalOverride()
    }

    fun getFontDisplayName(): String? =
    MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT_CUSTOM_NAME)?.takeIf { it.isNotEmpty() }

    fun getTypeface(context: Context): Typeface? {
        val file = getFontFile(context) ?: return null

        cachedTypeface?.let { if (cachedPath == file.absolutePath) return it }

        return try {
            val typeface = Typeface.createFromFile(file)
            cachedTypeface = typeface
            cachedPath = file.absolutePath
            typeface
        } catch (e: Exception) {
            null
        }
    }

    fun applyGlobalOverride(context: Context) {
        getTypeface(context)?.let { applyGlobalOverride(it) }
    }

    fun applyGlobalOverride(typeface: Typeface) {
        cacheOriginalFonts()
        replaceStaticField("DEFAULT", typeface)
        replaceStaticField(
            "DEFAULT_BOLD",
            GoogleSansFlexManager.getBoldTypeface() ?: Typeface.create(typeface, Typeface.BOLD)
        )
        replaceStaticField("SANS_SERIF", typeface)
        replaceSystemFontMapEntries(typeface)
    }

    fun applyToViewTree(context: Context, root: View) {
        getTypeface(context)?.let { applyToViewTree(it, root) }
    }

    fun applyToViewTree(typeface: Typeface, root: View) {
        fun apply(view: View) {
            if (view is TextView) {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = Typeface.create(typeface, style)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    apply(view.getChildAt(index))
                }
            }
        }

        apply(root)
    }

    fun restoreGlobalOverride() {
        if (!isOriginalCached) return
        originalDefault?.let { replaceStaticField("DEFAULT", it) }
        originalDefaultBold?.let { replaceStaticField("DEFAULT_BOLD", it) }
        originalSansSerif?.let { replaceStaticField("SANS_SERIF", it) }
        originalSystemFontMap?.let { originalMap ->
            try {
                val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
                field.isAccessible = true
                field.set(null, originalMap)
            } catch (e: Exception) {
            }
        }
    }

    private fun replaceStaticField(fieldName: String, typeface: Typeface) {
        try {
            val field = Typeface::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, typeface)
        } catch (e: Exception) {
        }
    }

    private fun replaceSystemFontMapEntries(typeface: Typeface) {
        try {
            val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(null) as? Map<String, Typeface> ?: return
            val mutableMap = HashMap(map)
            listOf(
                "sans-serif", "sans-serif-medium", "sans-serif-light",
                "sans-serif-thin", "sans-serif-black", "sans-serif-condensed",
                "normal", "default"
            ).forEach { mutableMap[it] = typeface }
            field.set(null, mutableMap)
        } catch (e: Exception) {
        }
    }
}
