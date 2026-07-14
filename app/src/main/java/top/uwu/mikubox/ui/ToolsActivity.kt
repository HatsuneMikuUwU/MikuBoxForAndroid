package top.uwu.mikubox.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
import top.uwu.mikubox.databinding.ActivityToolsBinding
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

/** Core-independent diagnostics: a URL/latency test and a network-info dump. */
class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolsTab.addTab(
            binding.toolsTab.newTab().setText(getString(R.string.tools_connection_test))
        )
        binding.btnTest.setOnClickListener { runTest() }
        binding.tvNetworkInfo.text = networkInfo()
    }

    private fun runTest() {
        val url = binding.etTestUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) return
        binding.btnTest.isEnabled = false
        binding.tvTestResult.text = getString(R.string.tools_testing)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { probe(url) }
            binding.tvTestResult.text = result
            binding.btnTest.isEnabled = true
        }
    }

    private fun probe(url: String): String = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            instanceFollowRedirects = false
        }
        val start = SystemClock.elapsedRealtime()
        conn.connect()
        val code = conn.responseCode
        val elapsed = SystemClock.elapsedRealtime() - start
        conn.disconnect()
        getString(R.string.tools_test_ok, code, elapsed)
    }.getOrElse { getString(R.string.tools_test_failed, it.message ?: it.javaClass.simpleName) }

    private fun networkInfo(): String {
        val sb = StringBuilder()
        val cm = getSystemService<ConnectivityManager>()
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> getString(R.string.tools_net_none)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> getString(R.string.tools_net_other)
        }
        sb.append(getString(R.string.tools_net_transport, transport)).append('\n')

        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .forEach { nic ->
                    val addrs = nic.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .joinToString(", ") { it.hostAddress ?: "" }
                    if (addrs.isNotBlank()) sb.append(nic.name).append(": ").append(addrs).append('\n')
                }
        }
        return sb.toString().trimEnd()
    }
}
