package com.miku.ray.ui.shortcut
import com.miku.ray.ui.base.BaseActivity
import android.os.Bundle
import com.miku.ray.R
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.LauncherManager

class ScStartActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (!CoreServiceManager.isRunning()) {
            LauncherManager.startServiceFromToggle(this)
        }
        finish()
    }
}
