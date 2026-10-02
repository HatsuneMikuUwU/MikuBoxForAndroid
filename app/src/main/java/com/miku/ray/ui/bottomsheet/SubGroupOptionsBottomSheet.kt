package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.R

class SubGroupOptionsBottomSheet : BaseBottomSheetFragment() {

    interface OnSubGroupOptionClickListener {
        fun onSubGroupOptionClicked(viewId: Int, subId: String)
    }

    private var mListener: OnSubGroupOptionClickListener? = null
    private var subId: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSubGroupOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnSubGroupOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subId = arguments?.getString(ARG_SUB_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_sub_group_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val clickListener = View.OnClickListener {
            mListener?.onSubGroupOptionClicked(it.id, subId)
            dismiss()
        }

        view.findViewById<View>(R.id.clear_group_traffic)?.setOnClickListener(clickListener)
        view.findViewById<View>(R.id.remove_group)?.setOnClickListener(clickListener)
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "SubGroupOptionsBottomSheet"
        private const val ARG_SUB_ID = "arg_sub_id"

        fun newInstance(subId: String): SubGroupOptionsBottomSheet {
            return SubGroupOptionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUB_ID, subId)
                }
            }
        }
    }
}
