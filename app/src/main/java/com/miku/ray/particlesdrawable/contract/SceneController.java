package com.miku.ray.particlesdrawable.contract;

import androidx.annotation.Keep;

@Keep
public interface SceneController {

    void nextFrame();

    void makeFreshFrame();

    void makeFreshFrameWithParticlesOffscreen();

}
