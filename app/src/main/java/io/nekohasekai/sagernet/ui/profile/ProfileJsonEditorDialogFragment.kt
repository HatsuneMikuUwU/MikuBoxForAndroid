package io.nekohasekai.sagernet.ui.profile

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import com.blacksquircle.ui.editorkit.insert
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutEditConfigBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.utils.showBlur
import moe.matsuri.nb4a.ui.ExtendedKeyboard
import org.json.JSONObject

class ProfileJsonEditorDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutEditConfigBinding
    private var dirty = false
    private lateinit var storeKey: String
    private var titleRes: Int = 0
    var onSaved: ((String) -> Unit)? = null

    companion object {
        private const val ARG_STORE_KEY = "store_key"
        private const val ARG_TITLE_RES = "title_res"

        fun newInstance(storeKey: String, titleRes: Int): ProfileJsonEditorDialogFragment {
            return ProfileJsonEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STORE_KEY, storeKey)
                    putInt(ARG_TITLE_RES, titleRes)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storeKey = it.getString(ARG_STORE_KEY) ?: Key.SERVER_CUSTOM
            titleRes = it.getInt(ARG_TITLE_RES)
        }
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = LayoutEditConfigBinding.inflate(LayoutInflater.from(requireContext()))
        bindEditor()

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.Theme_SagerNet_Dialog
        ).setView(binding.root).create()

        dialog.setOnKeyListener { _: DialogInterface, keyCode: Int, event: KeyEvent ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (dirty) {
                    showClosePrompt()
                } else {
                    dismissAllowingStateLoss()
                }
                true
            } else {
                false
            }
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun bindEditor() {
        // layout_edit_config includes layout_appbar without an id, so the toolbar
        // isn't exposed on the binding.
        val toolbar = binding.root.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(titleRes)
        toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
        toolbar.setNavigationOnClickListener {
            if (dirty) {
                showClosePrompt()
            } else {
                dismissAllowingStateLoss()
            }
        }
        toolbar.inflateMenu(R.menu.profile_apply_menu)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_apply -> {
                    saveAndClose()
                    true
                }
                else -> false
            }
        }

        binding.editor.apply {
            language = JsonLanguage()
            setHorizontallyScrolling(true)
            setTextContent(readCurrentText())
            addTextChangedListener {
                if (!dirty) {
                    dirty = true
                    DataStore.dirty = true
                }
            }
        }

        binding.actionTab.setOnClickListener {
            try {
                binding.editor.insert(binding.editor.tab())
            } catch (_: Exception) {
            }
        }
        binding.actionUndo.setOnClickListener {
            try {
                binding.editor.undo()
            } catch (_: Exception) {
            }
        }
        binding.actionRedo.setOnClickListener {
            try {
                binding.editor.redo()
            } catch (_: Exception) {
            }
        }
        binding.actionFormat.setOnClickListener {
            formatText()?.let {
                binding.editor.setTextContent(it)
            }
        }

        val extendedKeyboard = binding.root.findViewById<ExtendedKeyboard>(R.id.extended_keyboard)
        extendedKeyboard.setKeyListener { char ->
            try {
                binding.editor.insert(char)
            } catch (_: Exception) {
            }
        }
        extendedKeyboard.setHasFixedSize(true)
        extendedKeyboard.submitList("{},:_\"".map { it.toString() })
        extendedKeyboard.setBackgroundColor(requireContext().getColorAttr(R.attr.primaryOrTextPrimary))

        ViewCompat.setOnApplyWindowInsetsListener(binding.keyboardContainer) { v, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            v.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = if (imeVisible) {
                    imeInsets.bottom - systemBarInsets.bottom
                } else {
                    0
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun readCurrentText(): String {
        return when (storeKey) {
            Key.SERVER_CUSTOM_OUTBOUND -> DataStore.serverCustomOutbound
            else -> DataStore.serverCustom
        }
    }

    private fun writeCurrentText(text: String) {
        when (storeKey) {
            Key.SERVER_CUSTOM_OUTBOUND -> DataStore.serverCustomOutbound = text
            else -> DataStore.serverCustom = text
        }
    }

    private fun formatText(): String? {
        return try {
            val txt = binding.editor.text.toString()
            if (txt.isBlank()) {
                ""
            } else {
                JSONObject(txt).toStringPretty()
            }
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error_title)
                .setMessage(e.readableMessage)
                .showBlur()
            null
        }
    }

    private fun saveAndClose() {
        formatText()?.let { formatted ->
            writeCurrentText(formatted)
            onSaved?.invoke(formatted)
            dismissAllowingStateLoss()
        }
    }

    private fun showClosePrompt() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.unsaved_changes_prompt)
            .setPositiveButton(R.string.yes) { _, _ ->
                saveAndClose()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                dismissAllowingStateLoss()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .showBlur()
    }
}
