package com.miku.ray.particlesdrawable.contract;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;

@Keep
public interface SceneConfiguration {

    void setDensity(@IntRange(from = 0) int density);

    int getDensity();

    void setFrameDelay(@IntRange(from = 0) int delay);

    int getFrameDelay();

    void setLineColor(@ColorInt int lineColor);

    @ColorInt
    int getLineColor();

    void setLineLength(@FloatRange(from = 0) float lineLength);

    float getLineLength();

    void setLineThickness(@FloatRange(from = 1) float lineThickness);

    float getLineThickness();

    void setParticleColor(@ColorInt int color);

    @ColorInt
    int getParticleColor();

    void setParticleRadiusRange(
        @FloatRange(from = 0.5f) float minRadius,
        @FloatRange(from = 0.5f) float maxRadius);

    float getParticleRadiusMax();

    float getParticleRadiusMin();

    void setSpeedFactor(@FloatRange(from = 0) final float speedFactor);

    float getSpeedFactor();
}
