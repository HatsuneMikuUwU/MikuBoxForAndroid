package top.nkbe.mikubox.ui.page.tools

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.neko.hostnamefinder.HostnameFinder
import com.neko.hostpotproxy.ui.ProxySettings
import com.neko.hosttoip.HostToIP
import com.neko.ip.hostchecker.HostChecker
import com.neko.speedtest.SpeedTestActivity
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.StunActivity
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSegmentedTabs
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class BackupImportCandidate(
    val hasProfiles: Boolean,
    val hasRules: Boolean,
    val hasSettings: Boolean,
)

@Immutable
data class ToolsUiState(
    val importCandidate: BackupImportCandidate? = null,
    val isImporting: Boolean = false,
)

data class ToolsCallbacks(
    val onOpenMenu: () -> Unit,
    val onLaunchTool: (Class<*>) -> Unit,
    val onExportBackup: (profiles: Boolean, rules: Boolean, settings: Boolean) -> Unit,
    val onShareBackup: (profiles: Boolean, rules: Boolean, settings: Boolean) -> Unit,
    val onPickImportFile: () -> Unit,
    val onConfirmImport: (profiles: Boolean, rules: Boolean, settings: Boolean) -> Unit,
    val onDismissImport: () -> Unit,
    val onResetSettings: () -> Unit,
)

@Immutable
private data class NetworkToolEntry(
    val titleRes: Int,
    val summaryRes: Int,
    @DrawableRes val iconRes: Int,
    val targetActivity: Class<*>,
)

