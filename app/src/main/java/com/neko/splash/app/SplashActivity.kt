package com.neko.splash.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ThemedActivity() {

    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DataStore.showSplashScreen) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.uwu_activity_splash)

        versionText = findViewById(R.id.splash_version)

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        versionText.text = getString(R.string.uwu_splash_summary, versionName, versionCode)

        startSplash()
    }

    private fun startSplash() {
        lifecycleScope.launch {
            delay(2000)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onBackPressed() {
    }
}
