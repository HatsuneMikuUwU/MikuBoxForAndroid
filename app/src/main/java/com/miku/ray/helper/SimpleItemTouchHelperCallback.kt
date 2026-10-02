package com.miku.ray.helper

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

class SimpleItemTouchHelperCallback(private val mAdapter: ItemTouchHelperAdapter, private val allowSwipe: Boolean = false) : ItemTouchHelper.Callback() {
    private var mReturnAnimator: ValueAnimator? = null

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = allowSwipe

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        val swipeFlags = if (allowSwipe) ItemTouchHelper.START or ItemTouchHelper.END else 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return if (source.itemViewType != target.itemViewType) {
            false
        } else {
            mAdapter.onItemMove(source.bindingAdapterPosition, target.bindingAdapterPosition)
            true
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        returnViewToOriginalPosition(viewHolder)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val maxSwipeDistance = viewHolder.itemView.width * SWIPE_THRESHOLD
            val swipeAmount = abs(dX)
            val direction = sign(dX)

            val translationX = min(swipeAmount, maxSwipeDistance) * direction
            val alpha = ALPHA_FULL - min(swipeAmount, maxSwipeDistance) / maxSwipeDistance

            viewHolder.itemView.translationX = translationX
            viewHolder.itemView.alpha = alpha

            if (swipeAmount >= maxSwipeDistance && isCurrentlyActive) {
                returnViewToOriginalPosition(viewHolder)
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private fun returnViewToOriginalPosition(viewHolder: RecyclerView.ViewHolder) {
        mReturnAnimator?.takeIf { it.isRunning }?.cancel()

        mReturnAnimator = ValueAnimator.ofFloat(viewHolder.itemView.translationX, 0f).apply {
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                viewHolder.itemView.translationX = value
                viewHolder.itemView.alpha = 1f - abs(value) / (viewHolder.itemView.width * SWIPE_THRESHOLD)
            }
            interpolator = DecelerateInterpolator()
            duration = ANIMATION_DURATION
            start()
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder is ItemTouchHelperViewHolder) {
            viewHolder.onItemSelected()
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = ALPHA_FULL
        if (viewHolder is ItemTouchHelperViewHolder) {
            viewHolder.onItemClear()
        }
        mAdapter.onItemMoveCompleted()
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return 1.1f
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 10
    }

    companion object {
        private const val ALPHA_FULL = 1.0f
        private const val SWIPE_THRESHOLD = 0.25f
        private const val ANIMATION_DURATION: Long = 200
    }
}
