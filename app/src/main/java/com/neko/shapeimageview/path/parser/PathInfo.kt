package com.neko.shapeimageview.path.parser

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

class PathInfo(
    private val path: Path,
    width: Float,
    height: Float
) {
    val width: Float
    val height: Float

    init {
        val bounds = RectF()
        path.computeBounds(bounds, true)

        if (width <= 0 && height <= 0) {
            this.width = ceil(bounds.width())
            this.height = ceil(bounds.height())

            path.offset(
                -1f * floor(bounds.left),
                -1f * round(bounds.top)
            )
        } else {
            this.width = width
            this.height = height
        }
    }

    fun transform(matrix: Matrix, dst: Path) {
        path.transform(matrix, dst)
    }
}
