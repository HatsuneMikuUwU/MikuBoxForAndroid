package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.handler.MmkvManager

class ShareConfigBottomSheet : BaseBottomSheetFragment() {

    interface OnShareOptionClickListener {
        fun onShareOptionClicked(optionId: Int, guid: String)
    }

    private var mListener: OnShareOptionClickListener? = null
    private var configGuid: String = ""
    private var configType: Int = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnShareOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnShareOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configGuid = arguments?.getString(ARG_GUID) ?: ""
        configType = arguments?.getInt(ARG_CONFIG_TYPE) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_share_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val clickListener = View.OnClickListener {
            mListener?.onShareOptionClicked(it.id, configGuid)
            dismiss()
        }

        view.findViewById<View>(R.id.share_qrcode)?.setOnClickListener(clickListener)

        val shareClipboardView = view.findViewById<View>(R.id.share_clipboard)
        shareClipboardView?.setOnClickListener(clickListener)

        view.findViewById<View>(R.id.share_full_clipboard)?.setOnClickListener(clickListener)

        view.findViewById<View>(R.id.share_file)?.setOnClickListener(clickListener)

        val typeEnum = EConfigType.fromInt(configType)
        val isCustomConfig = typeEnum?.isComplexType() == true

        if (isCustomConfig) {
            shareClipboardView?.visibility = View.GONE
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "ShareConfigBottomSheet"
        private const val ARG_GUID = "arg_guid"
        private const val ARG_CONFIG_TYPE = "arg_config_type"

        fun newInstance(guid: String, configType: Int): ShareConfigBottomSheet {
            return ShareConfigBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_GUID, guid)
                    putInt(ARG_CONFIG_TYPE, configType)
                }
            }
        }
    }
}
