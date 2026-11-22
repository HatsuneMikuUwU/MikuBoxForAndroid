package com.neko.hosttoip

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import org.json.JSONObject
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.HostToIpScreen
import top.nkbe.mikubox.ui.page.tools.HostToIpUiState

class HostToIP : ThemedActivity() {

    private val requestNetwork: RequestNetwork by lazy { RequestNetwork(this) }
    private var uiState by mutableStateOf(HostToIpUiState())

    private val requestListener = object : RequestNetwork.RequestListener {
        override fun onResponse(
            tag: String,
            response: String,
            responseHeaders: HashMap<String, Any>,
        ) {
            val formatted = try {
                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    val ip = json.optString("query")
                    val country = json.optString("country", "Unknown")
                    val city = json.optString("city", "Unknown")
                    val ispName = json.optString("isp", json.optString("org", "Unknown"))
                    getString(
                        R.string.hip_result_format,
                        uiState.host.trim(),
                        ip,
                        country,
                        city,
                        ispName,
                    )
                } else {
                    val msg = json.optString("message", "Unknown Error")
                    "API Failed: $msg"
                }
            } catch (e: Exception) {
                "Parse Error: ${e.message}"
            }
            uiState = uiState.copy(isLoading = false, resultText = formatted)
        }

        override fun onErrorResponse(tag: String, message: String) {
            uiState = uiState.copy(isLoading = false, resultText = "Network Error: $message")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMikuContent {
            HostToIpScreen(
                state = uiState,
                onHostChange = { host ->
                    uiState = uiState.copy(host = host, inputError = null)
                },
                onLookup = {
                    val host = uiState.host.trim()
                    if (host.isEmpty()) {
                        uiState = uiState.copy(inputError = getString(R.string.hip_error_empty))
                    } else {
                        uiState = uiState.copy(
                            isLoading = true,
                            inputError = null,
                            resultText = getString(R.string.hip_resolving),
                        )
                        requestNetwork.startRequestNetwork(
                            RequestNetworkController.GET,
                            "http://ip-api.com/json/$host",
                            "TAG_CHECK_IP",
                            requestListener,
                        )
                    }
                },
                onBack = { finish() },
            )
        }
    }
}