@Composable
fun ToolsScreen(
    state: ToolsUiState,
    callbacks: ToolsCallbacks,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var backupProfiles by rememberSaveable { mutableStateOf(true) }
    var backupRules by rememberSaveable { mutableStateOf(true) }
    var backupSettings by rememberSaveable { mutableStateOf(true) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    val networkTools = remember {
        listOf(
            NetworkToolEntry(
                titleRes = R.string.stun_test,
                summaryRes = R.string.stun_test_summary,
                iconRes = R.drawable.ic_baseline_nat_24,
                targetActivity = StunActivity::class.java,
            ),
            NetworkToolEntry(
                titleRes = R.string.hostname_finder,
                summaryRes = R.string.hostname_finder_summary,
                iconRes = R.drawable.ic_domain,
                targetActivity = HostnameFinder::class.java,
            ),
            NetworkToolEntry(
                titleRes = R.string.host_to_ip,
                summaryRes = R.string.host_to_ip_summary,
                iconRes = R.drawable.ic_ip_info,
                targetActivity = HostToIP::class.java,
            ),
            NetworkToolEntry(
                titleRes = R.string.hotspot_proxy_title,
                summaryRes = R.string.hotspot_proxy_summay,
                iconRes = R.drawable.ic_wifi,
                targetActivity = ProxySettings::class.java,
            ),
            NetworkToolEntry(
                titleRes = R.string.hostchecker_title,
                summaryRes = R.string.hostchecker_summay,
                iconRes = R.drawable.ic_baseline_security_24,
                targetActivity = HostChecker::class.java,
            ),
            NetworkToolEntry(
                titleRes = R.string.speedtest,
                summaryRes = R.string.summary_speed_test,
                iconRes = R.drawable.ic_baseline_speed_24,
                targetActivity = SpeedTestActivity::class.java,
            ),
        )
    }

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
            title = stringResource(R.string.menu_tools),
            subtitle = if (selectedTab == 0) {
                stringResource(R.string.tools_network)
            } else {
                stringResource(R.string.backup)
            },
            onOpenMenu = callbacks.onOpenMenu,
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = "${networkTools.size} ${stringResource(R.string.tools_network)}",
                    heroPalette = heroPalette,
                    onClick = { selectedTab = 0 },
                )
                MikuHeroGlassPill(
                    text = stringResource(R.string.backup),
                    heroPalette = heroPalette,
                    onClick = { selectedTab = 1 },
                )
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
            Column(modifier = Modifier.fillMaxSize()) {
                MikuSegmentedTabs(
                    tabs = listOf(
                        stringResource(R.string.tools_network),
                        stringResource(R.string.backup),
                    ),
                    selectedIndex = selectedTab,
                    onSelectTab = { selectedTab = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                )

                Crossfade(
                    targetState = selectedTab,
                    modifier = Modifier.weight(1f),
                    label = "ToolsTabCrossfade",
                ) { tab ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = bottomDockDp + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (tab == 0) {
                            items(networkTools, key = { it.titleRes }) { tool ->
                                NetworkToolCard(
                                    entry = tool,
                                    onPrimaryColor = onPrimaryContrast,
                                    onClick = { callbacks.onLaunchTool(tool.targetActivity) },
                                )
                            }
                        } else {
                            item(key = "backup_export") {
                                BackupExportCard(
                                    backupProfiles = backupProfiles,
                                    onBackupProfilesChange = { backupProfiles = it },
                                    backupRules = backupRules,
                                    onBackupRulesChange = { backupRules = it },
                                    backupSettings = backupSettings,
                                    onBackupSettingsChange = { backupSettings = it },
                                    onPrimaryContrast = onPrimaryContrast,
                                    onExport = {
                                        callbacks.onExportBackup(backupProfiles, backupRules, backupSettings)
                                    },
                                    onShare = {
                                        callbacks.onShareBackup(backupProfiles, backupRules, backupSettings)
                                    },
                                )
                            }

                            item(key = "backup_restore_reset") {
                                BackupRestoreAndResetCard(
                                    onPickImportFile = callbacks.onPickImportFile,
                                    onRequestReset = { showResetConfirm = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.importCandidate?.let { candidate ->
        BackupImportDialog(
            candidate = candidate,
            onPrimaryContrast = onPrimaryContrast,
            onConfirm = callbacks.onConfirmImport,
            onDismiss = callbacks.onDismissImport,
        )
    }

    if (state.isImporting) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = {
                Text(
                    text = stringResource(R.string.backup_import),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        text = stringResource(R.string.backup_importing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.confirm),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_settings_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        callbacks.onResetSettings()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = mikuContrastColor(MaterialTheme.colorScheme.error),
                    ),
                ) {
                    Text(stringResource(R.string.yes), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun NetworkToolCard(
    entry: NetworkToolEntry,
    onPrimaryColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    MikuSoftCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MikuSettingIconBadge(iconRes = entry.iconRes)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(entry.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(entry.summaryRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = onPrimaryColor,
            ) {
                Text(
                    text = stringResource(R.string.start),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun BackupExportCard(
    backupProfiles: Boolean,
    onBackupProfilesChange: (Boolean) -> Unit,
    backupRules: Boolean,
    onBackupRulesChange: (Boolean) -> Unit,
    backupSettings: Boolean,
    onBackupSettingsChange: (Boolean) -> Unit,
    onPrimaryContrast: androidx.compose.ui.graphics.Color,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    val hasSelection = backupProfiles || backupRules || backupSettings
    val primary = MaterialTheme.colorScheme.primary

    MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MikuSettingIconBadge(iconRes = R.drawable.ic_file_file_upload)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.backup_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            BackupCheckRow(
                label = stringResource(R.string.backup_groups_and_configurations),
                checked = backupProfiles,
                onCheckedChange = onBackupProfilesChange,
            )
            BackupCheckRow(
                label = stringResource(R.string.backup_rules),
                checked = backupRules,
                onCheckedChange = onBackupRulesChange,
            )
            BackupCheckRow(
                label = stringResource(R.string.backup_settings),
                checked = backupSettings,
                onCheckedChange = onBackupSettingsChange,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onExport,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = onPrimaryContrast,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_file_upload),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_export),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onShare,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_social_share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.share),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreAndResetCard(
    onPickImportFile: () -> Unit,
    onRequestReset: () -> Unit,
) {
    MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickImportFile)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MikuSettingIconBadge(iconRes = R.drawable.ic_cloud_download)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.action_import_file),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.backup_import_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestReset)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_refresh_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reset_settings),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.reset_settings_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
    }
}

@Composable
private fun BackupImportDialog(
    candidate: BackupImportCandidate,
    onPrimaryContrast: androidx.compose.ui.graphics.Color,
    onConfirm: (profiles: Boolean, rules: Boolean, settings: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var importProfiles by remember(candidate) { mutableStateOf(candidate.hasProfiles) }
    var importRules by remember(candidate) { mutableStateOf(candidate.hasRules) }
    var importSettings by remember(candidate) { mutableStateOf(candidate.hasSettings) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.backup_import),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.backup_import_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (candidate.hasProfiles) {
                    BackupCheckRow(
                        label = stringResource(R.string.backup_groups_and_configurations),
                        checked = importProfiles,
                        onCheckedChange = { importProfiles = it },
                    )
                }
                if (candidate.hasRules) {
                    BackupCheckRow(
                        label = stringResource(R.string.backup_rules),
                        checked = importRules,
                        onCheckedChange = { importRules = it },
                    )
                }
                if (candidate.hasSettings) {
                    BackupCheckRow(
                        label = stringResource(R.string.backup_settings),
                        checked = importSettings,
                        onCheckedChange = { importSettings = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(importProfiles, importRules, importSettings) },
                enabled = importProfiles || importRules || importSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = onPrimaryContrast,
                ),
            ) {
                Text(stringResource(R.string.backup_import), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
