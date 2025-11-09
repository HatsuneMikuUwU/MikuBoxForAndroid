package io.nekohasekai.sagernet.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R

class ProfileMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnOptionClickListener {
        fun onOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment is OnOptionClickListener) {
            mListener = parentFragment as OnOptionClickListener
        } else {
            throw RuntimeException("$parentFragment must implement OnOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_import_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val clickListener = View.OnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.action_scan_qr_code,
            R.id.action_import_clipboard,
            R.id.action_import_file,
            R.id.action_new_socks,
            R.id.action_new_http,
            R.id.action_new_ss,
            R.id.action_new_vmess,
            R.id.action_new_vless,
            R.id.action_new_trojan,
            R.id.action_new_trojan_go,
            R.id.action_new_mieru,
            R.id.action_new_naive,
            R.id.action_new_hysteria,
            R.id.action_new_tuic,
            R.id.action_new_juicity,
            R.id.action_new_ssh,
            R.id.action_new_wg,
            R.id.action_new_shadowtls,
            R.id.action_new_anytls,
            R.id.action_new_config,
            R.id.action_new_chain
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
        const val TAG = "ProfileMenuBottomSheet"
    }
}
