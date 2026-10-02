package com.miku.ray.util

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.DialogTotalTrafficDetailBinding
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.widget.TrafficBarChartView

private const val HISTORY_DAYS = 7

fun showTotalTrafficDetailDialog(context: Context, onCleared: () -> Unit = {}) {
    val hasData = MmkvManager.getTotalTrafficDetail() != null
    val (uploadBytes, downloadBytes) = MmkvManager.getTotalTrafficDetail() ?: (0L to 0L)

    val binding = DialogTotalTrafficDetailBinding.inflate(LayoutInflater.from(context))
    binding.tvUploadValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes)
    binding.tvDownloadValue.text = MmkvManager.formatTrafficBytesPublic(downloadBytes)
    binding.tvCombinedValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes + downloadBytes)

    val (todayUp, todayDown) = MmkvManager.getTodayTrafficDetail()
    binding.tvTodayValue.text = MmkvManager.formatTrafficBytesPublic(todayUp + todayDown)

    val (monthUp, monthDown) = MmkvManager.getCurrentMonthTrafficDetail()
    binding.tvMonthValue.text = MmkvManager.formatTrafficBytesPublic(monthUp + monthDown)

    val history = MmkvManager.getDailyTrafficHistory(HISTORY_DAYS)
    val hasHistory = history.any { (_, up, down) -> up + down > 0L }
    binding.chartDailyTraffic.isVisible = hasHistory
    binding.tvHistoryEmpty.isVisible = !hasHistory
    binding.chartDailyTraffic.setEntries(
        history.map { (dateKey, up, down) -> TrafficBarChartView.DayEntry(dateKey, up, down) }
    )

    val dialog = MaterialAlertDialogBuilder(context)
    .setView(binding.root)
    .setPositiveButton(android.R.string.ok, null)
    .setNegativeButton(R.string.action_clear_total_traffic_button) { _, _ ->
        showClearTotalTrafficConfirmDialog(context, onCleared)
    }
    .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = hasData
    }
    dialog.show()
}

private fun showClearTotalTrafficConfirmDialog(context: Context, onCleared: () -> Unit) {
    MaterialAlertDialogBuilder(context)
    .setTitle(R.string.pref_action_clear_total_traffic_title)
    .setIcon(RemixR.drawable.rmx_delete_bin_line)
    .setMessage(R.string.confirm_clear_total_traffic)
    .setPositiveButton(android.R.string.ok) { _, _ ->
        MmkvManager.clearTotalTrafficDataAndHistory()
        context.sendBroadcast(
            Intent(AppConfig.BROADCAST_ACTION_TRAFFIC_WIDGET_REFRESH).setPackage(context.packageName)
        )
        onCleared()
        context.snackbarSuccess(
            context.getString(R.string.toast_total_traffic_cleared),
            title = context.getString(R.string.title_alerter_success)
        )
    }
    .setNegativeButton(android.R.string.cancel, null)
    .showBlur()
}
