package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.GroupOrder
import top.nkbe.mikubox.ui.component.clearDefaultSheetBackground
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.sheets.OtherMenuSheetContent

class OtherMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOtherOptionClickListener {
        fun onOtherOptionClicked(viewId: Int)
    }

    private var mListener: OnOtherOptionClickListener? = null
    private var currentOrder: Int = GroupOrder.ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = when {
            parentFragment is OnOtherOptionClickListener -> parentFragment as OnOtherOptionClickListener
            context is OnOtherOptionClickListener -> context
            else -> throw RuntimeException("$context must implement OnOtherOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrder = arguments?.getInt(ARG_CURRENT_ORDER) ?: GroupOrder.ORIGIN
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = mikuComposeView {
        OtherMenuSheetContent(
            currentOrder = currentOrder,
            onActionClick = { id ->
                mListener?.onOtherOptionClicked(id)
                dismiss()
            },
        )
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.clearDefaultSheetBackground()
            sheetDialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
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
            return OtherMenuBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CURRENT_ORDER, currentOrder)
                }
            }
        }
    }
}
