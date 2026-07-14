package top.uwu.mikubox.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
import top.uwu.mikubox.databinding.ActivityLogcatBinding

/** Minimal in-app logcat viewer for the app's own process. */
class LogcatActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityLogcatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogcatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.fabClear.setOnClickListener {
            runCatching { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() }
            reload()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val log = withContext(Dispatchers.IO) { readLogcat() }
            binding.logcatText.text = log
            binding.logcatScroll.post {
                binding.logcatScroll.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun readLogcat(): String = runCatching {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "brief", "-t", "600")
        )
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { getString(R.string.logcat_read_failed, it.message ?: "") }
}
