package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.PointerIcon
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.TooltipCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.BaseProgressIndicator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class ServiceButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.extendedFloatingActionButtonStyle
) : ExtendedFloatingActionButton(context, attrs, defStyleAttr),
    DynamicAnimation.OnAnimationEndListener {

    private val callback = object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable) {
            super.onAnimationEnd(drawable)
            var next = animationQueue.peek() ?: return
            if (next.icon.current == drawable) {
                animationQueue.pop()
                next = animationQueue.peek() ?: return
            }
            next.start()
        }
    }

    private inner class AnimatedState(
        @DrawableRes resId: Int,
        private val onStart: BaseProgressIndicator<*>.() -> Unit = { hideProgress() }
    ) {
        val icon: AnimatedVectorDrawableCompat =
            AnimatedVectorDrawableCompat.create(context, resId)!!.apply {
                registerAnimationCallback(this@ServiceButton.callback)
            }

        fun start() {
            setIcon(icon)
            icon.start()
            progress?.onStart()
        }

        fun stop() = icon.stop()
    }

    private val iconStopped by lazy { AnimatedState(R.drawable.ic_service_stopped) }
    private val iconConnecting by lazy {
        AnimatedState(R.drawable.ic_service_connecting) {
            hideProgress()
            delayedAnimation = (context as? LifecycleOwner)?.lifecycleScope?.launchWhenStarted {
                delay(context.resources.getInteger(android.R.integer.config_mediumAnimTime) + 1000L)
                isIndeterminate = true
                show()
            }
        }
    }
    private val iconConnected by lazy {
        AnimatedState(R.drawable.ic_service_connected) {
            delayedAnimation?.cancel()
            setProgressCompat(1, true)
        }
    }
    private val iconStopping by lazy { AnimatedState(R.drawable.ic_service_stopping) }
    private val animationQueue = ArrayDeque<AnimatedState>()

    private var checked = false
    private var delayedAnimation: Job? = null
    private var timerJob: Job? = null
    private var connectedSinceElapsedMs: Long = 0L
    private var progress: BaseProgressIndicator<*>? = null

    init {
        shrink()
    }

    fun initProgress(progress: BaseProgressIndicator<*>) {
        this.progress = progress
        progress.progressDrawable?.addSpringAnimationEndListener(this)
    }

    override fun onAnimationEnd(
        animation: DynamicAnimation<out DynamicAnimation<*>>?, canceled: Boolean, value: Float,
        velocity: Float
    ) {
        if (!canceled) progress?.hide()
    }

    private fun hideProgress() {
        delayedAnimation?.cancel()
        progress?.hide()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (checked) View.mergeDrawableStates(
            drawableState,
            intArrayOf(android.R.attr.state_checked)
        )
        return drawableState
    }

    fun refreshTimerVisibility() {
        if (checked && DataStore.showFabTimer) {
            startTimer()
        } else {
            stopTimer()
        }
    }

    private fun startTimer() {
        if (connectedSinceElapsedMs == 0L) {
            connectedSinceElapsedMs = SystemClock.elapsedRealtime()
        }
        if (timerJob?.isActive == true) {
            extend()
            return
        }
        val owner = context as? LifecycleOwner ?: return
        timerJob = owner.lifecycleScope.launch {
            extend()
            while (isActive) {
                val elapsedSec = ((SystemClock.elapsedRealtime() - connectedSinceElapsedMs) / 1000L)
                    .coerceAtLeast(0L)
                val hours = elapsedSec / 3600L
                val minutes = (elapsedSec % 3600L) / 60L
                val seconds = elapsedSec % 60L
                text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000L)
            }
        }
    }

    private fun stopTimer(resetTimestamp: Boolean = false) {
        timerJob?.cancel()
        timerJob = null
        if (resetTimestamp) {
            connectedSinceElapsedMs = 0L
        }
        text = ""
        shrink()
    }

    override fun onDetachedFromWindow() {
        timerJob?.cancel()
        timerJob = null
        super.onDetachedFromWindow()
    }

    fun changeState(state: BaseService.State, previousState: BaseService.State, animate: Boolean) {
        when (state) {
            BaseService.State.Connecting -> {
                stopTimer(resetTimestamp = true)
                changeState(iconConnecting, animate)
            }
            BaseService.State.Connected -> {
                if (connectedSinceElapsedMs == 0L) {
                    connectedSinceElapsedMs = SystemClock.elapsedRealtime()
                }
                changeState(iconConnected, animate)
            }
            BaseService.State.Stopping -> {
                stopTimer(resetTimestamp = true)
                changeState(iconStopping, animate && previousState == BaseService.State.Connected)
            }
            else -> {
                stopTimer(resetTimestamp = true)
                changeState(iconStopped, animate)
            }
        }
        checked = state == BaseService.State.Connected
        if (checked && DataStore.showFabTimer) {
            startTimer()
        } else {
            stopTimer(resetTimestamp = !checked)
        }
        refreshDrawableState()
        val description = context.getText(if (state.canStop) R.string.stop else R.string.connect)
        contentDescription = description
        TooltipCompat.setTooltipText(this, description)
        val enabled = state.canStop || state == BaseService.State.Stopped ||
            state == BaseService.State.Idle
        isEnabled = enabled
        if (Build.VERSION.SDK_INT >= 24) pointerIcon = PointerIcon.getSystemIcon(
            context,
            if (enabled) PointerIcon.TYPE_HAND else PointerIcon.TYPE_WAIT
        )
    }

    private fun changeState(icon: AnimatedState, animate: Boolean) {
        fun counters(a: AnimatedState, b: AnimatedState): Boolean =
            a == iconStopped && b == iconConnecting ||
                    a == iconConnecting && b == iconStopped ||
                    a == iconConnected && b == iconStopping ||
                    a == iconStopping && b == iconConnected
        if (animate) {
            if (animationQueue.size < 2 || !counters(animationQueue.last, icon)) {
                animationQueue.add(icon)
                if (animationQueue.size == 1) icon.start()
            } else animationQueue.removeLast()
        } else {
            animationQueue.peekFirst()?.stop()
            animationQueue.clear()
            icon.start()
            icon.stop()
        }
    }
}
