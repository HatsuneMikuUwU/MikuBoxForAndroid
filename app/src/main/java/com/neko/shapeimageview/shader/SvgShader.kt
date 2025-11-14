package com.neko.shapeimageview.shader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import com.neko.shapeimageview.path.SvgUtil
import com.neko.shapeimageview.path.parser.PathInfo
import io.nekohasekai.sagernet.R
import kotlin.math.min
import kotlin.math.roundToInt

class SvgShader @JvmOverloads constructor(
    private var resId: Int = -1,
    private var borderType: Int = BORDER_TYPE_DEFAULT
) : ShaderHelper() {

    companion object {
        const val BORDER_TYPE_DEFAULT = 0
        const val BORDER_TYPE_FILL = 1

        const val STROKE_CAP_DEFAULT = -1
        const val STROKE_CAP_BUTT = 0
        const val STROKE_CAP_ROUND = 1
        const val STROKE_CAP_SQUARE = 2

        const val STROKE_JOIN_DEFAULT = -1
        const val STROKE_JOIN_BEVEL = 0
        const val STROKE_JOIN_MITER = 1
        const val STROKE_JOIN_ROUND = 2
    }

    private val path = Path()
    private val borderPath = Path()
    private val pathMatrix = Matrix()
    private val pathDimensions = FloatArray(2)
    private var shapePath: PathInfo? = null

    private var strokeCap = STROKE_CAP_DEFAULT
    private var strokeJoin = STROKE_JOIN_DEFAULT
    private var strokeMiter = 0

    override fun init(context: Context, attrs: AttributeSet?, defStyle: Int) {
        super.init(context, attrs, defStyle)
        
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.ShaderImageView, defStyle, 0)
            resId = typedArray.getResourceId(R.styleable.ShaderImageView_siShape, resId)
            borderType = typedArray.getInt(R.styleable.ShaderImageView_siBorderType, borderType)
            strokeCap = typedArray.getInt(R.styleable.ShaderImageView_siStrokeCap, strokeCap)
            strokeJoin = typedArray.getInt(R.styleable.ShaderImageView_siStrokeJoin, strokeJoin)
            strokeMiter = typedArray.getDimensionPixelSize(R.styleable.ShaderImageView_siStrokeMiter, strokeMiter)
            typedArray.recycle()
        }

        setShapeResId(context, resId)
        setBorderType(borderType)
        setStrokeCap(strokeCap)
        setStrokeJoin(strokeJoin)
        setStrokeMiter(strokeMiter)
    }

    fun setShapeResId(context: Context, resId: Int) {
        if (resId != -1) {
            shapePath = SvgUtil.readSvg(context, resId)
        } else {
            throw RuntimeException("No resource is defined as shape")
        }
    }

    fun setStrokeMiter(strokeMiter: Int) {
        this.strokeMiter = strokeMiter
        if (strokeMiter > 0) {
            borderPaint.strokeMiter = strokeMiter.toFloat()
        }
    }

    fun setStrokeCap(strokeCap: Int) {
        this.strokeCap = strokeCap
        when (strokeCap) {
            STROKE_CAP_BUTT -> borderPaint.strokeCap = Paint.Cap.BUTT
            STROKE_CAP_ROUND -> borderPaint.strokeCap = Paint.Cap.ROUND
            STROKE_CAP_SQUARE -> borderPaint.strokeCap = Paint.Cap.SQUARE
            STROKE_CAP_DEFAULT -> { /* No-op */ }
        }
    }

    fun setStrokeJoin(strokeJoin: Int) {
        this.strokeJoin = strokeJoin
        when (strokeJoin) {
            STROKE_JOIN_BEVEL -> borderPaint.strokeJoin = Paint.Join.BEVEL
            STROKE_JOIN_MITER -> borderPaint.strokeJoin = Paint.Join.MITER
            STROKE_JOIN_ROUND -> borderPaint.strokeJoin = Paint.Join.ROUND
            STROKE_JOIN_DEFAULT -> { /* No-op */ }
        }
    }

    fun setBorderType(borderType: Int) {
        this.borderType = borderType
        when (borderType) {
            BORDER_TYPE_FILL -> borderPaint.style = Paint.Style.FILL
            BORDER_TYPE_DEFAULT -> borderPaint.style = Paint.Style.STROKE
            else -> borderPaint.style = Paint.Style.STROKE
        }
    }

    override fun draw(canvas: Canvas, imagePaint: Paint, borderPaint: Paint) {
        canvas.save()
        canvas.drawPath(borderPath, borderPaint)
        canvas.concat(matrix)
        canvas.drawPath(path, imagePaint)
        canvas.restore()
    }

    override fun calculate(
        bitmapWidth: Int,
        bitmapHeight: Int,
        width: Float,
        height: Float,
        scale: Float,
        translateX: Float,
        translateY: Float
    ) {
        path.reset()
        borderPath.reset()

        // Ensure shapePath is not null before proceeding
        val currentShapePath = shapePath ?: return 

        pathDimensions[0] = currentShapePath.width
        pathDimensions[1] = currentShapePath.height

        pathMatrix.reset()

        // Calculate main path scaling
        val pathScale = min(width / pathDimensions[0], height / pathDimensions[1])
        val pathTx = ((width - pathDimensions[0] * pathScale) * 0.5f).roundToInt().toFloat()
        val pathTy = ((height - pathDimensions[1] * pathScale) * 0.5f).roundToInt().toFloat()
        
        pathMatrix.setScale(pathScale, pathScale)
        pathMatrix.postTranslate(pathTx, pathTy)
        currentShapePath.transform(pathMatrix, path)
        
        // Adjust path for border width
        path.offset(borderWidth.toFloat(), borderWidth.toFloat())

        if (borderWidth > 0) {
            pathMatrix.reset()
            val newWidth: Float
            val newHeight: Float
            val d: Float
            
            if (borderType == BORDER_TYPE_DEFAULT) {
                newWidth = viewWidth - borderWidth.toFloat()
                newHeight = viewHeight - borderWidth.toFloat()
                d = borderWidth / 2f
            } else {
                newWidth = viewWidth.toFloat()
                newHeight = viewHeight.toFloat()
                d = 0f
            }

            val borderScale = min(newWidth / pathDimensions[0], newHeight / pathDimensions[1])
            val borderTx = ((newWidth - pathDimensions[0] * borderScale) * 0.5f + d).roundToInt().toFloat()
            val borderTy = ((newHeight - pathDimensions[1] * borderScale) * 0.5f + d).roundToInt().toFloat()
            
            pathMatrix.setScale(borderScale, borderScale)
            pathMatrix.postTranslate(borderTx, borderTy)
            currentShapePath.transform(pathMatrix, borderPath)
            // borderPath.op(path, Path.Op.DIFFERENCE); // Commented out as in original
        }

        pathMatrix.reset()
        matrix.invert(pathMatrix)
        path.transform(pathMatrix)
    }

    override fun reset() {
        path.reset()
        borderPath.reset()
    }
}
