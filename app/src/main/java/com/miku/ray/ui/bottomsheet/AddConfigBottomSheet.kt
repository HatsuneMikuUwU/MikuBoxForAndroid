package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class AddConfigBottomSheet : BaseBottomSheetFragment() {

    interface OnAddConfigClickListener {
        fun onAddConfigOptionClicked(viewId: Int)
    }

    private var mListener: OnAddConfigClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAddConfigClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnAddConfigClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_layout_bottom_sheet_add_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupParticles(view)
        loadBannerSheet(view)

        val clickListener = View.OnClickListener {
            mListener?.onAddConfigOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.import_qrcode,
            R.id.import_clipboard,
            R.id.import_local,
            R.id.import_manually_policy_group,
            R.id.import_manually_proxy_chain,
            R.id.import_manually_vmess,
            R.id.import_manually_vless,
            R.id.import_manually_ss,
            R.id.import_manually_socks,
            R.id.import_manually_http,
            R.id.import_manually_trojan,
            R.id.import_manually_wireguard,
            R.id.import_manually_hysteria2
        )

        actionIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "AddConfigBottomSheet"
    }
}
