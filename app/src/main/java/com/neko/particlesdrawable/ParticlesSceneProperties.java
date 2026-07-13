/*
 * Copyright (C) 2017 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neko.particlesdrawable;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Particles scene configuration
 */
final class ParticlesSceneProperties implements ParticlesSceneConfiguration {

    private final List<Particle> mPoints = new ArrayList<Particle>(
            Defaults.DEFAULT_DOT_NUMBER);

    private float mMinDotRadius = Defaults.DEFAULT_MIN_DOT_RADIUS;
    private float mMaxDotRadius = Defaults.DEFAULT_MAX_DOT_RADIUS;

    private float mLineThickness = Defaults.DEFAULT_LINE_THICKNESS;

    private float mLineDistance = Defaults.DEFAULT_LINE_DISTANCE;

    private int mNumDots = Defaults.DEFAULT_DOT_NUMBER;

    @ColorInt
    private int mDotColor = Defaults.DEFAULT_DOT_COLOR;

    @ColorInt
    private int mLineColor = Defaults.DEFAULT_LINE_COLOR;

    private int mDelay = Defaults.DEFAULT_DELAY;

    private float mStepMultiplier = Defaults.DEFAULT_STEP_MULTIPLIER;

    // The alpha value of the Drawable
    private int mAlpha = 255;

    @ColorInt
    private int mDotColorResolvedAlpha = resolveDotColorWithDrawableAlpha(mDotColor, mAlpha);

    private int mWidth;
    private int mHeight;

    List<Particle> getMutablePoints() {
        return mPoints;
    }

    void setWidth(final int width) {
        mWidth = width;
    }

    void setHeight(final int height) {
        mHeight = height;
    }

    int getWidth() {
        return mWidth;
    }

    int getHeight() {
        return mHeight;
    }

    void addPoint(@NonNull final Particle p) {
        mPoints.add(p);
    }

    void removeFirstPoint() {
        if (!mPoints.isEmpty()) {
            mPoints.remove(0);
        }
    }

    void clearPoints() {
        mPoints.clear();
    }

    void setAlpha(final int alpha) {
        mAlpha = alpha;
        mDotColorResolvedAlpha = resolveDotColorWithDrawableAlpha(mDotColor, alpha);
    }

    @VisibleForTesting
    static int resolveDotColorWithDrawableAlpha(
            @ColorInt final int dotColor,
            final int drawableAlpha) {
        final int alpha = Color.alpha(dotColor) * drawableAlpha / 255;
        return (dotColor & 0x00FFFFFF) | (alpha << 24);
    }

    int getAlpha() {
        return mAlpha;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be nagative");
        }
        mDelay = delay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFrameDelay() {
        return mDelay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStepMultiplier(@FloatRange(from = 0) final float stepMultiplier) {
        if (stepMultiplier < 0) {
            throw new IllegalArgumentException("step multiplier must not be nagative");
        }
        if (Float.compare(stepMultiplier, Float.NaN) == 0) {
            throw new IllegalArgumentException("step multiplier must be a valid float");
        }
        mStepMultiplier = stepMultiplier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getStepMultiplier() {
        return mStepMultiplier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDotRadiusRange(
            @FloatRange(from = 0.5f) final float minRadius,
            @FloatRange(from = 0.5f) final float maxRadius) {
        if (minRadius < 0.5f || maxRadius < 0.5f) {
            throw new IllegalArgumentException("Dot radius must not be less than 0.5");
        }
        if (Float.compare(minRadius, Float.NaN) == 0
                || Float.compare(maxRadius, Float.NaN) == 0) {
            throw new IllegalArgumentException("Dot radius must be a valid float");
        }
        if (minRadius > maxRadius) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Min radius must not be greater than max, but min = %f, max = %f",
                    minRadius, maxRadius));
        }
        mMinDotRadius = minRadius;
        mMaxDotRadius = maxRadius;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMinDotRadius() {
        return mMinDotRadius;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMaxDotRadius() {
        return mMaxDotRadius;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        if (lineThickness < 1) {
            throw new IllegalArgumentException("Line thickness must not be less than 1");
        }
        if (Float.compare(lineThickness, Float.NaN) == 0) {
            throw new IllegalArgumentException("line thickness must be a valid float");
        }
        mLineThickness = lineThickness;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineThickness() {
        return mLineThickness;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineDistance(@FloatRange(from = 0) final float lineDistance) {
        if (lineDistance < 0) {
            throw new IllegalArgumentException("line distance must not be negative");
        }
        if (Float.compare(lineDistance, Float.NaN) == 0) {
            throw new IllegalArgumentException("line distance must be a valid float");
        }
        mLineDistance = lineDistance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineDistance() {
        return mLineDistance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumDots(@IntRange(from = 0) final int newNum) {
        if (newNum < 0) {
            throw new IllegalArgumentException("numPoints must not be negative");
        }
        mNumDots = newNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDots() {
        return mNumDots;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDotColor(@ColorInt final int dotColor) {
        mDotColor = dotColor;
        mDotColorResolvedAlpha = resolveDotColorWithDrawableAlpha(dotColor, mAlpha);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDotColor() {
        return mDotColor;
    }

    int getDotColorResolvedAlpha() {
        return mDotColorResolvedAlpha;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineColor(@ColorInt final int lineColor) {
        mLineColor = lineColor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLineColor() {
        return mLineColor;
    }
}
