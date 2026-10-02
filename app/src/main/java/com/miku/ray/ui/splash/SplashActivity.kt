package com.miku.ray.ui.splash

import com.miku.ray.ui.main.MainActivity
import android.content.Intent
import android.os.Bundle
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity

/**
 * Routes to home immediately so the system splash-screen mask reveal carries
 * the transition; no forced dwell, no cross-fade. The Android 12+ splash
 * stays on screen from process start until the first frame is drawn, which
 * produces the top-down reveal the reference client shows.
 */
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTaskRoot && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
