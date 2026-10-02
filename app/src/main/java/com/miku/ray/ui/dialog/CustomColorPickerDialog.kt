package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.miku.ray.R
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.WindowBlurUtils

class CustomColorPickerDialog : DialogFragment() {

    companion object {
        const val TAG = "CustomColorPickerDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            currentColor: Int = Color.parseColor("#6750A4"),
            onApplied: () -> Unit = {},
        ) {
            CustomColorPickerDialog().apply {
                arguments = Bundle().apply { putInt("current_color", currentColor) }
                onAppliedCallback = onApplied
            }.show(fragmentManager, TAG)
        }
    }

    var onAppliedCallback: () -> Unit = {}
    private var selectedColor: Int = Color.parseColor("#6750A4")

    override fun onStart() {
        super.onStart()
        WindowBlurUtils.applyWindowBlur(dialog?.window)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val initialColor = arguments?.getInt("current_color") ?: selectedColor
        selectedColor = initialColor

        val view = layoutInflater.inflate(R.layout.dialog_custom_color_picker, null)

        val colorPickerView = view.findViewById<ColorPickerView>(R.id.color_picker_view)

        colorPickerView.post {
            colorPickerView.selectByHsvColor(initialColor)
        }

        colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
                selectedColor = envelope.color
        })

        return MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.pref_custom_color_title)
        .setIcon(RemixR.drawable.rmx_palette_line)
        .setView(view)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            activity?.let { ThemeManager.saveCustomColor(it, selectedColor) }
            onAppliedCallback()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.pref_custom_color_reset) { _, _ ->
            activity?.let { ThemeManager.clearCustomColor(it) }
            onAppliedCallback()
        }
        .create()
    }
}
