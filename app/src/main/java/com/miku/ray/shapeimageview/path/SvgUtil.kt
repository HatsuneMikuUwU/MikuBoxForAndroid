package com.miku.ray.shapeimageview.path

import android.content.Context
import com.miku.ray.shapeimageview.path.parser.PathInfo
import com.miku.ray.shapeimageview.path.parser.SvgToPath
import java.util.concurrent.ConcurrentHashMap

object SvgUtil {
    private val PATH_MAP = ConcurrentHashMap<Int, PathInfo>()

    fun readSvg(context: Context, resId: Int): PathInfo {
        return PATH_MAP.getOrPut(resId) {
            context.resources.openRawResource(resId).use { inputStream ->
                SvgToPath.getSVGFromInputStream(inputStream)
            }
        }
    }
}
