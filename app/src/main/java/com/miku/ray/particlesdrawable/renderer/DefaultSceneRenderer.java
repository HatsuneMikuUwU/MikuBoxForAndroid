package com.miku.ray.particlesdrawable.renderer;

import com.miku.ray.particlesdrawable.KeepAsApi;
import com.miku.ray.particlesdrawable.model.Scene;
import com.miku.ray.particlesdrawable.contract.LowLevelRenderer;
import com.miku.ray.particlesdrawable.contract.SceneRenderer;
import com.miku.ray.particlesdrawable.util.DistanceResolver;
import com.miku.ray.particlesdrawable.util.LineColorResolver;
import com.miku.ray.particlesdrawable.util.ParticleColorResolver;

import java.nio.FloatBuffer;

import androidx.annotation.NonNull;

@KeepAsApi
public class DefaultSceneRenderer implements SceneRenderer {

    private final LowLevelRenderer renderer;

    public DefaultSceneRenderer(@NonNull final LowLevelRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void drawScene(@NonNull final Scene scene) {
        if (scene.getDensity() > 0) {

            final int particleColor = ParticleColorResolver.resolveParticleColorWithSceneAlpha(
                scene.getParticleColor(),
                scene.getAlpha()
            );

            final FloatBuffer radiuses = scene.getRadiuses();
            final int particlesCount = scene.getDensity();
            for (int i = 0; i < particlesCount; i++) {

                final float x1 = scene.getParticleX(i);
                final float y1 = scene.getParticleY(i);

                for (int j = i + 1; j < particlesCount; j++) {

                    final float x2 = scene.getParticleX(j);
                    final float y2 = scene.getParticleY(j);

                    final float distance = DistanceResolver.distance(x1, y1, x2, y2);
                    if (distance < scene.getLineLength()) {

                        final int lineColor = LineColorResolver.resolveLineColorWithAlpha(
                            scene.getAlpha(),
                            scene.getLineColor(),
                            scene.getLineLength(),
                            distance);

                        renderer.drawLine(
                            x1,
                            y1,
                            x2,
                            y2,
                            scene.getLineThickness(),
                            lineColor);
                    }
                }

                final float radius = radiuses.get(i);
                renderer.fillCircle(x1, y1, radius, particleColor);
            }
        }
    }
}
