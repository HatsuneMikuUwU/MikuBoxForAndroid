package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.res.ColorStateList
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr

/**
 * Shared presentation for the app's bottom sheet menus: opens fully expanded,
 * rounded corners, background tinted to match the sheet content, and the
 * navigation bar colored to match so it reads as a seamless surface.
 */
abstract class BaseBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return

        val bgColor = requireContext().getColorAttr(R.attr.colorBg)

        sheetDialog.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.navigationBarColor = bgColor
        }

        val bottomSheet = sheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.backgroundTintList = ColorStateList.valueOf(bgColor)
        bottomSheet.clipToOutline = true

        sheetDialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val screenHeight = view.resources.displayMetrics.heightPixels
            val margin = (8 * view.resources.displayMetrics.density).toInt()

            sheetDialog.behavior.maxHeight = screenHeight - statusBarInset - margin

            insets
        }
    }
}
