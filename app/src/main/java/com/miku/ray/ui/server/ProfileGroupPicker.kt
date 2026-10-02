package com.miku.ray.ui.server

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.miku.ray.MikuProfiles

object ProfileGroupPicker {
    fun create(context: Context, done: (MikuProfiles.Group) -> Unit) {
        val input = TextInputEditText(context).apply { setSingleLine() }
        val box = TextInputLayout(context).apply {
            hint = context.getString(com.miku.ray.R.string.mihomo_group_name_hint)
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(com.miku.ray.R.string.mihomo_group_create_title))
            .setView(box).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching { requireNotNull(MikuProfiles.impl).createGroup(input.text.toString()) }
                    .onSuccess { dialog.dismiss(); done(it) }
                    .onFailure { box.error = it.message }
            }
        }
        dialog.show()
    }

    fun manage(context: Context, done: () -> Unit) {
        val store = MikuProfiles.impl ?: return
        val groups = store.groups().filter { it.id != com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(com.miku.ray.R.string.mihomo_group_manage_title))
            .setNeutralButton(context.getString(com.miku.ray.R.string.mihomo_group_create_title)) { _, _ -> create(context) { done() } }
            .setNegativeButton(android.R.string.cancel, null)
        if (groups.isEmpty()) builder.setMessage(context.getString(com.miku.ray.R.string.mihomo_group_manage_empty))
        else builder.setItems(groups.map { group ->
            context.getString(
                com.miku.ray.R.string.mihomo_group_entry_count,
                group.name, store.list().count { it.groupId == group.id },
            )
        }.toTypedArray()) { _, index -> confirmDelete(context, groups[index], done) }
        builder.show()
    }

    fun confirmDelete(context: Context, group: MikuProfiles.Group, done: () -> Unit) {
        if (group.id == com.miku.ray.AppConfig.DEFAULT_SUBSCRIPTION_ID) { manage(context, done); return }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(com.miku.ray.R.string.mihomo_group_delete_title, group.name))
            .setMessage(context.getString(com.miku.ray.R.string.mihomo_group_delete_message))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(context.getString(com.miku.ray.R.string.mihomo_group_delete_action)) { _, _ ->
                runCatching { requireNotNull(MikuProfiles.impl).deleteGroup(group.id) }
                    .onSuccess { done() }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }.show()
    }

    fun choose(context: Context, selected: String, done: (MikuProfiles.Group) -> Unit) {
        val groups = MikuProfiles.impl?.groups().orEmpty()
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(com.miku.ray.R.string.mihomo_group_choose_title))
            .setSingleChoiceItems(groups.map { it.name }.toTypedArray(), groups.indexOfFirst { it.id == selected }) { dialog, index ->
                dialog.dismiss()
                done(groups[index])
            }
            .setNeutralButton(context.getString(com.miku.ray.R.string.mihomo_group_new_action)) { _, _ -> create(context, done) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
