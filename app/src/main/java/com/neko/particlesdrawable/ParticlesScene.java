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

import androidx.annotation.Keep;

/**
 * The Particles scene
 */
@Keep
public interface ParticlesScene extends ParticlesSceneConfiguration {

    /**
     * Use this if you want to manually set to next frame, while animations are stopped.
     */
    void nextFrame();

    /**
     * Resets and makes new random frame. This is useful for re-generating new fancy static
     * backgrounds when not using animations.
     */
    void makeBrandNewFrame();

    /**
     * Resets and makes new random frame where all points are out of screen bounds and will be
     * moving into the screen once animation starts.
     */
    void makeBrandNewFrameWithPointsOffscreen();

}
