package com.miku.ray.util

import android.content.Context
import android.graphics.Typeface

object AppFontResolver {

    private val assetPaths = mapOf(
        "ios15" to "fonts/ios15.ttf",
        "google" to "fonts/googlesansregular.ttf",
        "roboto" to "fonts/robotoregular.ttf",
        "poppins" to "fonts/poppinsregular.ttf",
        "chococooky" to "fonts/chococookyregular.ttf",
        "simpleday" to "fonts/simpleday.ttf",
        "fucek" to "fonts/fucek.ttf",
        "sfprodisplay" to "fonts/sfprodisplay.ttf",
        "dancingscript" to "fonts/dancingscript.ttf",
        "cream" to "fonts/cream.ttf",
        "oneui" to "fonts/oneui.ttf",
        "inconsolata" to "fonts/incosolata.ttf",
        "emilyscandy" to "fonts/emilyscandy.ttf",
        "summerdream" to "fonts/summerdream.ttf",
        "rine" to "fonts/rine.ttf",
        "evolve" to "fonts/evolvesans.ttf"
    )

    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun getTypeface(context: Context, value: String?): Typeface? {
        val assetPath = assetPaths[value] ?: return null

        synchronized(typefaceCache) {
            typefaceCache[assetPath]?.let { return it }
        }

        return try {
            Typeface.createFromAsset(context.assets, assetPath).also { typeface ->
                synchronized(typefaceCache) {
                    typefaceCache[assetPath] = typeface
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
