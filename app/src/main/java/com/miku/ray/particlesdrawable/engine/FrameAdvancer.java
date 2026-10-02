package com.miku.ray.particlesdrawable.engine;

import com.miku.ray.particlesdrawable.model.Scene;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

final class FrameAdvancer {

    @NonNull
    private final ParticleGenerator particleGenerator;

    FrameAdvancer(@NonNull final ParticleGenerator particleGenerator) {
        this.particleGenerator = particleGenerator;
    }

    void advanceToNextFrame(
        @NonNull final Scene scene,
        final float step
    ) {
        final int particlesCount = scene.getDensity();
        for (int i = 0; i < particlesCount; i++) {
            float x = scene.getParticleX(i);
            float y = scene.getParticleY(i);

            final float speedFactor = scene.getParticleSpeedFactor(i);
            final float dCos = scene.getParticleDirectionCos(i);
            final float dSin = scene.getParticleDirectionSin(i);

            x += step * scene.getSpeedFactor() * speedFactor * dCos;
            y += step * scene.getSpeedFactor() * speedFactor * dSin;

            if (particleOutOfBounds(scene, x, y)) {
                particleGenerator.applyFreshParticleOffScreen(scene, i);
            } else {
                scene.setParticleX(i, x);
                scene.setParticleY(i, y);
            }
        }
    }

    @VisibleForTesting
    boolean particleOutOfBounds(
        @NonNull final Scene scene,
        final float x,
        final float y) {
        final float offset = scene.getParticleRadiusMin() + scene.getLineLength();
        return x + offset < 0 || x - offset > scene.getWidth()
        || y + offset < 0 || y - offset > scene.getHeight();
    }
}
