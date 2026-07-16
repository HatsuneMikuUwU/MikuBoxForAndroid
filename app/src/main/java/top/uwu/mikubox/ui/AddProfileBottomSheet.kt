package top.uwu.mikubox.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import top.uwu.mikubox.R

/** Bottom sheet for adding a subscription or importing a config from clipboard. */
class AddProfileBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onAddSubscription(url: String, name: String, intervalMinutes: Long, connectedOnly: Boolean)
        fun onImportClipboard(name: String)
    }

    private var listener: Listener? = null

    /** Preset auto-update intervals in minutes, matched by index to R.array.subscription_intervals. */
    private val intervalMinutes = longArrayOf(360, 720, 1440, 4320, 10080)
    private val defaultIntervalIndex = 2

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
        val interval = view.findViewById<MaterialAutoCompleteTextView>(R.id.dropdown_interval)
        val connectedOnly = view.findViewById<MaterialSwitch>(R.id.switch_connected_only)

        val labels = resources.getStringArray(R.array.subscription_intervals)
        interval.setSimpleItems(labels)
        interval.setText(labels[defaultIntervalIndex], false)
        var selectedIndex = defaultIntervalIndex
        interval.setOnItemClickListener { _, _, position, _ -> selectedIndex = position }

        view.findViewById<View>(R.id.btn_add_sub).setOnClickListener {
            listener?.onAddSubscription(
                url.text?.toString()?.trim().orEmpty(),
                name.text?.toString()?.trim().orEmpty(),
                intervalMinutes[selectedIndex],
                connectedOnly.isChecked,
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
