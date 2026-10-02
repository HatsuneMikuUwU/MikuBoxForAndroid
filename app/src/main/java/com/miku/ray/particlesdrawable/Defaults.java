package com.miku.ray.particlesdrawable;

import android.content.res.Resources;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import android.util.TypedValue;

public final class Defaults {

    private Defaults() {
        throw new UnsupportedOperationException();
    }

    public static final int DENSITY = 60;

    public static final int FRAME_DELAY = 10;

    @ColorInt
    public static final int LINE_COLOR = Color.WHITE;

    public static final float LINE_LENGTH = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 86, Resources.getSystem().getDisplayMetrics());

    public static final float LINE_THICKNESS = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1, Resources.getSystem().getDisplayMetrics());

    @ColorInt
    public static final int PARTICLE_COLOR = Color.WHITE;

    public static final float PARTICLE_RADIUS_MAX = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 3f, Resources.getSystem().getDisplayMetrics());

    public static final float PARTICLE_RADIUS_MIN = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 1f, Resources.getSystem().getDisplayMetrics());

    public static final float SPEED_FACTOR = 1f;
}
