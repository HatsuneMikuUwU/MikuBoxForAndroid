package com.miku.ray.util

import com.miku.ray.remixicon.R as RemixR
import android.app.Activity
import android.content.res.ColorStateList
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogTabIconPickerBinding
import com.miku.ray.util.showBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class SubscriptionImportChoice(
    val name: String,
    val tabIcon: String?,
)

suspend fun Activity.requestSubscriptionImportName(
    suggestedName: String?,
    existingNames: Set<String>
): SubscriptionImportChoice? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        var completed = false
        fun finish(value: SubscriptionImportChoice?) {
            if (!completed) {
                completed = true
                continuation.resume(value)
            }
        }
        val baseName = suggestedName?.trim().takeUnless { it.isNullOrEmpty() }
        ?: getString(R.string.sub_import_default_name)
        var candidate = baseName
        var suffix = 2
        while (candidate in existingNames) candidate = "$baseName ${suffix++}"

        var selectedTabIcon: String? = null

        val inputView = layoutInflater.inflate(R.layout.dialog_subscription_import, null)
        inputView.findViewById<android.widget.TextView>(android.R.id.message).visibility = android.view.View.GONE
        inputView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.subscription_import_input_layout).hint =
        getString(R.string.sub_import_name)
        val input = inputView.findViewById<com.google.android.material.textfield.TextInputEditText>(android.R.id.edit).apply {
            setSingleLine(true)
            setText(candidate)
            setSelection(text?.length ?: 0)
        }

        val tabIconLayout = inputView.findViewById<com.google.android.material.textfield.TextInputLayout>(
            R.id.subscription_import_tab_icon_layout
        )
        val tabIconEdit = inputView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.subscription_import_tab_icon_edit
        )

        fun applyTabIconSelection(iconName: String?) {
            selectedTabIcon = iconName
            if (iconName == null) {
                tabIconEdit.setText(getString(R.string.sub_tab_icon_none))
                tabIconLayout.setStartIconDrawable(RemixR.drawable.rmx_apps_line)
                tabIconLayout.setStartIconTintList(
                    ColorStateList.valueOf(getColorAttr("colorOnSurfaceVariant"))
                )
            } else {
                val resId = resources.getIdentifier(iconName, "drawable", packageName)
                tabIconEdit.setText(TabIconPickerAdapter.labelFor(iconName))
                if (resId != 0) {
                    tabIconLayout.setStartIconDrawable(resId)
                    tabIconLayout.setStartIconTintList(
                        ColorStateList.valueOf(getColorAttr("colorOnSurfaceVariant"))
                    )
                }
            }
        }
        applyTabIconSelection(null)

        fun showTabIconPicker() {
            val dialogBinding = DialogTabIconPickerBinding.inflate(layoutInflater)
            val rowNone = dialogBinding.rowNone
            val checkNone = dialogBinding.checkNone
            val rv = dialogBinding.rvIcons

            var iconDialog: androidx.appcompat.app.AlertDialog? = null

            val adapter = TabIconPickerAdapter(
                context = this@requestSubscriptionImportName,
                icons = TabIconPickerAdapter.DEFAULT_ICONS,
                selectedIcon = selectedTabIcon,
                onSelect = { name ->
                    applyTabIconSelection(name)
                    iconDialog?.dismiss()
                }
            )
            rv.layoutManager = GridLayoutManager(this@requestSubscriptionImportName, 5)
            rv.adapter = adapter

            val noneSelected = selectedTabIcon == null
            checkNone.visibility = if (noneSelected) android.view.View.VISIBLE else android.view.View.GONE
            checkNone.imageTintList = ColorStateList.valueOf(
                if (noneSelected) getColorAttr("colorPrimary") else 0
            )

            rowNone.setOnClickListener {
                applyTabIconSelection(null)
                iconDialog?.dismiss()
            }

            iconDialog = MaterialAlertDialogBuilder(this@requestSubscriptionImportName)
            .setTitle(R.string.sub_setting_tab_icon)
            .setIcon(RemixR.drawable.rmx_apps_line)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .showBlur()
        }

        tabIconEdit.setOnClickListener { showTabIconPicker() }
        tabIconLayout.setEndIconOnClickListener { showTabIconPicker() }

        val dialog = MaterialAlertDialogBuilder(this@requestSubscriptionImportName)
        .setTitle(R.string.sub_import_name)
        .setIcon(RemixR.drawable.rmx_edit_line)
        .setView(inputView)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val name = input.text?.toString()
            if (name.isNullOrEmpty()) {
                finish(null)
            } else {
                finish(SubscriptionImportChoice(name, selectedTabIcon))
            }
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> finish(null) }
        .setOnCancelListener { finish(null) }
        .showBlur()
        continuation.invokeOnCancellation { dialog.dismiss() }
    }
}
