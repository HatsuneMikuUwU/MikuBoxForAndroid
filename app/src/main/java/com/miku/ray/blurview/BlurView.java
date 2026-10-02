package com.miku.ray.blurview;

import static com.miku.ray.blurview.PreDrawBlurController.TRANSPARENT;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.miku.ray.R;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class BlurView extends FrameLayout {

    private static final String TAG = BlurView.class.getSimpleName();

    BlurController blurController = new NoOpController();

    @ColorInt
    private int overlayColor;

    public BlurView(Context context) {
        super(context);
        init(null, 0);
    }

    public BlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public BlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }

    private void init(AttributeSet attrs, int defStyleAttr) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BlurView, defStyleAttr, 0);
        overlayColor = a.getColor(R.styleable.BlurView_blurOverlayColor, TRANSPARENT);
        a.recycle();
    }

    @Override
    public void draw(Canvas canvas) {
        boolean shouldDraw = blurController.draw(canvas);
        if (shouldDraw) {
            super.draw(canvas);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blurController.updateBlurViewSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blurController.setBlurAutoUpdate(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isHardwareAccelerated()) {
            Log.e(TAG, "BlurView can't be used in not hardware-accelerated window!");
        } else {
            blurController.setBlurAutoUpdate(true);
        }
    }

    public BlurViewFacade setupWith(@NonNull ViewGroup rootView, BlurAlgorithm algorithm) {
        this.blurController.destroy();
        BlurController blurController = new PreDrawBlurController(this, rootView, overlayColor, algorithm);
        this.blurController = blurController;

        return blurController;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public BlurViewFacade setupWith(@NonNull ViewGroup rootView) {
        return setupWith(rootView, getBlurAlgorithm());
    }

    public BlurViewFacade setBlurRadius(float radius) {
        return blurController.setBlurRadius(radius);
    }

    public BlurViewFacade setOverlayColor(@ColorInt int overlayColor) {
        this.overlayColor = overlayColor;
        return blurController.setOverlayColor(overlayColor);
    }

    public BlurViewFacade setBlurAutoUpdate(boolean enabled) {
        return blurController.setBlurAutoUpdate(enabled);
    }

    public BlurViewFacade setBlurEnabled(boolean enabled) {
        return blurController.setBlurEnabled(enabled);
    }

    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.N)
    private BlurAlgorithm getBlurAlgorithm() {
        BlurAlgorithm algorithm;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            algorithm = new RenderEffectBlur();
        } else {
            algorithm = new RenderScriptBlur(getContext());
        }
        return algorithm;
    }
}
