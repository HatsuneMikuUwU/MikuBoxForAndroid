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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link IParticlesView} that draws on {@link Canvas}
 */
final class CanvasParticlesView implements IParticlesView {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    @Nullable
    private Canvas mCanvas;

    void setCanvas(@Nullable final Canvas canvas) {
        mCanvas = canvas;
    }

    @NonNull
    Paint getPaint() {
        return mPaint;
    }

    void setColorFilter(final ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public void drawLine(final float startX, final float startY, final float stopX,
            final float stopY, final float strokeWidth, @ColorInt final int color) {
        if (mCanvas == null) {
            throw new IllegalStateException("Called in wrong state");
        }
        mPaint.setStrokeWidth(strokeWidth);
        mPaint.setColor(color);
        mCanvas.drawLine(startX, startY, stopX, stopY, mPaint);
    }

    @Override
    public void fillCircle(final float cx, final float cy, final float radius,
            @ColorInt final int color) {
        if (mCanvas == null) {
            throw new IllegalStateException("Called in wrong state");
        }
        mPaint.setColor(color);
        mCanvas.drawCircle(cx, cy, radius, mPaint);
    }
}
