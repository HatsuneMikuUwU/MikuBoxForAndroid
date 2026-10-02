package com.miku.ray.particlesdrawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.miku.ray.particlesdrawable.contract.SceneConfiguration;
import com.miku.ray.particlesdrawable.contract.SceneController;
import com.miku.ray.particlesdrawable.contract.SceneRenderer;
import com.miku.ray.particlesdrawable.contract.SceneScheduler;
import com.miku.ray.particlesdrawable.engine.Engine;
import com.miku.ray.particlesdrawable.engine.SceneConfigurator;
import com.miku.ray.particlesdrawable.model.Scene;
import com.miku.ray.particlesdrawable.renderer.CanvasSceneRenderer;
import com.miku.ray.particlesdrawable.renderer.DefaultSceneRenderer;
import com.miku.ray.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@Keep
public class ParticlesDrawable extends Drawable implements
Animatable,
SceneConfiguration,
SceneController,
SceneScheduler {

    private CanvasSceneRenderer canvasRenderer = new CanvasSceneRenderer();

    private Scene scene = new Scene();

    private SceneConfigurator sceneConfigurator = new SceneConfigurator();

    private SceneRenderer renderer = new DefaultSceneRenderer(canvasRenderer);

    private Engine engine = new Engine(scene, this, renderer);

    @Override
    public void inflate(
        @NonNull final Resources r,
        @NonNull final XmlPullParser parser,
        @NonNull final AttributeSet attrs,
        @Nullable final Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);

        final TypedArray a;
        if (theme != null) {
            a = theme.obtainStyledAttributes(attrs, R.styleable.ParticlesView, 0, 0);
        } else {
            a = r.obtainAttributes(attrs, R.styleable.ParticlesView);
        }

        try {
            final int count = a.getIndexCount();
            float particleRadiusMax = Defaults.PARTICLE_RADIUS_MAX;
            float particleRadiusMin = Defaults.PARTICLE_RADIUS_MIN;

            for (int i = 0; i < count; i++) {
                final int attr = a.getIndex(i);
                if (attr == R.styleable.ParticlesView_density) {
                    scene.setDensity(a.getInteger(attr, Defaults.DENSITY));
                } else if (attr == R.styleable.ParticlesView_frameDelayMillis) {
                    scene.setFrameDelay(a.getInteger(attr, Defaults.FRAME_DELAY));
                } else if (attr == R.styleable.ParticlesView_lineColor) {
                    scene.setLineColor(a.getColor(attr, Defaults.LINE_COLOR));
                } else if (attr == R.styleable.ParticlesView_lineLength) {
                    scene.setLineLength(a.getDimension(attr, Defaults.LINE_LENGTH));
                } else if (attr == R.styleable.ParticlesView_lineThickness) {
                    scene.setLineThickness(a.getDimension(attr, Defaults.LINE_THICKNESS));
                } else if (attr == R.styleable.ParticlesView_particleColor) {
                    scene.setParticleColor(a.getColor(attr, Defaults.PARTICLE_COLOR));
                } else if (attr == R.styleable.ParticlesView_particleRadiusMax) {
                    particleRadiusMax = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MAX);
                } else if (attr == R.styleable.ParticlesView_particleRadiusMin) {
                    particleRadiusMin = a.getDimension(attr, Defaults.PARTICLE_RADIUS_MIN);
                } else if (attr == R.styleable.ParticlesView_speedFactor) {
                    scene.setSpeedFactor(a.getFloat(attr, Defaults.SPEED_FACTOR));
                }
            }
            scene.setParticleRadiusRange(particleRadiusMin, particleRadiusMax);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void setBounds(final int left, final int top, final int right, final int bottom) {
        super.setBounds(left, top, right, bottom);
        engine.setDimensions(right - left, bottom - top);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        canvasRenderer.setCanvas(canvas);
        engine.draw();
        canvasRenderer.setCanvas(null);
        engine.run();
    }

    @Override
    public void scheduleNextFrame(final long delay) {
        if (delay == 0L) {
            requestRender();
        } else {
            scheduleSelf(invalidateSelfRunnable, SystemClock.uptimeMillis() + delay);
        }
    }

    @Override
    public void unscheduleNextFrame() {
        unscheduleSelf(invalidateSelfRunnable);
    }

    @Override
    public void requestRender() {
        invalidateSelf();
    }

    @Override
    public void setAlpha(final int alpha) {
        engine.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return engine.getAlpha();
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        canvasRenderer.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        engine.start();
    }

    @Override
    public void stop() {
        engine.stop();
    }

    @Override
    public boolean isRunning() {
        return engine.isRunning();
    }

    @Override
    public void nextFrame() {
        engine.nextFrame();
    }

    @Override
    public void makeFreshFrame() {
        engine.makeFreshFrame();
    }

    @Override
    public void makeFreshFrameWithParticlesOffscreen() {
        engine.makeFreshFrameWithParticlesOffscreen();
    }

    @Override
    public void setFrameDelay(@IntRange(from = 0) final int delay) {
        scene.setFrameDelay(delay);
    }

    @Override
    public int getFrameDelay() {
        return scene.getFrameDelay();
    }

    @Override
    public void setSpeedFactor(@FloatRange(from = 0) final float speedFactor) {
        scene.setSpeedFactor(speedFactor);
    }

    @Override
    public float getSpeedFactor() {
        return scene.getSpeedFactor();
    }

    @Override
    public void setParticleRadiusRange(
        @FloatRange(from = 0.5f) final float minRadius,
        @FloatRange(from = 0.5f) final float maxRadius) {
        scene.setParticleRadiusRange(minRadius, maxRadius);
    }

    @Override
    public float getParticleRadiusMin() {
        return scene.getParticleRadiusMin();
    }

    @Override
    public float getParticleRadiusMax() {
        return scene.getParticleRadiusMax();
    }

    @Override
    public void setLineThickness(@FloatRange(from = 1) final float lineThickness) {
        scene.setLineThickness(lineThickness);
    }

    @Override
    public float getLineThickness() {
        return scene.getLineThickness();
    }

    @Override
    public void setLineLength(@FloatRange(from = 0) final float lineLength) {
        scene.setLineLength(lineLength);
    }

    @Override
    public float getLineLength() {
        return scene.getLineLength();
    }

    public void setDensity(@IntRange(from = 0) final int newNum) {
        scene.setDensity(newNum);
    }

    @Override
    public int getDensity() {
        return scene.getDensity();
    }

    public void setParticleColor(@ColorInt final int color) {
        scene.setParticleColor(color);
    }

    @Override
    public int getParticleColor() {
        return scene.getParticleColor();
    }

    @Override
    public void setLineColor(@ColorInt final int lineColor) {
        scene.setLineColor(lineColor);
    }

    @Override
    public int getLineColor() {
        return scene.getLineColor();
    }

    private final Runnable invalidateSelfRunnable = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };
}
