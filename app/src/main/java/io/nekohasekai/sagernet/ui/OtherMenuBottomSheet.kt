package io.nekohasekai.sagernet.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R


private const val TRANSITION_DURATION = 300L

private fun View.toggleWithTransition(parentView: ViewGroup, isExpanding: Boolean) {
    
    TransitionManager.beginDelayedTransition(parentView, AutoTransition().setDuration(TRANSITION_DURATION))

    this.visibility = if (isExpanding) View.VISIBLE else View.GONE
}

private fun View.animateRotation(endDegree: Float) {
    this.animate()
        .rotation(endDegree)
        .setDuration(TRANSITION_DURATION)
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
        
        val menuContainerParent = view.findViewById<ViewGroup>(R.id.menu_container_parent) ?: view as ViewGroup

        setupExpandable(
            parent = menuContainerParent,
            toggleHeader = view.findViewById(R.id.action_group_setting_1),
            expandableContent = view.findViewById(R.id.expandable_setting_content_1),
            arrowIcon = view.findViewById(R.id.arrow_icon_1)
        )
        
        setupExpandable(
            parent = menuContainerParent,
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

    private fun setupExpandable(parent: ViewGroup, toggleHeader: View?, expandableContent: View?, arrowIcon: ImageView?) {
        if (toggleHeader != null && expandableContent != null) {
            
            if (arrowIcon != null) {
                arrowIcon.rotation = if (expandableContent.visibility == View.VISIBLE) 90f else 0f
            }
            
            toggleHeader.setOnClickListener {
                val isExpanding = expandableContent.visibility == View.GONE

                if (isExpanding) {
                    expandableContent.toggleWithTransition(parent, true)
                    arrowIcon?.animateRotation(90f)
                } else {
                    expandableContent.toggleWithTransition(parent, false)
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
