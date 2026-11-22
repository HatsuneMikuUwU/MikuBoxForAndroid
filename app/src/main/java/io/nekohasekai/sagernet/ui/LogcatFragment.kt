package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import libcore.Libcore
import moe.matsuri.nb4a.utils.SendLog
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.logs.LogcatScreen
import top.nkbe.mikubox.ui.page.logs.LogcatScreenCallbacks

class LogcatFragment : ToolbarFragment() {

    private var logLines by mutableStateOf<List<String>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        reloadSession()
        return mikuComposeView {
            LogcatScreen(
                lines = logLines,
                callbacks = LogcatScreenCallbacks(
                    onOpenMenu = {
                        (activity as? NavigationHost)?.showNavigationSheet()
                            ?: activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onRefresh = { reloadSession() },
                    onSendLog = {
                        val ctx = requireContext()
                        runOnDefaultDispatcher {
                            SendLog.sendLog(ctx, "NB4A")
                        }
                    },
                    onClearLog = { clearLog() },
                ),
            )
        }
    }

    private fun reloadSession() {
        runOnDefaultDispatcher {
            val raw = runCatching { String(SendLog.getNekoLog(50 * 1024)) }.getOrDefault("")
            val parsed = raw.lines().filter { it.isNotEmpty() }
            onMainDispatcher {
                logLines = parsed
            }
        }
    }

    private fun clearLog() {
        runOnDefaultDispatcher {
            try {
                Libcore.nekoLogClear()
                Runtime.getRuntime().exec("/system/bin/logcat -c")
            } catch (e: Exception) {
                onMainDispatcher {
                    snackbar(e.readableMessage).show()
                }
                return@runOnDefaultDispatcher
            }
            onMainDispatcher {
                logLines = emptyList()
            }
        }
    }
}
