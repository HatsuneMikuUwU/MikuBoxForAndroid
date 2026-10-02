package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogDeleteConfirmBinding
import com.miku.ray.remixicon.R as RemixR

fun showDeleteConfirmDialog(
    context: Context,
    @StringRes messageRes: Int,
    @StringRes titleRes: Int = R.string.del_config_comfirm,
    @DrawableRes iconRes: Int = RemixR.drawable.rmx_system_alert_line,
    @StringRes positiveTextRes: Int = R.string.del_button_dialog_comfirm,
    @StringRes negativeTextRes: Int = android.R.string.cancel,
    onConfirm: () -> Unit
) = showDeleteConfirmDialog(
    context = context,
    message = context.getString(messageRes),
    titleRes = titleRes,
    iconRes = iconRes,
    positiveTextRes = positiveTextRes,
    negativeTextRes = negativeTextRes,
    onConfirm = onConfirm,
)

/** Same dialog for dynamic message text (e.g. a name the caller appends). */
fun showDeleteConfirmDialog(
    context: Context,
    message: CharSequence,
    @StringRes titleRes: Int = R.string.del_config_comfirm,
    @DrawableRes iconRes: Int = RemixR.drawable.rmx_system_alert_line,
    @StringRes positiveTextRes: Int = R.string.del_button_dialog_comfirm,
    @StringRes negativeTextRes: Int = android.R.string.cancel,
    onConfirm: () -> Unit
) {
    val binding = DialogDeleteConfirmBinding.inflate(LayoutInflater.from(context))
    binding.dialogIcon.setImageResource(iconRes)
    binding.dialogTitle.setText(titleRes)
    binding.dialogMessage.text = message

    val dialog = MaterialAlertDialogBuilder(context)
    .setView(binding.root)
    .setPositiveButton(positiveTextRes) { _, _ ->
        onConfirm()
    }
    .setNegativeButton(negativeTextRes, null)
    .create()

    WindowBlurUtils.applyWindowBlur(dialog.window)
    dialog.show()
}
