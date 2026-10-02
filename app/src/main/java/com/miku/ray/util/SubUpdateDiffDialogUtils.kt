package com.miku.ray.util

import com.miku.ray.remixicon.R as RemixR
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.dto.ProfileDiffEntry
import com.miku.ray.dto.SubscriptionUpdateResult

fun showSubUpdateDiffDialog(context: Context, result: SubscriptionUpdateResult) {
    if (result.addedProfiles.isEmpty() && result.deletedProfiles.isEmpty()) return

    val subNames = (result.addedProfiles.asSequence().map { it.subscriptionName } +
        result.deletedProfiles.asSequence().map { it.subscriptionName })
    .distinct()
    .toList()
    val multipleSubs = subNames.size > 1

    fun format(entries: List<ProfileDiffEntry>): String = entries.joinToString("\n") { entry ->
        if (multipleSubs) "• [${entry.subscriptionName}] ${entry.profileName}" else "• ${entry.profileName}"
    }

    val titleSubject = if (subNames.size == 1) subNames.first() else context.getString(R.string.title_sub_update)
    val title = context.getString(R.string.title_sub_update_diff, titleSubject)

    val message = buildString {
        if (result.addedProfiles.isNotEmpty()) {
            append(context.getString(R.string.sub_update_diff_added, format(result.addedProfiles)))
        }
        if (result.deletedProfiles.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(context.getString(R.string.sub_update_diff_deleted, format(result.deletedProfiles)))
        }
    }

    MaterialAlertDialogBuilder(context)
    .setTitle(title)
    .setIcon(RemixR.drawable.rmx_rss_line)
    .setMessage(message)
    .setPositiveButton(android.R.string.ok, null)
    .showBlur()
}
