package com.miku.ray.ui.dialog

import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.util.AppIconPickerAdapter
import com.miku.ray.util.LauncherAliasSwitcher
import com.miku.ray.util.WindowBlurUtils

class AppIconPickerDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    fun refreshSummary() {
        val current = LauncherAliasSwitcher.currentIconVariant()
        val icons = AppIconPickerAdapter.icons(context)
        summary = icons.firstOrNull { it.first == current }?.third ?: icons.first().third
    }

    override fun onClick() {
        val current = LauncherAliasSwitcher.currentIconVariant()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_icon_picker, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rv_app_icons)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val adapter = AppIconPickerAdapter(
            context = context,
            selectedValue = current,
            onSelect = { value ->
                LauncherAliasSwitcher.applyIconVariant(context.applicationContext, value)
                summary = AppIconPickerAdapter.icons(context).firstOrNull { it.first == value }?.third ?: value
                callChangeListener(value)
                dialog.dismiss()
            }
        )
        rv.layoutManager = GridLayoutManager(context, 3)
        rv.adapter = adapter

        dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.title_pref_app_icon)
        .setIcon(RemixR.drawable.rmx_apps_line)
        .setView(dialogView)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

        WindowBlurUtils.applyWindowBlur(dialog.window)
        dialog.show()
    }
}
