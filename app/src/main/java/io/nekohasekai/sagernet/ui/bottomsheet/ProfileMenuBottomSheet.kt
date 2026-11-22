package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import top.nkbe.mikubox.ui.component.clearDefaultSheetBackground
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.sheets.ProfileMenuSheetContent

class ProfileMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOptionClickListener {
        fun onOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = when {
            parentFragment is OnOptionClickListener -> parentFragment as OnOptionClickListener
            context is OnOptionClickListener -> context
            else -> throw RuntimeException("$parentFragment must implement OnOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = mikuComposeView {
        ProfileMenuSheetContent(
            onActionClick = { id ->
                mListener?.onOptionClicked(id)
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
        const val TAG = "ProfileMenuBottomSheet"
    }
}
