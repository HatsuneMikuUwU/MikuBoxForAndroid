package com.neko.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

class LayoutOff(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private var key: String? = null
    private val mHandler = Handler(Looper.getMainLooper())
    
    private val sharedPrefs by lazy {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }

    private val mTicker = object : Runnable {
        override fun run() {
            val value = key?.let {
                sharedPrefs.getInt(it, 0)
            } ?: 0

            if (value == 0) {
                if (visibility != View.VISIBLE) visibility = View.VISIBLE
            } else if (value == 1) {
                if (visibility != View.GONE) visibility = View.GONE
            }

            this@LayoutOff.invalidate()

            val now = SystemClock.uptimeMillis()
            val next = now + (1L - (now % 1L))
            mHandler.postAtTime(this, next)
        }
    }

    init {
        if (attrs != null) {
            key = attrs.getAttributeValue(null, "key")
        }
        mTicker.run()
    }
}
