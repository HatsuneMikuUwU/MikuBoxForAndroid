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
                }

                else -> {
                    hideOnScroll = false
                    allowShow = false
                    performHide()
                    updateSpeed(0, 0)
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
                val url = URL("https://ipwhois.app/json/")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.getBoolean("success")) {
                    val ip = json.getString("ip")
                    val country = json.getString("country")
                    val countryCode = json.getString("country_code")
                    
                    val flag = getFlagEmoji(countryCode)
                    
                    "IP: $ip ($flag $country)"
                } else {
                    context.getString(R.string.ip_info_failed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "IP Fail: ${e.javaClass.simpleName}"
            }
        }
    }
    
    fun testConnection() {
        val activity = context as MainActivity
        isEnabled = false
        setStatus(app.getText(R.string.connection_test_testing))
        
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
                            
                            setStatus("$latencyResult • $ipInfo")
                        }
                    }
                }

            } catch (e: Exception) {
                Logs.w(e.toString())
                onMainDispatcher {
                    isEnabled = true
                    setStatus(app.getText(R.string.connection_test_testing)) 

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
