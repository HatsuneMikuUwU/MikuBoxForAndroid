package com.neko.widget

import androidx.annotation.RawRes
import io.nekohasekai.sagernet.R

/**
 * Resolves MikuRay's 35 Material Expressive SVG shapes plus legacy MikuBox shape keys
 * so existing user preferences migrate cleanly without crashing.
 */
object MikuShapeResolver {

    @RawRes
    fun resolve(shapeKey: String?): Int = when (shapeKey) {
        "uwu_shape_arch" -> R.raw.uwu_shape_arch
        "uwu_shape_arrow" -> R.raw.uwu_shape_arrow
        "uwu_shape_boom" -> R.raw.uwu_shape_boom
        "uwu_shape_bun" -> R.raw.uwu_shape_bun
        "uwu_shape_burst" -> R.raw.uwu_shape_burst
        "uwu_shape_circle" -> R.raw.uwu_shape_circle
        "uwu_shape_clover_4", "uwu_shape_clover" -> R.raw.uwu_shape_clover_4
        "uwu_shape_clover_8" -> R.raw.uwu_shape_clover_8
        "uwu_shape_cookie_4" -> R.raw.uwu_shape_cookie_4
        "uwu_shape_cookie_6" -> R.raw.uwu_shape_cookie_6
        "uwu_shape_cookie_7" -> R.raw.uwu_shape_cookie_7
        "uwu_shape_cookie_9", "uwu_shape_cookie" -> R.raw.uwu_shape_cookie_9
        "uwu_shape_cookie_12" -> R.raw.uwu_shape_cookie_12
        "uwu_shape_diamond" -> R.raw.uwu_shape_diamond
        "uwu_shape_fan" -> R.raw.uwu_shape_fan
        "uwu_shape_flower" -> R.raw.uwu_shape_flower
        "uwu_shape_gem" -> R.raw.uwu_shape_gem
        "uwu_shape_ghostish" -> R.raw.uwu_shape_ghostish
        "uwu_shape_heart" -> R.raw.uwu_shape_heart
        "uwu_shape_hexagon" -> R.raw.uwu_shape_hexagon
        "uwu_shape_oval" -> R.raw.uwu_shape_oval
        "uwu_shape_pentagon" -> R.raw.uwu_shape_pentagon
        "uwu_shape_pill" -> R.raw.uwu_shape_pill
        "uwu_shape_pixel_circle" -> R.raw.uwu_shape_pixel_circle
        "uwu_shape_pixel_triangle" -> R.raw.uwu_shape_pixel_triangle
        "uwu_shape_puffy" -> R.raw.uwu_shape_puffy
        "uwu_shape_puffy_diamond" -> R.raw.uwu_shape_puffy_diamond
        "uwu_shape_semicircle" -> R.raw.uwu_shape_semicircle
        "uwu_shape_slanted_square" -> R.raw.uwu_shape_slanted_square
        "uwu_shape_soft_boom" -> R.raw.uwu_shape_soft_boom
        "uwu_shape_soft_burst" -> R.raw.uwu_shape_soft_burst
        "uwu_shape_square", "uwu_shape_rounded_square", "uwu_shape_squircle" -> R.raw.uwu_shape_square
        "uwu_shape_sunny" -> R.raw.uwu_shape_sunny
        "uwu_shape_triangle" -> R.raw.uwu_shape_triangle
        "uwu_shape_very_sunny" -> R.raw.uwu_shape_very_sunny
        "uwu_shape_octagon" -> R.raw.uwu_shape_octagon
        else -> R.raw.uwu_shape_cookie_9
    }
}
