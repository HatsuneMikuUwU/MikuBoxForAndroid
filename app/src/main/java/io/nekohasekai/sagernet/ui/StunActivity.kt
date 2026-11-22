package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import libcore.Libcore
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.StunScreen
import top.nkbe.mikubox.ui.page.tools.StunUiState

class StunActivity : ThemedActivity() {

    private var uiState by mutableStateOf(StunUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMikuContent {
            StunScreen(
                state = uiState,
                onServerChange = { server ->
                    uiState = uiState.copy(server = server)
                },
                onStartTest = { doTest() },
                onDismissError = {
                    uiState = uiState.copy(errorMessage = null)
                },
                onBack = { finish() },
            )
        }
    }

    private fun doTest() {
        val server = uiState.server.trim()
        if (server.isEmpty() || uiState.isTesting) return
        uiState = uiState.copy(isTesting = true, errorMessage = null)

        runOnDefaultDispatcher {
            val result = runCatching {
                val res = Libcore.stunTest(server)
                if (res != null && res.success) {
                    res.text
                } else {
                    throw IllegalStateException(res?.text ?: "STUN test failed")
                }
            }
            onMainDispatcher {
                result.onSuccess { text ->
                    uiState = uiState.copy(isTesting = false, result = text)
                }.onFailure { err ->
                    uiState = uiState.copy(isTesting = false, errorMessage = err.readableMessage)
                }
            }
        }
    }
}
