package top.uwu.mikubox.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import top.uwu.mikubox.R

/** Bottom sheet for adding a subscription or importing a config from clipboard. */
class AddProfileBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onAddSubscription(url: String, name: String)
        fun onImportClipboard(name: String)
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.layout_add_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = view.findViewById<TextInputEditText>(R.id.et_sub_url)
        val name = view.findViewById<TextInputEditText>(R.id.et_name)

        view.findViewById<View>(R.id.btn_add_sub).setOnClickListener {
            listener?.onAddSubscription(
                url.text?.toString()?.trim().orEmpty(),
                name.text?.toString()?.trim().orEmpty(),
            )
            dismiss()
        }
        view.findViewById<View>(R.id.btn_import_clipboard).setOnClickListener {
            listener?.onImportClipboard(name.text?.toString()?.trim().orEmpty())
            dismiss()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "AddProfileBottomSheet"
    }
}
