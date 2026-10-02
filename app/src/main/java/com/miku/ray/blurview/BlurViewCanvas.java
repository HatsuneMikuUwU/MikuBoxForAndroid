package com.miku.ray.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

public class BlurViewCanvas extends Canvas {
    public BlurViewCanvas(@NonNull Bitmap bitmap) {
        super(bitmap);
    }
}
