package com.miku.ray.particlesdrawable.engine;

import android.content.res.Resources;
import android.util.TypedValue;

import com.miku.ray.particlesdrawable.contract.SceneConfiguration;
import com.miku.ray.particlesdrawable.model.Scene;

import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

final class ParticleGenerator {

    private final float pcc = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 18f, Resources.getSystem().getDisplayMetrics());

    @NonNull
    private final Random random;

    ParticleGenerator() {
        this(new Random());
    }

    @VisibleForTesting
    ParticleGenerator(@NonNull final Random random) {
        this.random = random;
    }

    void applyFreshParticleOnScreen(
        @NonNull final Scene scene,
        final int position
    ) {
        final int w = scene.getWidth();
        final int h = scene.getHeight();
        if (w == 0 || h == 0) {
            throw new IllegalStateException(
                "Cannot generate particles if scene width or height is 0");
        }

        final double direction = Math.toRadians(random.nextInt(360));
        final float dCos = (float) Math.cos(direction);
        final float dSin = (float) Math.sin(direction);
        final float x = random.nextInt(w);
        final float y = random.nextInt(h);
        final float speedFactor = newRandomIndividualParticleSpeedFactor();
        final float radius = newRandomIndividualParticleRadius(scene);

        scene.setParticleData(
            position,
            x,
            y,
            dCos,
            dSin,
            radius,
            speedFactor);
    }

    void applyFreshParticleOffScreen(
        @NonNull final Scene scene,
        final int position) {
        final int w = scene.getWidth();
        final int h = scene.getHeight();
        if (w == 0 || h == 0) {
            throw new IllegalStateException(
                "Cannot generate particles if scene width or height is 0");
        }

        float x = random.nextInt(w);
        float y = random.nextInt(h);

        final short offset = (short) (scene.getParticleRadiusMin() + scene.getLineLength());

        final float startAngle;
        float endAngle;

        switch (random.nextInt(4)) {
            case 0:
            x = (short) -offset;
            startAngle = angleDeg(pcc, pcc, x, y);
            endAngle = angleDeg(pcc, h - pcc, x, y);
            break;

            case 1:
            y = (short) -offset;
            startAngle = angleDeg(w - pcc, pcc, x, y);
            endAngle = angleDeg(pcc, pcc, x, y);
            break;

            case 2:
            x = (short) (w + offset);
            startAngle = angleDeg(w - pcc, h - pcc, x, y);
            endAngle = angleDeg(w - pcc, pcc, x, y);
            break;

            case 3:
            y = (short) (h + offset);
            startAngle = angleDeg(pcc, h - pcc, x, y);
            endAngle = angleDeg(w - pcc, h - pcc, x, y);
            break;

            default:
            throw new IllegalArgumentException("Supplied value out of range");
        }

        if (endAngle < startAngle) {
            endAngle += 360;
        }

        final float randomAngleInRange = startAngle + (random
            .nextInt((int) Math.abs(endAngle - startAngle)));
        final double direction = Math.toRadians(randomAngleInRange);

        final float dCos = (float) Math.cos(direction);
        final float dSin = (float) Math.sin(direction);
        final float speedFactor = newRandomIndividualParticleSpeedFactor();
        final float radius = newRandomIndividualParticleRadius(scene);

        scene.setParticleData(
            position,
            x,
            y,
            dCos,
            dSin,
            radius,
            speedFactor);
    }

    private static float angleDeg(final float ax, final float ay,
        final float bx, final float by) {
        final double angleRad = Math.atan2(ay - by, ax - bx);
        double angle = Math.toDegrees(angleRad);
        if (angleRad < 0) {
            angle += 360;
        }
        return (float) angle;
    }

    private float newRandomIndividualParticleSpeedFactor() {
        return 1f + 0.1f * (random.nextInt(11) - 5);
    }

    private float newRandomIndividualParticleRadius(@NonNull final SceneConfiguration scene) {
        return scene.getParticleRadiusMin() == scene.getParticleRadiusMax() ?
        scene.getParticleRadiusMin() : scene.getParticleRadiusMin() + (random.nextInt(
                (int) ((scene.getParticleRadiusMax() - scene.getParticleRadiusMin()) * 100f)))
        / 100f;
    }
}
