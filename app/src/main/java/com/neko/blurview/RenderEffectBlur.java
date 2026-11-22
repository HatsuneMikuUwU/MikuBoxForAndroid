package com.neko.blurview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.S)
public class RenderEffectBlur implements BlurAlgorithm {

    private final RenderNode node = new RenderNode("BlurViewNode");

    private int height, width;
    private float lastBlurRadius = 1f;

    @Nullable
    public BlurAlgorithm fallbackAlgorithm;
    private Context context;

    public RenderEffectBlur() {
    }

    @Override
    public Bitmap blur(@NonNull Bitmap bitmap, float blurRadius) {
        float safeRadius = Math.max(1f, blurRadius);
        lastBlurRadius = safeRadius;

        try {
            if (bitmap.getHeight() != height || bitmap.getWidth() != width) {
                height = bitmap.getHeight();
                width = bitmap.getWidth();
                node.setPosition(0, 0, width, height);
            }
            Canvas canvas = node.beginRecording();
            canvas.drawBitmap(bitmap, 0, 0, null);
            node.endRecording();
            node.setRenderEffect(RenderEffect.createBlurEffect(safeRadius, safeRadius, Shader.TileMode.MIRROR));
        } catch (Throwable ignored) {
            if (fallbackAlgorithm == null && context != null) {
                fallbackAlgorithm = new RenderScriptBlur(context);
            }
            if (fallbackAlgorithm != null) {
                return fallbackAlgorithm.blur(bitmap, safeRadius);
            }
        }

        return bitmap;
    }

    @Override
    public void destroy() {
        node.discardDisplayList();
        if (fallbackAlgorithm != null) {
            fallbackAlgorithm.destroy();
        }
    }

    @Override
    public boolean canModifyBitmap() {
        return true;
    }

    @NonNull
    @Override
    public Bitmap.Config getSupportedBitmapConfig() {
        return Bitmap.Config.ARGB_8888;
    }

    @Override
    public float scaleFactor() {
        return BlurController.DEFAULT_SCALE_FACTOR;
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap) {
        if (canvas.isHardwareAccelerated()) {
            try {
                canvas.drawRenderNode(node);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (fallbackAlgorithm == null && context != null) {
            fallbackAlgorithm = new RenderScriptBlur(context);
        }
        if (fallbackAlgorithm != null) {
            fallbackAlgorithm.blur(bitmap, lastBlurRadius);
            fallbackAlgorithm.render(canvas, bitmap);
        } else {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
    }

    void setContext(@NonNull Context context) {
        this.context = context;
    }
}
