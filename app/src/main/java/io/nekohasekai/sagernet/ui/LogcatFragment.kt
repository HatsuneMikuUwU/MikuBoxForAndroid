package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import libcore.Libcore
import moe.matsuri.nb4a.utils.SendLog
import io.nekohasekai.sagernet.ui.bottomsheet.LogcatMenuBottomSheet
import io.nekohasekai.sagernet.ui.toolbar.LogcatMenuController
import top.nkbe.mikubox.ui.component.applyBottomDockPadding

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    LogcatMenuBottomSheet.OnOptionClickListener {

    lateinit var binding: LayoutLogcatBinding
    
    private lateinit var menuController: LogcatMenuController

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutLogcatBinding.bind(view)

        val collapsingToolbar = binding.collapsingToolbar
        val toolbarView = binding.toolbar
        val appBarLayout = binding.appbar

        collapsingToolbar.title = getString(R.string.menu_log)
        
        toolbar = toolbarView
        
        menuController = LogcatMenuController(
            toolbar = toolbar,
            fragmentManager = childFragmentManager,
            listener = this
        )

        if (Build.VERSION.SDK_INT >= 23) {
            binding.textview.breakStrategy = 0 // simple
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)
        binding.scroolview.applyBottomDockPadding(20)

        binding.scroolview.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            appBarLayout.setExpanded(scrollY == 0)
        }

        reloadSession()
    }

    override fun onResume() {
        super.onResume()
        if (::menuController.isInitialized) {
            menuController.refresh()
        }
    }
    
    private fun getColorForLine(line: String): ForegroundColorSpan {
        var color = ForegroundColorSpan(Color.GRAY)
        when {
            line.contains("INFO[") || line.contains(" [Info]") -> {
                color = ForegroundColorSpan((0xFF86C166).toInt())
            }

            line.contains("ERROR[") || line.contains(" [Error]") -> {
                color = ForegroundColorSpan(Color.RED)
            }

            line.contains("WARN[") || line.contains(" [Warning]") -> {
                color = ForegroundColorSpan(Color.RED)
            }
        }
        return color
    }

    private fun reloadSession() {
        val span = SpannableString(
            String(SendLog.getNekoLog(50 * 1024))
        )
        var offset = 0
        for (line in span.lines()) {
            val color = getColorForLine(line)
            span.setSpan(
                color, offset, offset + line.length, SPAN_EXCLUSIVE_EXCLUSIVE
            )
            offset += line.length + 1
        }
        binding.textview.text = span
        binding.textview.clearFocus()
        // 等 textview 完成最终 layout 再滚动到底部
        binding.textview.doOnLayout {
            binding.scroolview.scrollTo(0, binding.textview.height)
        }
    }

    override fun onOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.action_clear_logcat -> {
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
                        binding.textview.text = ""
                    }
                }

            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "NB4A")
                }
            }

            R.id.action_refresh -> {
                reloadSession()
            }
        }
    }

}
