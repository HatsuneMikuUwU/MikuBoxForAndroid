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
        return inflater.inflate(R.layout.bottom_sheet_import_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.action_scan_qr_code).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        view.findViewById<View>(R.id.action_import_clipboard).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        view.findViewById<View>(R.id.action_import_file).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_socks).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_http).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_ss).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_vmess).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_vless).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_trojan).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_trojan_go).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_mieru).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_naive).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_hysteria).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_tuic).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_juicity).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_ssh).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_wg).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_shadowtls).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_anytls).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_config).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }
        
        view.findViewById<View>(R.id.action_new_chain).setOnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
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
