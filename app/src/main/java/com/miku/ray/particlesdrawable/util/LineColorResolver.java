package com.miku.ray.particlesdrawable.util;

import com.miku.ray.particlesdrawable.KeepAsApi;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;

@KeepAsApi
public final class LineColorResolver {

    private static final int OPAQUE = 255;

    @IntRange(from = 0, to = OPAQUE)
    private static int resolveLineAlpha(
        @IntRange(from = 0, to = OPAQUE) final int sceneAlpha,
        final float maxDistance,
        final float distance) {
        final float alphaPercent = 1f - distance / maxDistance;
        final int alpha = (int) ((float) OPAQUE * alphaPercent);
        return alpha * sceneAlpha / OPAQUE;
    }

    @ColorInt
    public static int resolveLineColorWithAlpha(
        @IntRange(from = 0, to = OPAQUE) final int sceneAlpha,
        @ColorInt final int lineColor,
        final float maxDistance,
        final float distance) {
        final int alpha = resolveLineAlpha(sceneAlpha, maxDistance, distance);
        return (lineColor & 0x00FFFFFF) | (alpha << 24);
    }
}
