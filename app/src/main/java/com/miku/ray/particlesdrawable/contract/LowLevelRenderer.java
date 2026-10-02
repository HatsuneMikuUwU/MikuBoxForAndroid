package com.miku.ray.particlesdrawable.contract;

import androidx.annotation.ColorInt;

public interface LowLevelRenderer {

    void drawLine(
        float startX,
        float startY,
        float stopX,
        float stopY,
        float strokeWidth,
        @ColorInt int color);

    void fillCircle(
        float cx,
        float cy,
        float radius,
        @ColorInt int color);
}
