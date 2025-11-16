package com.neko.elasticview

import android.graphics.*
import android.view.View


internal class DebugPath(parentView: View) : CentrePointProvider(parentView) {

    private val _pathPaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
    }
    private val _circlePaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
    }
    private val _horizontalPath = Path()
    private val _verticalPath = Path()

    fun onDispatchDraw(canvas: Canvas?) {
        _verticalPath.reset()
        _horizontalPath.reset()

        _horizontalPath.moveTo(cx, 0f)
        _horizontalPath.lineTo(cx, parentView.height.toFloat())
        _verticalPath.moveTo(0f, cy)
        _verticalPath.lineTo(parentView.width.toFloat(), cy)
        canvas?.run {
            drawPath(_horizontalPath, _pathPaint)
            drawPath(_verticalPath, _pathPaint)
            drawCircle(cx, cy, 15f, _circlePaint)
        }
    }
}