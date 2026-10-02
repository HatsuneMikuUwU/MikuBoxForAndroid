package com.miku.ray.expandable.layout

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import androidx.core.view.children
import com.miku.ray.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExpandableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        object State {
            const val COLLAPSED = 0
            const val COLLAPSING = 1
            const val EXPANDING = 2
            const val EXPANDED = 3
        }

        const val KEY_SUPER_STATE = "super_state"
        const val KEY_EXPANSION = "expansion"

        const val HORIZONTAL = 0
        const val VERTICAL = 1

        private const val DEFAULT_DURATION = 1000

        private val ff = floatArrayOf(
            0.0f, 0.0001f, 0.0002f, 0.0005f, 0.0009f, 0.0014f, 0.002f, 0.0027f, 0.0036f, 0.0046f, 0.0058f, 0.0071f,
            0.0085f, 0.0101f, 0.0118f, 0.0137f, 0.0158f, 0.018f, 0.0205f, 0.0231f, 0.0259f, 0.0289f, 0.0321f,
            0.0355f, 0.0391f, 0.043f, 0.0471f, 0.0514f, 0.056f, 0.0608f, 0.066f, 0.0714f, 0.0771f, 0.083f,
            0.0893f, 0.0959f, 0.1029f, 0.1101f, 0.1177f, 0.1257f, 0.1339f, 0.1426f, 0.1516f, 0.161f, 0.1707f,
            0.1808f, 0.1913f, 0.2021f, 0.2133f, 0.2248f, 0.2366f, 0.2487f, 0.2611f, 0.2738f, 0.2867f, 0.2998f,
            0.3131f, 0.3265f, 0.34f, 0.3536f, 0.3673f, 0.381f, 0.3946f, 0.4082f, 0.4217f, 0.4352f, 0.4485f,
            0.4616f, 0.4746f, 0.4874f, 0.5f, 0.5124f, 0.5246f, 0.5365f, 0.5482f, 0.5597f, 0.571f, 0.582f,
            0.5928f, 0.6033f, 0.6136f, 0.6237f, 0.6335f, 0.6431f, 0.6525f, 0.6616f, 0.6706f, 0.6793f, 0.6878f,
            0.6961f, 0.7043f, 0.7122f, 0.7199f, 0.7275f, 0.7349f, 0.7421f, 0.7491f, 0.7559f, 0.7626f, 0.7692f,
            0.7756f, 0.7818f, 0.7879f, 0.7938f, 0.7996f, 0.8053f, 0.8108f, 0.8162f, 0.8215f, 0.8266f, 0.8317f,
            0.8366f, 0.8414f, 0.8461f, 0.8507f, 0.8551f, 0.8595f, 0.8638f, 0.8679f, 0.872f, 0.876f, 0.8798f,
            0.8836f, 0.8873f, 0.8909f, 0.8945f, 0.8979f, 0.9013f, 0.9046f, 0.9078f, 0.9109f, 0.9139f, 0.9169f,
            0.9198f, 0.9227f, 0.9254f, 0.9281f, 0.9307f, 0.9333f, 0.9358f, 0.9382f, 0.9406f, 0.9429f, 0.9452f,
            0.9474f, 0.9495f, 0.9516f, 0.9536f, 0.9556f, 0.9575f, 0.9594f, 0.9612f, 0.9629f, 0.9646f, 0.9663f,
            0.9679f, 0.9695f, 0.971f, 0.9725f, 0.9739f, 0.9753f, 0.9766f, 0.9779f, 0.9791f, 0.9803f, 0.9815f,
            0.9826f, 0.9837f, 0.9848f, 0.9858f, 0.9867f, 0.9877f, 0.9885f, 0.9894f, 0.9902f, 0.991f, 0.9917f,
            0.9924f, 0.9931f, 0.9937f, 0.9944f, 0.9949f, 0.9955f, 0.996f, 0.9964f, 0.9969f, 0.9973f, 0.9977f,
            0.998f, 0.9984f, 0.9986f, 0.9989f, 0.9991f, 0.9993f, 0.9995f, 0.9997f, 0.9998f, 0.9999f, 0.9999f,
            1.0f, 1.0f
        )
    }

    var duration: Int = DEFAULT_DURATION

    var parallax: Float = 1f
    set(value) {
        field = min(1f, max(0f, value))
    }

    var expansion: Float = 0f
    set(value) {
        if (field == value) return

        val delta = value - field
        when {
            value == 0f -> state = State.COLLAPSED
            value == 1f -> state = State.EXPANDED
            delta < 0 -> state = State.COLLAPSING
            delta > 0 -> state = State.EXPANDING
        }

        visibility = if (state == State.COLLAPSED) View.GONE else View.VISIBLE
        field = value
        requestLayout()

        listener?.onExpansionUpdate(value, state)
    }

    var orientation: Int = VERTICAL
    set(value) {
        require(value in 0..1) { "Orientation must be either 0 (horizontal) or 1 (vertical)" }
        field = value
    }

    var state: Int = State.COLLAPSED
    private set

    var interpolator: Interpolator = FastOutSlowInInterpolator(ff)

    private var animator: ValueAnimator? = null
    private var listener: OnExpansionUpdateListener? = null

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.ExpandableView)
            duration = a.getInt(R.styleable.ExpandableView_el_duration, DEFAULT_DURATION)
            val isExpanded = a.getBoolean(R.styleable.ExpandableView_el_expanded, false)
            expansion = if (isExpanded) 1f else 0f
            state = if (isExpanded) State.EXPANDED else State.COLLAPSED
            orientation = a.getInt(R.styleable.ExpandableView_el_orientation, VERTICAL)
            parallax = a.getFloat(R.styleable.ExpandableView_el_parallax, 1f)
            a.recycle()
        }
        visibility = if (expansion == 0f) View.GONE else View.VISIBLE
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val bundle = Bundle()
        val currentExpansion = if (isExpanded) 1f else 0f
        bundle.putFloat(KEY_EXPANSION, currentExpansion)
        bundle.putParcelable(KEY_SUPER_STATE, superState)
        return bundle
    }

    override fun onRestoreInstanceState(parcelable: Parcelable?) {
        if (parcelable is Bundle) {
            expansion = parcelable.getFloat(KEY_EXPANSION)
            state = if (expansion == 1f) State.EXPANDED else State.COLLAPSED
            val superState: Parcelable? = BundleCompat.getParcelable(parcelable, KEY_SUPER_STATE, Parcelable::class.java)
            super.onRestoreInstanceState(superState)
        } else {
            super.onRestoreInstanceState(parcelable)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        val size = if (orientation == LinearLayout.HORIZONTAL) width else height
        visibility = if (expansion == 0f && size == 0) View.GONE else View.VISIBLE
        val expansionDelta = size - (size * expansion).roundToInt()
        if (parallax > 0) {
            val parallaxDelta = expansionDelta * parallax
            for (child in children) {
                if (orientation == HORIZONTAL) {
                    val direction = -1
                    child.translationX = direction * parallaxDelta
                } else {
                    child.translationY = -parallaxDelta
                }
            }
        }

        if (orientation == HORIZONTAL) {
            setMeasuredDimension(width - expansionDelta, height)
        } else {
            setMeasuredDimension(width, height - expansionDelta)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        animator?.cancel()
        super.onConfigurationChanged(newConfig)
    }

    val isExpanded: Boolean
    get() = state == State.EXPANDING || state == State.EXPANDED

    fun toggle(animate: Boolean = true) {
        if (isExpanded) {
            collapse(animate)
        } else {
            expand(animate)
        }
    }

    fun expand(animate: Boolean = true) {
        setExpanded(true, animate)
    }

    fun collapse(animate: Boolean = true) {
        setExpanded(false, animate)
    }

    fun setExpanded(expand: Boolean, animate: Boolean = true) {
        if (expand == isExpanded) {
            return
        }

        val targetExpansion = if (expand) 1 else 0
        if (animate) {
            animateSize(targetExpansion)
        } else {
            setExpansion(expand)
        }
    }

    fun setExpansion(isExpanded: Boolean) {
        if (isExpanded) {
            state = State.EXPANDED
            this.expansion = 1f
        } else {
            state = State.COLLAPSED
            this.expansion = 0f
        }
    }

    fun setOnExpansionUpdateListener(listener: OnExpansionUpdateListener?) {
        this.listener = listener
    }

    private fun animateSize(targetExpansion: Int) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(expansion, targetExpansion.toFloat()).apply {
            interpolator = this@ExpandableView.interpolator
            duration = this@ExpandableView.duration.toLong()
            addUpdateListener { valueAnimator ->
                expansion = valueAnimator.animatedValue as Float
            }
            addListener(ExpansionListener(targetExpansion))
            start()
        }
    }

    fun interface OnExpansionUpdateListener {
        fun onExpansionUpdate(expansionFraction: Float, state: Int)
    }

    private inner class ExpansionListener(private val targetExpansion: Int) : Animator.AnimatorListener {
        private var canceled: Boolean = false

        override fun onAnimationStart(animation: Animator) {
            state = if (targetExpansion == 0) State.COLLAPSING else State.EXPANDING
        }

        override fun onAnimationEnd(animation: Animator) {
            if (!canceled) {
                state = if (targetExpansion == 0) State.COLLAPSED else State.EXPANDED
                expansion = targetExpansion.toFloat()
            }
        }

        override fun onAnimationCancel(animation: Animator) {
            canceled = true
        }

        override fun onAnimationRepeat(animation: Animator) {}
    }

    class FastOutSlowInInterpolator(values: FloatArray) : LookupTableInterpolator(values)

    abstract class LookupTableInterpolator(private val mValues: FloatArray) : Interpolator {
        private val mStepSize: Float = 1f / (mValues.size - 1)

        override fun getInterpolation(input: Float): Float {
            if (input >= 1.0f) return 1.0f
            if (input <= 0f) return 0f
            val position = min((input * (mValues.size - 1)).toInt(), mValues.size - 2)
            val quantized = position * mStepSize
            val diff = input - quantized
            val weight = diff / mStepSize
            return mValues[position] + weight * (mValues[position + 1] - mValues[position])
        }
    }
}
