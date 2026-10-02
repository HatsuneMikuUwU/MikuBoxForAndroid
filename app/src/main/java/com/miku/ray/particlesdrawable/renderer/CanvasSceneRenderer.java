package com.miku.ray.particlesdrawable.renderer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;

import com.miku.ray.particlesdrawable.KeepAsApi;
import com.miku.ray.particlesdrawable.contract.LowLevelRenderer;
import com.miku.ray.particlesdrawable.contract.SceneRenderer;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@KeepAsApi
public final class CanvasSceneRenderer implements LowLevelRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private Canvas canvas;

    public void setCanvas(@Nullable final Canvas canvas) {
        this.canvas = canvas;
    }

    @NonNull
    public Paint getPaint() {
        return paint;
    }

    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public void drawLine(
        final float startX,
        final float startY,
        final float stopX,
        final float stopY,
        final float strokeWidth,
        @ColorInt final int color) {
        if (canvas == null) {
            throw new IllegalStateException("Called in wrong state");
        }
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        canvas.drawLine(startX, startY, stopX, stopY, paint);
    }

    @Override
    public void fillCircle(
        final float cx,
        final float cy,
        final float radius,
        @ColorInt final int color) {
        if (canvas == null) {
            throw new IllegalStateException("Called in wrong state");
        }
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }
}
