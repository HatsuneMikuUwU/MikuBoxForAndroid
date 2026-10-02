package com.miku.ray.blurview;

import android.graphics.Canvas;

public interface BlurController extends BlurViewFacade {

    float DEFAULT_SCALE_FACTOR = 6f;
    float DEFAULT_BLUR_RADIUS = 16f;

    boolean draw(Canvas canvas);

    void updateBlurViewSize();

    void destroy();
}
