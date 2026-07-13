package top.uwu.mikubox.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import top.uwu.mikubox.R
import top.uwu.mikubox.core.AppSettings

/**
 * NekoBox-style navigation menu, ported from MikuRay's main-menu bottom sheet:
 * an animated particle banner with a profile header, an "app settings" row, and
 * a "tools & info" badge row. Replaces the old side navigation drawer.
 */
class MenuBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.layout_menu_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.particles_view).isVisible = AppSettings.particlesEnabled(requireContext())

        bindRow(view, R.id.row_menu_settings, R.drawable.ic_settings_24dp, R.string.settings)
        bindBadge(view, R.id.badge_menu_logcat, R.drawable.ic_logcat_24dp, R.string.menu_log, R.string.desc_menu_log)
        bindBadge(view, R.id.badge_menu_tools, R.drawable.baseline_construction_24, R.string.menu_tools, R.string.desc_menu_tools)
        bindBadge(view, R.id.badge_menu_about, R.drawable.ic_about_24dp, R.string.menu_about, R.string.desc_menu_about)

        view.findViewById<View>(R.id.menu_settings).setOnClickListener {
            open(SettingsActivity::class.java)
        }
        view.findViewById<View>(R.id.menu_logcat).setOnClickListener {
            open(LogcatActivity::class.java)
        }
        view.findViewById<View>(R.id.menu_tools).setOnClickListener { comingSoon() }
        view.findViewById<View>(R.id.menu_about).setOnClickListener {
            open(AboutActivity::class.java)
        }
    }

    private fun open(activity: Class<*>) {
        startActivity(Intent(requireContext(), activity))
        dismiss()
    }

    private fun comingSoon() {
        Toast.makeText(requireContext(), R.string.toast_coming_soon, Toast.LENGTH_SHORT).show()
    }

    private fun bindRow(root: View, rowId: Int, iconRes: Int, titleRes: Int) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.row_title).setText(titleRes)
    }

    private fun bindBadge(root: View, badgeId: Int, iconRes: Int, titleRes: Int, descRes: Int) {
        val badge = root.findViewById<View>(badgeId)
        badge.findViewById<ImageView>(R.id.badge_icon).setImageResource(iconRes)
        badge.findViewById<TextView>(R.id.badge_title).setText(titleRes)
        badge.findViewById<TextView>(R.id.badge_desc).setText(descRes)
    }

    companion object {
        const val TAG = "MenuBottomSheet"
    }
}
