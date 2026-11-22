package com.neko.ip.hostchecker

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.HostCheckerScreen
import top.nkbe.mikubox.ui.page.tools.HostCheckerUiState
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class HostChecker : ThemedActivity() {

    private lateinit var sp: SharedPreferences
    private val methods = listOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "CONNECT", "TRACE", "PATCH")
    private var uiState by mutableStateOf(HostCheckerUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sp = PreferenceManager.getDefaultSharedPreferences(this)
        uiState = HostCheckerUiState(
            host = sp.getString("hostChecker", "").orEmpty(),
            proxy = sp.getString("proxyChecker", "").orEmpty(),
            isDirect = sp.getBoolean("Xen", true),
            selectedMethod = methods[0],
            methods = methods,
        )

        setMikuContent {
            HostCheckerScreen(
                state = uiState,
                onHostChange = { uiState = uiState.copy(host = it) },
                onProxyChange = { uiState = uiState.copy(proxy = it) },
                onSelectDirect = { direct ->
                    sp.edit().putBoolean("Xen", direct).apply()
                    uiState = uiState.copy(isDirect = direct)
                },
                onSelectMethod = { uiState = uiState.copy(selectedMethod = it) },
                onCheck = { submitCheck() },
                onClearLogs = { uiState = uiState.copy(logs = emptyList()) },
                onBack = { finish() },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sp.edit()
            .putString("hostChecker", uiState.host.trim())
            .putString("proxyChecker", uiState.proxy.trim())
            .apply()
    }

    private fun submitCheck() {
        val host = uiState.host.trim()
        val proxyText = uiState.proxy.trim()
        val isDirect = uiState.isDirect

        if (host.isEmpty()) {
            Toast.makeText(this, R.string.hostchecker_url, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isDirect && proxyText.isEmpty()) {
            Toast.makeText(this, R.string.hostchecker_proxy, Toast.LENGTH_SHORT).show()
            return
        }

        val method = uiState.selectedMethod
        val (ipProxy, portProxy) = if (!isDirect) {
            if (proxyText.contains(":")) {
                val split = proxyText.split(":")
                split[0] to (if (split.size > 1 && split[1].isNotEmpty()) split[1] else "8080")
            } else {
                proxyText to "8080"
            }
        } else {
            "" to "8080"
        }

        val initialLogs = uiState.logs + listOf(
            "====================================",
            "$method - URL: http://$host",
            if (isDirect) {
                getString(R.string.hostchecker_log_mode_direct)
            } else {
                getString(R.string.hostchecker_log_mode_proxy, ipProxy, portProxy)
            },
        )
        uiState = uiState.copy(isChecking = true, logs = initialLogs)

        lifecycleScope.launch(Dispatchers.IO) {
            val responseLines = mutableListOf<String>()
            var errorMsg: String? = null

            try {
                val url = URL("http://$host")
                val conn = if (isDirect) {
                    url.openConnection() as HttpURLConnection
                } else {
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(ipProxy, portProxy.toInt()))
                    url.openConnection(proxy) as HttpURLConnection
                }

                conn.apply {
                    requestMethod = method
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                    )
                    readTimeout = 10000
                    connectTimeout = 10000
                    doInput = true
                }

                val code = conn.responseCode
                val msg = conn.responseMessage
                responseLines.add("HTTP/1.1 $code $msg")

                for ((key, value) in conn.headerFields) {
                    if (key != null) {
                        responseLines.add("$key: ${value.joinToString(", ")}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.message ?: e.toString()
            }

            withContext(Dispatchers.Main) {
                val appended = if (errorMsg != null) {
                    listOf(
                        getString(R.string.hostchecker_log_error, errorMsg),
                        getString(R.string.hostchecker_log_stopped),
                    )
                } else {
                    responseLines + listOf("", getString(R.string.hostchecker_log_stopped))
                }
                uiState = uiState.copy(
                    isChecking = false,
                    logs = uiState.logs + appended,
                )
            }
        }
    }
}
