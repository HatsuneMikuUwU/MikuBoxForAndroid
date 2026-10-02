package com.miku.ray.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

public interface BlurAlgorithm {

    Bitmap blur(@NonNull Bitmap bitmap, @NonNull float blurRadius);

    void destroy();

    boolean canModifyBitmap();

    @NonNull
    Bitmap.Config getSupportedBitmapConfig();

    float scaleFactor();

    void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap);
}
