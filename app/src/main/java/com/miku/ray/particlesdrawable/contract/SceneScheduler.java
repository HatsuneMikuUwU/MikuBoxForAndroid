package com.miku.ray.particlesdrawable.contract;

import androidx.annotation.Keep;

@Keep
public interface SceneScheduler {

    void scheduleNextFrame(long delay);

    void unscheduleNextFrame();

    void requestRender();
}
