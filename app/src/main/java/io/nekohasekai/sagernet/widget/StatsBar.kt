package io.nekohasekai.sagernet.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.google.android.material.bottomappbar.BottomAppBar
import com.neko.marquee.text.AutoMarqueeTextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class StatsBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.bottomAppBarStyle,
) : BottomAppBar(context, attrs, defStyleAttr) {
    private lateinit var statusText: AutoMarqueeTextView
    private lateinit var ipStatusText: AutoMarqueeTextView
    private lateinit var txText: TextView
    private lateinit var rxText: TextView
    private lateinit var behavior: YourBehavior

    var allowShow = true

    override fun getBehavior(): YourBehavior {
        if (!this::behavior.isInitialized) behavior = YourBehavior { allowShow }
        return behavior
    }

    class YourBehavior(val getAllowShow: () -> Boolean) : Behavior() {
        override fun onNestedScroll(
            coordinatorLayout: CoordinatorLayout, child: BottomAppBar, target: View,
            dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed + dyUnconsumed,
                dxUnconsumed,
                0,
                type,
                consumed
            )
        }

        override fun slideUp(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideUp(child)
        }

        override fun slideDown(child: BottomAppBar) {
            if (!getAllowShow()) return
            super.slideDown(child)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        statusText = findViewById(R.id.status)
        ipStatusText = findViewById(R.id.ip_status) 
        txText = findViewById(R.id.tx)
        rxText = findViewById(R.id.rx)
        super.setOnClickListener(l)
    }

    private fun setStatus(text: CharSequence) {
        statusText.text = text
        TooltipCompat.setTooltipText(this, text)
    }

    fun changeState(state: BaseService.State) {
        val activity = context as? LifecycleOwner ?: return
        activity.lifecycleScope.launch(Dispatchers.Main) {
            delay(100L)
            when (state) {
                BaseService.State.Connected -> {
                    hideOnScroll = true
                    allowShow = true
                    performShow()
                    setStatus(context.getText(R.string.vpn_connected))
                    ipStatusText.visibility = View.GONE
                    ipStatusText.text = ""
                }

                else -> {
                    hideOnScroll = false
                    allowShow = false
                    performHide()
                    updateSpeed(0, 0)
                    ipStatusText.visibility = View.GONE
                    ipStatusText.text = ""
                    setStatus(
                        context.getText(
                            when (state) {
                                BaseService.State.Connecting -> R.string.connecting
                                BaseService.State.Stopping -> R.string.stopping
                                else -> R.string.not_connected
                            }
                        )
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateSpeed(txRate: Long, rxRate: Long) {
        txText.text = "▲  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, txRate)
            )
        }"
        rxText.text = "▼  ${
            context.getString(
                R.string.speed, Formatter.formatFileSize(context, rxRate)
            )
        }"
    }

    private fun getFlagEmoji(countryCode: String): String {
        try {
            if (countryCode.length != 2) {
                return "❓"
            }
            val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            return "❓"
        }
    }

    private suspend fun getPublicIpInfo(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://speed.cloudflare.com/meta")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.has("clientIp") && json.has("country")) {
                    val ip = json.getString("clientIp")
                    val countryCode = json.getString("country")
                    val flag = getFlagEmoji(countryCode)
                    
                    val organization = if (json.has("asOrganization")) {
                         json.getString("asOrganization")
                    } else {
                        ""
                    }

                    val ipInfo = "IP: $ip ($flag $countryCode)"
                    if (organization.isNotEmpty()) {
                        "$ipInfo • $organization"
                    } else {
                        ipInfo
                    }
                } else {
                    context.getString(R.string.ip_info_failed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                context.getString(R.string.ip_info_failed)
            }
        }
    }
    
    fun testConnection() {
        val activity = context as MainActivity
        isEnabled = false
        setStatus(app.getText(R.string.connection_test_testing))
        
        ipStatusText.visibility = View.GONE
        ipStatusText.text = ""
        
        runOnDefaultDispatcher {
            try {
                val elapsed = activity.urlTest()
                val latencyResult = app.getString(
                    if (DataStore.connectionTestURL.startsWith("https://")) {
                        R.string.connection_test_available
                    } else {
                        R.string.connection_test_available_http
                    }, elapsed
                )

                onMainDispatcher {
                    isEnabled = true
                    setStatus(latencyResult)

                    if (DataStore.connectionTestWithIp) {
                        activity.lifecycleScope.launch {
                            val ipInfo = getPublicIpInfo()

                            if (DataStore.showIpInTwoLine) { 
                                ipStatusText.text = ipInfo
                                ipStatusText.visibility = View.VISIBLE
                            } else {
                                setStatus("$latencyResult | $ipInfo")
                                ipStatusText.visibility = View.GONE
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Logs.w(e.toString())
                onMainDispatcher {
                    isEnabled = true
                    setStatus(app.getText(R.string.connection_test_testing)) 
                    ipStatusText.visibility = View.GONE
                    ipStatusText.text = ""
                    activity.snackbar(
                        app.getString(
                            R.string.connection_test_error, e.readableMessage
                        )
                    ).show()
                }
            }
        }
    }
}
