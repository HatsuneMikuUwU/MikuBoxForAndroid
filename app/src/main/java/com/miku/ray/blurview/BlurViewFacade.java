package com.miku.ray.blurview;

import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public interface BlurViewFacade {

    BlurViewFacade setBlurEnabled(boolean enabled);

    BlurViewFacade setBlurAutoUpdate(boolean enabled);

    BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable);

    BlurViewFacade setBlurRadius(float radius);

    BlurViewFacade setOverlayColor(@ColorInt int overlayColor);
}
