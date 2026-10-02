package com.miku.ray.util

import com.miku.ray.particlesdrawable.ParticlesView
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager

object ParticlesController {

    fun applyTo(view: ParticlesView) {
        val density = view.resources.displayMetrics.density

        val frameDelay = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_FRAME_DELAY,
            AppConfig.PARTICLES_FRAME_DELAY_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_FRAME_DELAY_MIN, AppConfig.PARTICLES_FRAME_DELAY_MAX).toInt()

        val lineLengthDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_LINE_LENGTH,
            AppConfig.PARTICLES_LINE_LENGTH_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_LINE_LENGTH_MIN, AppConfig.PARTICLES_LINE_LENGTH_MAX)

        val lineThicknessDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_LINE_THICKNESS,
            AppConfig.PARTICLES_LINE_THICKNESS_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_LINE_THICKNESS_MIN, AppConfig.PARTICLES_LINE_THICKNESS_MAX)

        val radiusMaxDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_RADIUS_MAX,
            AppConfig.PARTICLES_RADIUS_MAX_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_RADIUS_MAX_MIN, AppConfig.PARTICLES_RADIUS_MAX_MAX)

        val radiusMinDp = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_RADIUS_MIN,
            AppConfig.PARTICLES_RADIUS_MIN_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_RADIUS_MIN_MIN, AppConfig.PARTICLES_RADIUS_MIN_MAX)

        val density_ = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_DENSITY,
            AppConfig.PARTICLES_DENSITY_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_DENSITY_MIN, AppConfig.PARTICLES_DENSITY_MAX).toInt()

        val speedFactor = MmkvManager.decodeSettingsFloat(
            AppConfig.PREF_PARTICLES_SPEED_FACTOR,
            AppConfig.PARTICLES_SPEED_FACTOR_DEFAULT
        ).coerceIn(AppConfig.PARTICLES_SPEED_FACTOR_MIN, AppConfig.PARTICLES_SPEED_FACTOR_MAX)

        val minRadiusPx = minOf(radiusMinDp, radiusMaxDp) * density
        val maxRadiusPx = maxOf(radiusMinDp, radiusMaxDp) * density

        view.setFrameDelay(frameDelay)
        view.setLineLength(lineLengthDp * density)
        view.setLineThickness(lineThicknessDp * density)
        view.setParticleRadiusRange(minRadiusPx.coerceAtLeast(0.5f), maxRadiusPx.coerceAtLeast(0.5f))
        view.setDensity(density_)
        view.setSpeedFactor(speedFactor)
        view.makeFreshFrame()
    }
}
