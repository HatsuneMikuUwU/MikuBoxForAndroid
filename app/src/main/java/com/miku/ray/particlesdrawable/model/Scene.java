package com.miku.ray.particlesdrawable.model;

import com.miku.ray.particlesdrawable.Defaults;
import com.miku.ray.particlesdrawable.KeepAsApi;
import com.miku.ray.particlesdrawable.contract.SceneConfiguration;

import java.nio.FloatBuffer;
import java.util.Locale;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

@KeepAsApi
public final class Scene implements SceneConfiguration {

    private static final int COORDINATES_PER_VERTEX = 2;

    private int alpha = 255;

    private int density = Defaults.DENSITY;

    private int frameDelay = Defaults.FRAME_DELAY;

    @ColorInt
    private int lineColor = Defaults.LINE_COLOR;

    private float lineLength = Defaults.LINE_LENGTH;

    private float lineThickness = Defaults.LINE_THICKNESS;

    @ColorInt
    private int particleColor = Defaults.PARTICLE_COLOR;

    private float particleRadiusMax = Defaults.PARTICLE_RADIUS_MAX;

    private float particleRadiusMin = Defaults.PARTICLE_RADIUS_MIN;

    private float speedFactor = Defaults.SPEED_FACTOR;

    private int width;
    private int height;

    private FloatBuffer coordinates;

    private FloatBuffer directions;
    private FloatBuffer radiuses;
    private FloatBuffer speedFactors;

    public Scene() {
        initBuffers(density);
    }

    @NonNull
    public FloatBuffer getCoordinates() {
        return coordinates;
    }

    @NonNull
    public FloatBuffer getRadiuses() {
        return radiuses;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public void setHeight(final int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setParticleData(
        final int position,
        final float x,
        final float y,
        final float dCos,
        final float dSin,
        final float radius,
        final float speedFactor) {
        setParticleX(position, x);
        setParticleY(position, y);

        setParticleDirectionCos(position, dCos);
        setParticleDirectionSin(position, dSin);

        radiuses.put(position, radius);
        speedFactors.put(position, speedFactor);
    }

    public float getParticleX(final int position) {
        return coordinates.get(position * 2);
    }

    public float getParticleY(final int position) {
        return coordinates.get(position * 2 + 1);
    }

    public float getParticleDirectionCos(final int position) {
        return directions.get(position * 2);
    }

    public float getParticleDirectionSin(final int position) {
        return directions.get(position * 2 + 1);
    }

    public float getParticleSpeedFactor(final int position) {
        return speedFactors.get(position);
    }

    public void setParticleX(final int position, final float x) {
        coordinates.put(position * 2, x);
    }

    public void setParticleY(final int position, final float y) {
        coordinates.put(position * 2 + 1, y);
    }

    private void setParticleDirectionCos(final int position, final float direction) {
        directions.put(position * 2, direction);
    }

    private void setParticleDirectionSin(final int position, final float direction) {
        directions.put(position * 2 + 1, direction);
    }

    public void setAlpha(final int alpha) {
        this.alpha = alpha;
    }

    @IntRange(from = 0, to = 255)
    public int getAlpha() {
        return alpha;
    }

    private void initBuffers(final int density) {
        initCoordinates(density);
        initDirections(density);
        initSpeedFactors(density);
        initRadiuses(density);
    }

    private void initCoordinates(final int density) {
        final int capacity = density * COORDINATES_PER_VERTEX;
        if (coordinates == null || coordinates.capacity() != capacity) {
            coordinates = FloatBuffer.allocate(capacity);
        }
    }

    private void initDirections(final int density) {
        final int capacity = density * 2;
        if (directions == null || directions.capacity() != capacity) {
            directions = FloatBuffer.allocate(capacity);
        }
    }

    private void initSpeedFactors(final int density) {
        if (speedFactors == null || speedFactors.capacity() != density) {
            speedFactors = FloatBuffer.allocate(density);
        }
    }

    private void initRadiuses(final int density) {
        if (radiuses == null || radiuses.capacity() != density) {
            radiuses = FloatBuffer.allocate(density);
        }
    }

    @Override
    public int getDensity() {
        return density;
    }

    @Override
    public void setDensity(@IntRange(from = 0) final int density) {
        if (density < 0) {
            throw new IllegalArgumentException("Density must not be negative");
        }
        if (this.density != density) {
            this.density = density;
            initBuffers(density);
        }
    }

    @Override
    public int getFrameDelay() {
        return frameDelay;
    }

    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be nagative");
        }
        frameDelay = delay;
    }

    @Override
    public int getLineColor() {
        return lineColor;
    }

    @Override
    public void setLineColor(@ColorInt final int lineColor) {
        this.lineColor = lineColor;
    }

    @Override
    public float getLineLength() {
        return lineLength;
    }

    @Override
    public float getLineThickness() {
        return lineThickness;
    }

    @Override
    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        if (lineThickness < 1) {
            throw new IllegalArgumentException("Line thickness must not be less than 1");
        }
        if (Float.compare(lineThickness, Float.NaN) == 0) {
            throw new IllegalArgumentException("line thickness must be a valid float");
        }
        this.lineThickness = lineThickness;
    }

    @Override
    public void setLineLength(@FloatRange(from = 0) final float lineLength) {
        if (lineLength < 0) {
            throw new IllegalArgumentException("line length must not be negative");
        }
        if (Float.compare(lineLength, Float.NaN) == 0) {
            throw new IllegalArgumentException("line length must be a valid float");
        }
        this.lineLength = lineLength;
    }

    @Override
    public int getParticleColor() {
        return particleColor;
    }

    @Override
    public void setParticleColor(@ColorInt final int color) {
        particleColor = color;
    }

    @Override
    public float getParticleRadiusMin() {
        return particleRadiusMin;
    }

    @Override
    public float getParticleRadiusMax() {
        return particleRadiusMax;
    }

    @Override
    public void setParticleRadiusRange(
        @FloatRange(from = 0.5f) final float minRadius,
        @FloatRange(from = 0.5f) final float maxRadius) {
        if (minRadius < 0.5f || maxRadius < 0.5f) {
            throw new IllegalArgumentException("Particle radius must not be less than 0.5");
        }
        if (Float.compare(minRadius, Float.NaN) == 0
            || Float.compare(maxRadius, Float.NaN) == 0) {
            throw new IllegalArgumentException("Particle radius must be a valid float");
        }
        if (minRadius > maxRadius) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Min radius must not be greater than max, but min = %f, max = %f",
                    minRadius, maxRadius));
        }
        particleRadiusMin = minRadius;
        particleRadiusMax = maxRadius;
    }

    @Override
    public float getSpeedFactor() {
        return speedFactor;
    }

    @Override
    public void setSpeedFactor(@FloatRange(from = 0) final float speedFactor) {
        if (speedFactor < 0) {
            throw new IllegalArgumentException("speedFactor must not be nagative");
        }
        if (Float.compare(speedFactor, Float.NaN) == 0) {
            throw new IllegalArgumentException("speedFactor must be a valid float");
        }
        this.speedFactor = speedFactor;
    }
}
