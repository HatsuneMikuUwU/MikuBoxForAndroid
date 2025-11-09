package io.nekohasekai.sagernet.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckedTextView
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R


private const val ANIMATION_DURATION = 300L

private fun View.expand() {
    measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    val targetHeight = measuredHeight

    layoutParams.height = 1
    visibility = View.VISIBLE

    val valueAnimator = ValueAnimator.ofInt(1, targetHeight)
    valueAnimator.addUpdateListener { animator ->
        layoutParams.height = animator.animatedValue as Int
        requestLayout()
    }

    valueAnimator.interpolator = DecelerateInterpolator()
    valueAnimator.duration = ANIMATION_DURATION
    valueAnimator.start()
}

private fun View.collapse() {
    val initialHeight = measuredHeight

    val valueAnimator = ValueAnimator.ofInt(initialHeight, 0)
    valueAnimator.addUpdateListener { animator ->
        layoutParams.height = animator.animatedValue as Int
        requestLayout()
    }

    valueAnimator.interpolator = DecelerateInterpolator()
    valueAnimator.duration = ANIMATION_DURATION
    valueAnimator.start()

    valueAnimator.addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            visibility = View.GONE
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT // Reset height
        }
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
}

private fun View.animateRotation(endDegree: Float) {
    this.animate()
        .rotation(endDegree)
        .setDuration(ANIMATION_DURATION)
        .start()
}

class OtherMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOtherOptionClickListener {
        fun onOtherOptionClicked(viewId: Int)
    }

    private var mListener: OnOtherOptionClickListener? = null
    private var currentOrder: Int = GroupOrder.ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment is OnOtherOptionClickListener) {
            mListener = parentFragment as OnOtherOptionClickListener
        } else {
            mListener = try {
                context as OnOtherOptionClickListener
            } catch (e: ClassCastException) {
                throw RuntimeException("$context must implement OnOtherOptionClickListener")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrder = arguments?.getInt(ARG_CURRENT_ORDER) ?: GroupOrder.ORIGIN
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_other_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val checkOrigin = view.findViewById<CheckedTextView>(R.id.action_order_origin)
        val checkName = view.findViewById<CheckedTextView>(R.id.action_order_by_name)
        val checkDelay = view.findViewById<CheckedTextView>(R.id.action_order_by_delay)

        when (currentOrder) {
            GroupOrder.ORIGIN -> checkOrigin?.isChecked = true
            GroupOrder.BY_NAME -> checkName?.isChecked = true
            GroupOrder.BY_DELAY -> checkDelay?.isChecked = true
        }
        
        setupExpandable(
            toggleHeader = view.findViewById(R.id.action_group_setting_1),
            expandableContent = view.findViewById(R.id.expandable_setting_content_1),
            arrowIcon = view.findViewById(R.id.arrow_icon_1)
        )
        
        setupExpandable(
            toggleHeader = view.findViewById(R.id.action_group_setting_2),
            expandableContent = view.findViewById(R.id.expandable_setting_content_2),
            arrowIcon = view.findViewById(R.id.arrow_icon_2)
        )

        val clickListener = View.OnClickListener {
            mListener?.onOtherOptionClicked(it.id)
            dismiss()
        }

        val viewIds = listOf(
            R.id.action_update_subscription,
            R.id.action_clear_traffic_statistics,
            R.id.action_connection_test_clear_results,
            R.id.action_connection_test_delete_unavailable,
            R.id.action_remove_duplicate,
            R.id.action_connection_tcp_ping,
            R.id.action_connection_url_test,
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay
        )

        viewIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    private fun setupExpandable(toggleHeader: View?, expandableContent: View?, arrowIcon: ImageView?) {
        if (toggleHeader != null && expandableContent != null) {
            
            if (expandableContent.visibility == View.VISIBLE) {
                arrowIcon?.rotation = 90f
                expandableContent.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                arrowIcon?.rotation = 0f
            }

            toggleHeader.setOnClickListener {
                if (expandableContent.visibility == View.GONE) {
                    
                    expandableContent.visibility = View.VISIBLE
                    
                    expandableContent.post { 
                        expandableContent.expand()
                        arrowIcon?.animateRotation(90f)
                    }
                    
                } else {
                    expandableContent.collapse()
                    arrowIcon?.animateRotation(0f)
                }
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "OtherMenuBottomSheet"
        
        private const val ARG_CURRENT_ORDER = "current_order"

        fun newInstance(currentOrder: Int): OtherMenuBottomSheet {
            val fragment = OtherMenuBottomSheet()
            val args = Bundle()
            args.putInt(ARG_CURRENT_ORDER, currentOrder)
            fragment.arguments = args
            return fragment
        }
    }
}
