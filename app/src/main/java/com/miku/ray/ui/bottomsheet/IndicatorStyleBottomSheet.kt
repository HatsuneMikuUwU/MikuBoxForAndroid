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
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.IndicatorStyle
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.util.IndicatorStyleAdapter
class IndicatorStyleBottomSheet(
    private val context: Context,
    private val onSelected: () -> Unit
) {
    fun show() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.uwu_layout_bottom_sheet_indicator_style, null)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerStyle)

        val currentStyleName = MmkvManager.decodeSettingsString(
            AppConfig.PREF_INDICATOR_STYLE,
            IndicatorStyle.STYLE_0.name
        ) ?: IndicatorStyle.STYLE_0.name

        val selectedStyle = runCatching { IndicatorStyle.valueOf(currentStyleName) }
        .getOrDefault(IndicatorStyle.STYLE_0)

        recycler.layoutManager = LinearLayoutManager(context)

        recycler.adapter = IndicatorStyleAdapter(context, selectedStyle) { style ->
            MmkvManager.encodeSettings(AppConfig.PREF_INDICATOR_STYLE, style.name)
            onSelected()
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
