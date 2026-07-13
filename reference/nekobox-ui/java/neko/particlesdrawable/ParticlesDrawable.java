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
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * The Particles Drawable
 */
@Keep
public class ParticlesDrawable extends Drawable
        implements Animatable, IParticlesView, SceneScheduler, ParticlesScene {

    private final CanvasParticlesView mCanvasParticlesView = new CanvasParticlesView();
    private final SceneController mController = new SceneController(this, this);

    @Override
    public void inflate(@NonNull final Resources r,
            @NonNull final XmlPullParser parser,
            @NonNull final AttributeSet attrs,
            @Nullable final Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        mController.inflate(r, attrs);
    }

    @NonNull
    @Keep
    public Paint getPaint() {
        return mCanvasParticlesView.getPaint();
    }

    @Override
    public void setBounds(final int left, final int top, final int right, final int bottom) {
        super.setBounds(left, top, right, bottom);
        mController.setBounds(left, top, right, bottom);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        mCanvasParticlesView.setCanvas(canvas);
        mController.draw();
        mCanvasParticlesView.setCanvas(null);
    }

    @Override
    public void drawLine(final float startX, final float startY, final float stopX,
            final float stopY, final float strokeWidth, @ColorInt final int color) {
        mCanvasParticlesView.drawLine(startX, startY, stopX, stopY, strokeWidth, color);
    }

    @Override
    public void fillCircle(final float cx, final float cy, final float radius,
            @ColorInt final int color) {
        mCanvasParticlesView.fillCircle(cx, cy, radius, color);
    }

    @Override
    public void scheduleNextFrame(final long delay) {
        scheduleSelf(mController, SystemClock.uptimeMillis() + delay);
    }

    @Override
    public void unscheduleNextFrame() {
        unscheduleSelf(mController);
    }

    @Override
    public void invalidate() {
        invalidateSelf();
    }

    @Override
    public void setAlpha(final int alpha) {
        mController.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return mController.getAlpha();
    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {
        mCanvasParticlesView.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        mController.start();
    }

    @Override
    public void stop() {
        mController.stop();
    }

    @Override
    public boolean isRunning() {
        return mController.isRunning();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nextFrame() {
        mController.nextFrame();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void makeBrandNewFrame() {
        mController.makeBrandNewFrame();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void makeBrandNewFrameWithPointsOffscreen() {
        mController.makeBrandNewFrameWithPointsOffscreen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        mController.setFrameDelay(delay);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFrameDelay() {
        return mController.getFrameDelay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStepMultiplier(@FloatRange(from = 0) final float stepMultiplier) {
        mController.setStepMultiplier(stepMultiplier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getStepMultiplier() {
        return mController.getStepMultiplier();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDotRadiusRange(@FloatRange(from = 0.5f) final float minRadius,
            @FloatRange(from = 0.5f) final float maxRadius) {
        mController.setDotRadiusRange(minRadius, maxRadius);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMinDotRadius() {
        return mController.getMinDotRadius();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getMaxDotRadius() {
        return mController.getMaxDotRadius();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        mController.setLineThickness(lineThickness);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineThickness() {
        return mController.getLineThickness();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineDistance(@FloatRange(from = 0) final float lineDistance) {
        mController.setLineDistance(lineDistance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getLineDistance() {
        return mController.getLineDistance();
    }

    /**
     * {@inheritDoc}
     */
    public void setNumDots(@IntRange(from = 0) final int newNum) {
        mController.setNumDots(newNum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDots() {
        return mController.getNumDots();
    }

    /**
     * {@inheritDoc}
     */
    public void setDotColor(@ColorInt final int dotColor) {
        mController.setDotColor(dotColor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDotColor() {
        return mController.getDotColor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLineColor(@ColorInt final int lineColor) {
        mController.setLineColor(lineColor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLineColor() {
        return mController.getLineColor();
    }
}
