package top.uwu.mikubox.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.uwu.mikubox.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "UwU"
        findViewById<TextView>(R.id.splash_version).text =
            getString(R.string.splash_version, versionName)
        lifecycleScope.launch {
            delay(1500)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    @Deprecated("Splash screen does not handle back navigation")
    override fun onBackPressed() = Unit
}
