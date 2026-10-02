package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class ShareSubBottomSheet : BaseBottomSheetFragment() {

    interface OnShareSubOptionClickListener {
        fun onShareSubOptionClicked(optionId: Int, url: String)
    }

    private var mListener: OnShareSubOptionClickListener? = null
    private var subUrl: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnShareSubOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnShareSubOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subUrl = arguments?.getString(ARG_URL) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_share_sub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val clickListener = View.OnClickListener {
            mListener?.onShareSubOptionClicked(it.id, subUrl)
            dismiss()
        }

        view.findViewById<View>(R.id.share_qrcode)?.setOnClickListener(clickListener)
        view.findViewById<View>(R.id.share_clipboard)?.setOnClickListener(clickListener)
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "ShareSubBottomSheet"
        private const val ARG_URL = "arg_url"

        fun newInstance(url: String): ShareSubBottomSheet {
            return ShareSubBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
