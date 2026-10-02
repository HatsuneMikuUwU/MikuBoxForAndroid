package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.miku.ray.R
import com.miku.ray.util.FontPickerAdapter
import com.miku.ray.util.WindowBlurUtils

class FontPickerBottomSheet(
    private val context: Context,
    private val selectedValue: String,
    private val onSelected: (value: String, label: String) -> Unit
) {
    fun show() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.uwu_layout_bottom_sheet_font_picker, null)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerFont)

        val values = context.resources.getStringArray(R.array.app_font_values)
        val labels = context.resources.getStringArray(R.array.app_font_entries)

        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = FontPickerAdapter(context, values, labels, selectedValue) { value, label ->
            onSelected(value, label)
            dialog.dismiss()
        }

        dialog.setContentView(view)

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val bottomSheet = dialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        if (bottomSheet != null) {
            bottomSheet.clipToOutline = true

            ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { bottomSheetView, insets ->
                val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

                val screenHeight = bottomSheetView.resources.displayMetrics.heightPixels
                val baseSizePx = (8 * bottomSheetView.resources.displayMetrics.density).toInt()

                dialog.behavior.maxHeight = screenHeight - statusBarInset - baseSizePx

                bottomSheetView.findViewById<android.view.View>(R.id.bottom_sheet)?.updatePadding(
                    bottom = baseSizePx + navBarInset
                )

                insets
            }
        }

        dialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        dialog.show()
    }
}
