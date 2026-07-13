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

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.FloatRange;
import androidx.annotation.Keep;

/**
 * Particles scene configuration, makes sure all configuration fields are available.
 */
@Keep
public interface ParticlesSceneConfiguration {

    /**
     * Set a delay per frame in milliseconds.
     *
     * @param delay delay between frames
     * @throws IllegalArgumentException if delay is a negative number
     */
    void setFrameDelay(@IntRange(from = 0) int delay);

    /**
     * Returns a delay per frame in milliseconds.
     *
     * @return delay between frames
     */
    int getFrameDelay();

    /**
     * Sets step multiplier. Use this to control speed.
     *
     * @param stepMultiplier step multiplier
     */
    void setStepMultiplier(@FloatRange(from = 0) final float stepMultiplier);

    /**
     * Returns the step multiplier.
     *
     * @return the step multiplier
     */
    float getStepMultiplier();

    /**
     * Set dot radius range
     *
     * @param minRadius smallest dot radius
     * @param maxRadius largest dot radius
     */
    void setDotRadiusRange(
            @FloatRange(from = 0.5f) float minRadius,
            @FloatRange(from = 0.5f) float maxRadius);

    /**
     * Returns smallest particle radius
     *
     * @return smallest particle radius
     */
    float getMinDotRadius();

    /**
     * Largest particle radius
     *
     * @return largest particle radius
     */
    float getMaxDotRadius();

    /**
     * Set a line thickness
     *
     * @param lineThickness line thickness
     */
    void setLineThickness(@FloatRange(from = 1) float lineThickness);

    /**
     * Returns the connection like thickness
     *
     * @return the connection line thickness
     */
    float getLineThickness();

    /**
     * Set the maximum distance when the connection line is still drawn between points
     *
     * @param lineDistance maximum distance for connection lines
     */
    void setLineDistance(@FloatRange(from = 0) float lineDistance);

    /**
     * Returns the maximum distance when the connection line is still drawn between points
     *
     * @return maximum distance for connection lines
     */
    float getLineDistance();

    /**
     * Set number of points to draw
     *
     * @param newNum the number of points
     * @throws IllegalArgumentException if number of points is negative
     */
    void setNumDots(@IntRange(from = 0) int newNum);

    /**
     * Returns the number of particles in the scene
     *
     * @return the number of particles in the scene
     */
    int getNumDots();

    /**
     * Set the dot color
     *
     * @param dotColor dot color to use
     */
    void setDotColor(@ColorInt int dotColor);

    /**
     * Returns the particles color
     *
     * @return the particles color
     */
    @ColorInt int getDotColor();

    /**
     * Set the line color. Note that the color alpha is ignored and will be calculated depending on
     * distance between points
     *
     * @param lineColor line color to use
     */
    void setLineColor(@ColorInt int lineColor);

    /**
     * Returns the connection line color
     *
     * @return the connection line color
     */
    @ColorInt int getLineColor();
}
