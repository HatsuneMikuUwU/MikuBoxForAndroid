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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import io.nekohasekai.sagernet.R;

public class ParticlesView extends View implements IParticlesView, SceneScheduler, ParticlesScene {

    private final SceneController mController = new SceneController(this, this);
    private final CanvasParticlesView mCanvasParticlesView = new CanvasParticlesView();

    @VisibleForTesting
    boolean mExplicitlyStopped;

    private boolean mAttachedToWindow;
    private boolean mEmulateOnAttachToWindow;

    public ParticlesView(final Context context) {
        super(context);
        init(context, null);
    }

    public ParticlesView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ParticlesView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ParticlesView(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_HARDWARE, mCanvasParticlesView.getPaint());
        }
        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ParticlesView);
            try {
                mController.handleAttrs(a);
            } finally {
                a.recycle();
            }
        }
    }

    @NonNull
    @Keep
    public Paint getPaint() {
        return mCanvasParticlesView.getPaint();
    }

    @Override
    public void nextFrame() {
        mController.nextFrame();
    }

    @Override
    public void makeBrandNewFrame() {
        mController.makeBrandNewFrame();
    }

    @Override
    public void makeBrandNewFrameWithPointsOffscreen() {
        mController.makeBrandNewFrameWithPointsOffscreen();
    }

    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        mController.setFrameDelay(delay);
    }

    @Override
    public int getFrameDelay() {
        return mController.getFrameDelay();
    }

    @Override
    public void setStepMultiplier(@FloatRange(from = 0) final float stepMultiplier) {
        mController.setStepMultiplier(stepMultiplier);
    }

    @Override
    public float getStepMultiplier() {
        return mController.getStepMultiplier();
    }

    public void setDotRadiusRange(@FloatRange(from = 0.5f) final float minRadius, @FloatRange(from = 0.5f) final float maxRadius) {
        mController.setDotRadiusRange(minRadius, maxRadius);
    }

    @Override
    public float getMinDotRadius() {
        return mController.getMinDotRadius();
    }

    @Override
    public float getMaxDotRadius() {
        return mController.getMaxDotRadius();
    }

    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        mController.setLineThickness(lineThickness);
    }

    @Override
    public float getLineThickness() {
        return mController.getLineThickness();
    }

    public void setLineDistance(@FloatRange(from = 0) final float lineDistance) {
        mController.setLineDistance(lineDistance);
    }

    @Override
    public float getLineDistance() {
        return mController.getLineDistance();
    }

    public void setNumDots(@IntRange(from = 0) final int newNum) {
        mController.setNumDots(newNum);
    }

    @Override
    public int getNumDots() {
        return mController.getNumDots();
    }

    public void setDotColor(@ColorInt final int dotColor) {
        mController.setDotColor(dotColor);
    }

    @Override
    public int getDotColor() {
        return mController.getDotColor();
    }

    public void setLineColor(@ColorInt final int lineColor) {
        mController.setLineColor(lineColor);
    }

    @Override
    public int getLineColor() {
        return mController.getLineColor();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mController.setBounds(0, 0, w, h);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        mCanvasParticlesView.setCanvas(canvas);
        mController.draw();
        mController.run();
        mCanvasParticlesView.setCanvas(null);
    }

    @Override
    public void drawLine(final float startX, final float startY, final float stopX, final float stopY, final float strokeWidth, @ColorInt final int color) {
        mCanvasParticlesView.drawLine(startX, startY, stopX, stopY, strokeWidth, color);
    }

    @Override
    public void fillCircle(final float cx, final float cy, final float radius, @ColorInt final int color) {
        mCanvasParticlesView.fillCircle(cx, cy, radius, color);
    }

    @Override
    public void scheduleNextFrame(final long delay) {
        postInvalidateDelayed(delay);
    }

    @Override
    public void unscheduleNextFrame() {
    }

    @Override
    protected void onVisibilityChanged(@NonNull final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE) {
            stopInternal();
        } else {
            startInternal();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        startInternal();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttachedToWindow = false;
        stopInternal();
    }

    @Keep
    public void start() {
        mExplicitlyStopped = false;
        startInternal();
    }

    @Keep
    public void stop() {
        mExplicitlyStopped = true;
        stopInternal();
    }

    @VisibleForTesting
    void startInternal() {
        if (!mExplicitlyStopped && isVisibleWithAllParents(this) && isAttachedToWindowCompat()) {
            mController.start();
        }
    }

    @VisibleForTesting
    void stopInternal() {
        mController.stop();
    }

    @VisibleForTesting
    boolean isRunning() {
        return mController.isRunning();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void setEmulateOnAttachToWindow(final boolean emulateOnAttachToWindow) {
        mEmulateOnAttachToWindow = emulateOnAttachToWindow;
    }

    private boolean isAttachedToWindowCompat() {
        if (mEmulateOnAttachToWindow) {
            return mAttachedToWindow;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return isAttachedToWindow();
        }
        return mAttachedToWindow;
    }

    private boolean isVisibleWithAllParents(@NonNull final View view) {
        if (view.getVisibility() != VISIBLE) {
            return false;
        }

        final ViewParent parent = view.getParent();
        if (parent instanceof View) {
            return isVisibleWithAllParents((View) parent);
        }

        return true;
    }
}
