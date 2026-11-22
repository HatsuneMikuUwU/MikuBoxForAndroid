package top.nkbe.mikubox.ui.page.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sagernet.R
import moe.matsuri.nb4a.utils.toBytesString
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

data class GroupScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onCreateGroup: () -> Unit,
    val onUpdateAll: () -> Unit,
    val onOpenGroup: (Long) -> Unit,
    val onEditGroup: (Long) -> Unit,
    val onUpdateGroup: (Long) -> Unit,
    val onMoveGroup: (Long, Int) -> Unit,
    val onShareUniversalQr: (GroupItemUi) -> Unit,
    val onCopyUniversalLink: (GroupItemUi) -> Unit,
    val onExportClipboard: (GroupItemUi) -> Unit,
    val onExportFile: (GroupItemUi) -> Unit,
    val onClearGroup: (Long) -> Unit,
    val onDeleteGroup: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: GroupUiState,
    callbacks: GroupScreenCallbacks,
) {
    var showUpdateAllConfirm by rememberSaveable { mutableStateOf(false) }
    var activeOptionsGroup by remember { mutableStateOf<GroupItemUi?>(null) }
    var confirmClearGroup by remember { mutableStateOf<GroupItemUi?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<GroupItemUi?>(null) }

    val bottomDockDp = LocalMikuBottomDockDp.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.app_name),
            title = stringResource(R.string.menu_group),
            subtitle = stringResource(R.string.sub_summary_count, state.subCount),
            onOpenMenu = callbacks.onOpenMenu,
            actions = { heroPalette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_update_24,
                    contentDescription = stringResource(R.string.update_all_subscription),
                    heroPalette = heroPalette,
                    onClick = { showUpdateAllConfirm = true },
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_av_playlist_add,
                    contentDescription = stringResource(R.string.group_create),
                    heroPalette = heroPalette,
                    onClick = callbacks.onCreateGroup,
                )
            },
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = stringResource(R.string.sub_summary_count, state.subCount),
                    heroPalette = heroPalette,
                )
                MikuHeroGlassPill(
                    text = stringResource(R.string.sub_summary_nodes, state.totalNodes),
                    heroPalette = heroPalette,
                )
                if (state.attentionCount > 0) {
                    MikuHeroGlassPill(
                        text = stringResource(R.string.sub_summary_attention, state.attentionCount),
                        heroPalette = heroPalette,
                    )
                }
            },
        )

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset(y = (-20).dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = bottomDockDp + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = state.groups,
                    key = { _, group -> group.id },
                ) { index, group ->
                    val prevMovable = index > 0 && !group.ungrouped && !group.isUpdating && !state.groups[index - 1].ungrouped
                    val nextMovable = index < state.groups.lastIndex && !group.ungrouped && !group.isUpdating && !state.groups[index + 1].ungrouped

                    GroupCard(
                        group = group,
                        canMoveUp = prevMovable,
                        canMoveDown = nextMovable,
                        onOpen = { callbacks.onOpenGroup(group.id) },
                        onEdit = { callbacks.onEditGroup(group.id) },
                        onUpdate = { callbacks.onUpdateGroup(group.id) },
                        onMoveUp = { callbacks.onMoveGroup(group.id, -1) },
                        onMoveDown = { callbacks.onMoveGroup(group.id, 1) },
                        onMore = { activeOptionsGroup = group },
                    )
                }
            }
        }
    }

    activeOptionsGroup?.let { group ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeOptionsGroup = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            GroupOptionsSheetContent(
                group = group,
                onShareQr = {
                    activeOptionsGroup = null
                    callbacks.onShareUniversalQr(group)
                },
                onCopyUniversal = {
                    activeOptionsGroup = null
                    callbacks.onCopyUniversalLink(group)
                },
                onExportClipboard = {
                    activeOptionsGroup = null
                    callbacks.onExportClipboard(group)
                },
                onExportFile = {
                    activeOptionsGroup = null
                    callbacks.onExportFile(group)
                },
                onRequestClear = {
                    activeOptionsGroup = null
                    confirmClearGroup = group
                },
                onRequestDelete = {
                    activeOptionsGroup = null
                    confirmDeleteGroup = group
                },
            )
        }
    }

    if (showUpdateAllConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateAllConfirm = false },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.update_all_subscription)) },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateAllConfirm = false
                        callbacks.onUpdateAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = onPrimaryContrast,
                    ),
                ) {
                    Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateAllConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    confirmClearGroup?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmClearGroup = null },
            title = { Text(target.name, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.clear_profiles_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearGroup = null
                        callbacks.onClearGroup(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = mikuContrastColor(MaterialTheme.colorScheme.error),
                    ),
                ) {
                    Text(stringResource(R.string.clear_profiles), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearGroup = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    confirmDeleteGroup?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = null },
            title = { Text(target.name, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_group_prompt)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteGroup = null
                        callbacks.onDeleteGroup(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = mikuContrastColor(MaterialTheme.colorScheme.error),
                    ),
                ) {
                    Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroup = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun GroupCard(
    group: GroupItemUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onUpdate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMore: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (accentColor, pillBg, pillFg) = remember(group.health, isDark, scheme) {
        when (group.health) {
            SubHealth.OK -> {
                val bg = scheme.secondaryContainer
                Triple(scheme.primary, bg, mikuContrastColor(bg))
            }
            SubHealth.WARN -> Triple(
                Color(0xFFF59E0B),
                Color(if (isDark) 0xFF3A2A12 else 0xFFFFF3DC),
                Color(if (isDark) 0xFFFBBF24 else 0xFFA86400),
            )
            SubHealth.ERR -> Triple(
                Color(if (isDark) 0xFFF87171 else 0xFFEF4444),
                Color(if (isDark) 0xFF3C1919 else 0xFFFEE4E2),
                Color(if (isDark) 0xFFFCA5A5 else 0xFFB42318),
            )
            SubHealth.LOCAL -> {
                val bg = scheme.surfaceContainerHigh
                Triple(scheme.onSurfaceVariant, bg, mikuContrastColor(bg))
            }
        }
    }

    val nodesLabel = stringResource(R.string.sub_summary_nodes, group.nodeCount)
    val pillText = if (!group.isSubscription) {
        nodesLabel
    } else {
        when {
            group.daysLeft == null -> nodesLabel
            group.daysLeft <= 0 -> stringResource(R.string.sub_expired)
            else -> stringResource(R.string.sub_days_left, group.daysLeft)
        }
    }

    val statusText = if (!group.isSubscription) {
        if (group.nodeCount == 0) {
            stringResource(R.string.group_status_empty)
        } else {
            stringResource(R.string.sub_local_group)
        }
    } else {
        if (group.nodeCount == 0) {
            stringResource(R.string.group_status_empty_subscription)
        } else {
            formatRelativeSyncLabel(group.lastUpdatedSec)
        }
    }

    MikuSoftCard(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (group.username.isNotBlank()) {
                            Text(
                                text = group.username,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = pillBg,
                    contentColor = pillFg,
                ) {
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            if (group.hasQuota) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "♪",
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (group.totalBytes > 0L) {
                                        "${group.usedBytes.toBytesString()} / ${group.totalBytes.toBytesString()}"
                                    } else {
                                        stringResource(R.string.subscription_used, group.usedBytes.toBytesString())
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (group.totalBytes > 0L) {
                                        "$nodesLabel · ${group.percent}%"
                                    } else {
                                        nodesLabel
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (group.totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { (group.percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = accentColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                }
            }

            if (group.isUpdating) {
                Spacer(Modifier.height(10.dp))
                if (group.updateProgress != null) {
                    LinearProgressIndicator(
                        progress = { group.updateProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!group.ungrouped) {
                    GroupIconActionChip(
                        iconRes = R.drawable.uwu_arrow_down,
                        contentDescription = "Move Up",
                        enabled = canMoveUp,
                        rotation = 180f,
                        onClick = onMoveUp,
                    )
                    GroupIconActionChip(
                        iconRes = R.drawable.uwu_arrow_down,
                        contentDescription = "Move Down",
                        enabled = canMoveDown,
                        onClick = onMoveDown,
                    )
                }

                Spacer(Modifier.weight(1f))

                if (group.isSubscription) {
                    GroupLabelActionChip(
                        iconRes = R.drawable.ic_baseline_refresh_24,
                        label = stringResource(R.string.group_update),
                        enabled = !group.isUpdating,
                        onClick = onUpdate,
                    )
                }

                if (!group.ungrouped && !group.isUpdating) {
                    GroupIconActionChip(
                        iconRes = R.drawable.ic_image_edit,
                        contentDescription = stringResource(R.string.edit),
                        enabled = true,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onEdit,
                    )
                }

                GroupIconActionChip(
                    iconRes = R.drawable.ic_baseline_more_vert_24,
                    contentDescription = stringResource(R.string.action_export),
                    enabled = true,
                    onClick = onMore,
                )
            }
        }
    }
}

@Composable
private fun formatRelativeSyncLabel(lastUpdatedSec: Int): String {
    if (lastUpdatedSec <= 0) return stringResource(R.string.sub_local_group)
    val diffMin = ((System.currentTimeMillis() / 1000L) - lastUpdatedSec.toLong()).coerceAtLeast(0L) / 60L
    return when {
        diffMin < 2L -> stringResource(R.string.sub_synced_just_now)
        diffMin < 60L -> stringResource(R.string.sub_synced_min_ago, diffMin.toInt())
        diffMin < 1440L -> stringResource(R.string.sub_synced_hour_ago, (diffMin / 60L).toInt())
        else -> stringResource(R.string.sub_synced_day_ago, (diffMin / 1440L).toInt())
    }
}

@Composable
private fun GroupIconActionChip(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    rotation: Float = 0f,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
            )
        }
    }
}

@Composable
private fun GroupLabelActionChip(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = remember(bg) { mikuContrastColor(bg) }
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = bg,
        contentColor = fg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupOptionsSheetContent(
    group: GroupItemUi,
    onShareQr: () -> Unit,
    onCopyUniversal: () -> Unit,
    onExportClipboard: () -> Unit,
    onExportFile: () -> Unit,
    onRequestClear: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (group.isSubscription) {
            MikuSoftCard(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SheetActionRow(
                        iconRes = R.drawable.ic_action_qr,
                        title = "${stringResource(R.string.share_subscription)} · ${stringResource(R.string.share_qr_nfc)}",
                        onClick = onShareQr,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    SheetActionRow(
                        iconRes = R.drawable.ic_content_copy,
                        title = "${stringResource(R.string.share_subscription)} · ${stringResource(R.string.action_export_clipboard)}",
                        onClick = onCopyUniversal,
                    )
                }
            }
        }

        MikuSoftCard(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetActionRow(
                    iconRes = R.drawable.ic_content_copy,
                    title = "${stringResource(R.string.action_export)} · ${stringResource(R.string.action_export_clipboard)}",
                    onClick = onExportClipboard,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                SheetActionRow(
                    iconRes = R.drawable.ic_file_file_upload,
                    title = stringResource(R.string.action_export_file),
                    onClick = onExportFile,
                )
            }
        }

        MikuSoftCard(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetActionRow(
                    iconRes = R.drawable.ic_baseline_refresh_24,
                    title = stringResource(R.string.clear_profiles),
                    destructive = true,
                    onClick = onRequestClear,
                )
                if (!group.ungrouped && !group.isUpdating) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    SheetActionRow(
                        iconRes = R.drawable.ic_action_delete,
                        title = stringResource(R.string.delete),
                        destructive = true,
                        onClick = onRequestDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    iconRes: Int,
    title: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (destructive) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            MikuSettingIconBadge(iconRes = iconRes)
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
