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

import android.content.res.Resources;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.ColorInt;

/**
 * Default values are here.
 */
final class Defaults {

    private Defaults() {
        throw new UnsupportedOperationException();
    }

    static final int DEFAULT_DOT_NUMBER = 60;
    static final float DEFAULT_MAX_DOT_RADIUS = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3f, Resources.getSystem().getDisplayMetrics());
    static final float DEFAULT_MIN_DOT_RADIUS = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1f, Resources.getSystem().getDisplayMetrics());
    static final float DEFAULT_LINE_THICKNESS = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1, Resources.getSystem().getDisplayMetrics());
    @ColorInt
    static final int DEFAULT_DOT_COLOR = Color.WHITE;
    @ColorInt
    static final int DEFAULT_LINE_COLOR = Color.WHITE;
    static final float DEFAULT_LINE_DISTANCE = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 86, Resources.getSystem().getDisplayMetrics());
    static final float DEFAULT_STEP_MULTIPLIER = 1f;
    static final int DEFAULT_DELAY = 10;
}
