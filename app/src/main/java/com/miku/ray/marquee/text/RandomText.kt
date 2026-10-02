package com.miku.ray.marquee.text

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class RandomText(context: Context, attrs: AttributeSet?) : AppCompatTextView(context, attrs), Runnable {

    private val updateHandler = Handler(Looper.getMainLooper())
    private val isRunnable: Boolean

    init {
        isRunnable = attrs?.getAttributeBooleanValue(null, "uwu_runnable", true) ?: true

        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        setHorizontallyScrolling(true)
        isSelected = true

    }

    override fun run() {
        setRandomText()
        updateHandler.postDelayed(this, 3000)
    }

    private fun setRandomText() {
        val res = resources
        val pkgName = context.packageName

        val id = res.getIdentifier("array/uwu_random_text", "array", pkgName)
        if (id == 0) return

        val texts = res.getStringArray(id)
        if (texts.isEmpty()) return

        text = texts.random()

        isSelected = true
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        if (visibility == View.VISIBLE) {
            setRandomText()

            if (isRunnable) {
                updateHandler.removeCallbacks(this)
                updateHandler.postDelayed(this, 3000)
            }
        } else {
            updateHandler.removeCallbacks(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateHandler.removeCallbacks(this)
    }
}
