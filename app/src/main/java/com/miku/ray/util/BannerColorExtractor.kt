package com.miku.ray.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import com.google.android.material.color.utilities.Hct
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Preserve MikuRay's exact HCT palette; the Material dependency is pinned in libs.versions.toml.
@android.annotation.SuppressLint("RestrictedApi")
object BannerColorExtractor {

    suspend fun extractAndSave(context: Context, uri: Uri, onDone: (colorChanged: Boolean) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }

                if (bitmap != null) {
                    val palette = Palette.from(bitmap)
                    .maximumColorCount(32)
                    .generate()
                    bitmap.recycle()

                    val bestSwatch = palette.swatches
                    .filter { swatch ->
                        val hsl = swatch.hsl
                        hsl[1] >= 0.2f && hsl[2] in 0.15f..0.85f
                    }
                    .maxByOrNull { it.population }

                    val rawColor = bestSwatch?.rgb
                    ?: palette.getDominantColor(0).takeIf { it != 0 }
                    ?: palette.getVibrantColor(0)

                    val color = if (rawColor != 0) {
                        val hct = Hct.fromInt(rawColor)
                        Hct.from(hct.hue, hct.chroma, 50.0).toInt()
                    } else rawColor

                    if (color != 0) {
                        MmkvManager.encodeSettings(AppConfig.PREF_BANNER_COLOR, color)
                        withContext(Dispatchers.Main) { onDone(true) }
                        return@withContext
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "BannerColorExtractor: failed to extract color", e)
            }
            withContext(Dispatchers.Main) { onDone(false) }
        }
    }
}
