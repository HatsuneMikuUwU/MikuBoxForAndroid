package top.uwu.mikubox.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.uwu.mikubox.R

class SplashActivity : EdgeToEdgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
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
