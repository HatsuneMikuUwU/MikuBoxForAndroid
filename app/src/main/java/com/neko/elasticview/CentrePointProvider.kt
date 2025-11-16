package com.neko.elasticview

import android.view.MotionEvent
import android.view.View

internal abstract class CentrePointProvider(protected val parentView: View) {

    private val screenOffDistance = -300f
    protected var cx = screenOffDistance
    protected var cy = screenOffDistance

    init {
        parentView.setOnTouchListener { v, event ->
            attach(event)
            v.onTouchEvent(event)
        }
    }

    private fun attach(event: MotionEvent) {
        val (x, y) = if (event.action == MotionEvent.ACTION_MOVE) {
            event.x to event.y
        } else {
            screenOffDistance to screenOffDistance
        }
        cx = x
        cy = y
        parentView.invalidate()
    }
}