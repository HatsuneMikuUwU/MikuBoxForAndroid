package com.miku.ray.particlesdrawable.engine;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.miku.ray.particlesdrawable.Defaults;
import com.miku.ray.R;
import com.miku.ray.particlesdrawable.contract.SceneConfiguration;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public final class SceneConfigurator {

    public void configureSceneFromAttributes(
        @NonNull final SceneConfiguration scene,
        @NonNull final Context context,
        @NonNull final AttributeSet attrs) {

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ParticlesView);

        try {
            final int count = a.getIndexCount();
            float particleRadiusMax = Defaults.PARTICLE_RADIUS_MAX;
            float particleRadiusMin = Defaults.PARTICLE_RADIUS_MIN;
            for (int i = 0; i < count; i++) {
                final int attr = a.getIndex(i);
                if (attr == R.styleable.ParticlesView_density) {
                    scene.setDensity(a.getInteger(attr, Defaults.DENSITY));

                } else if (attr == R.styleable.ParticlesView_frameDelayMillis) {
                    scene.setFrameDelay(a.getInteger(attr, Defaults.FRAME_DELAY));

                } else if (attr == R.styleable.ParticlesView_lineColor) {
                    scene.setLineColor(a.getColor(attr, Defaults.LINE_COLOR));

                } else if (attr == R.styleable.ParticlesView_lineLength) {
                    scene.setLineLength(a.getDimension(attr, Defaults.LINE_LENGTH));

                } else if (attr == R.styleable.ParticlesView_lineThickness) {
                    scene.setLineThickness(a.getDimension(attr, Defaults.LINE_THICKNESS));

                } else if (attr == R.styleable.ParticlesView_particleColor) {
                    scene.setParticleColor(a.getColor(attr, Defaults.PARTICLE_COLOR));

                } else if (attr == R.styleable.ParticlesView_particleRadiusMax) {
                    particleRadiusMax = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MAX);

                } else if (attr == R.styleable.ParticlesView_particleRadiusMin) {
                    particleRadiusMin = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MIN);

                } else if (attr == R.styleable.ParticlesView_speedFactor) {
                    scene.setSpeedFactor(a.getFloat(attr, Defaults.SPEED_FACTOR));
                }
            }
            scene.setParticleRadiusRange(particleRadiusMin, particleRadiusMax);
        } finally {
            a.recycle();
        }
    }
}
