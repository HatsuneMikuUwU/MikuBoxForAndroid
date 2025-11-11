package com.neko.marquee.text

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import io.nekohasekai.sagernet.R
import java.util.Calendar


class Greetings @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK ||
                intent?.action == Intent.ACTION_TIME_CHANGED ||
                intent?.action == Intent.ACTION_TIMEZONE_CHANGED
            ) {
                updateGreeting()
            }
        }
    }

    init {
        updateGreeting()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeChangeReceiver, filter)
        updateGreeting()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeChangeReceiver)
    }

    override fun isFocused(): Boolean = true

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        @StringRes val greetingResId = when (hour) {
            in 5..10 -> R.string.uwu_greeting_morning
            in 11..14 -> R.string.uwu_greeting_afternoon
            in 15..18 -> R.string.uwu_greeting_evening
            in 19..23 -> R.string.uwu_greeting_night
            else -> R.string.uwu_greeting_late_night
        }
        setText(greetingResId)
    }
}
