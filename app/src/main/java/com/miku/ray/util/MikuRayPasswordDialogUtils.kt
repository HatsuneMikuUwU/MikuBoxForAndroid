package com.miku.ray.util

import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogMikurayPasswordBinding

fun showMikuRayExportPasswordDialog(
    context: Context,
    onConfirm: (password: String) -> Unit
) {
    val binding = DialogMikurayPasswordBinding.inflate(LayoutInflater.from(context))
    binding.tvMikurayPasswordDesc.setText(R.string.mikuray_password_export_desc)

    val dialog = MaterialAlertDialogBuilder(context)
    .setTitle(R.string.mikuray_password_export_title)
    .setIcon(RemixR.drawable.rmx_lock_line)
    .setView(binding.root)
    .setPositiveButton(R.string.mikuray_password_confirm_button, null)
    .setNegativeButton(android.R.string.cancel, null)
    .showBlur()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val password = binding.etMikurayPassword.text.toString()
        val confirm = binding.etMikurayPasswordConfirm.text.toString()

        binding.etMikurayPassword.error = null
        binding.etMikurayPasswordConfirm.error = null

        when {
            password.isEmpty() -> {
                binding.etMikurayPassword.error = context.getString(R.string.mikuray_password_error_empty)
            }
            password != confirm -> {
                binding.etMikurayPasswordConfirm.error = context.getString(R.string.mikuray_password_error_mismatch)
            }
            else -> {
                dialog.dismiss()
                onConfirm(password)
            }
        }
    }
}

fun showMikuRayImportPasswordDialog(
    context: Context,
    onConfirm: (password: String) -> Unit
) {
    val binding = DialogMikurayPasswordBinding.inflate(LayoutInflater.from(context))
    binding.tvMikurayPasswordDesc.setText(R.string.mikuray_password_import_desc)
    binding.tilMikurayPasswordConfirm.visibility = View.GONE

    val dialog = MaterialAlertDialogBuilder(context)
    .setTitle(R.string.mikuray_password_import_title)
    .setIcon(RemixR.drawable.rmx_lock_unlock_line)
    .setView(binding.root)
    .setPositiveButton(R.string.mikuray_password_confirm_button, null)
    .setNegativeButton(android.R.string.cancel, null)
    .showBlur()

    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val password = binding.etMikurayPassword.text.toString()
        binding.etMikurayPassword.error = null

        if (password.isEmpty()) {
            binding.etMikurayPassword.error = context.getString(R.string.mikuray_password_error_empty)
        } else {
            dialog.dismiss()
            onConfirm(password)
        }
    }
}
