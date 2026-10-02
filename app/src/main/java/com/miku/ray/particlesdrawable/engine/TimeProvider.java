package com.miku.ray.particlesdrawable.engine;

import android.os.SystemClock;

final class TimeProvider {

    public long uptimeMillis() {
        return SystemClock.uptimeMillis();
    }
}
