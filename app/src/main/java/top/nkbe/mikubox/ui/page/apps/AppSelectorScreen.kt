package top.nkbe.mikubox.ui.page.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.nekohasekai.sagernet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSegmentedTabs
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

enum class AppProxyMode {
    OFF,
    PROXY,
    BYPASS,
}

@Immutable
data class AppEntryUi(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val selected: Boolean,
)

@Immutable
data class AppSelectorUiState(
    val title: String,
    val subtitle: String,
    val isLoading: Boolean = true,
    val apps: List<AppEntryUi> = emptyList(),
    val showSystemApps: Boolean = false,
    val showModeControls: Boolean = false,
    val proxyMode: AppProxyMode = AppProxyMode.PROXY,
    val statusMessage: String? = null,
)

data class AppSelectorCallbacks(
    val onBack: () -> Unit,
    val onToggleApp: (AppEntryUi) -> Unit,
    val onToggleSystemApps: (Boolean) -> Unit,
    val onSelectProxyMode: (AppProxyMode) -> Unit,
    val onAutoSelectProxyApps: () -> Unit,
    val onInvertSelections: () -> Unit,
    val onClearSelections: () -> Unit,
    val onExportClipboard: () -> Unit,
    val onImportClipboard: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onDismissStatus: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    state: AppSelectorUiState,
    callbacks: AppSelectorCallbacks,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showActionsSheet by rememberSaveable { mutableStateOf(false) }
    var showAutoSelectConfirm by rememberSaveable { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    val selectedCount = remember(state.apps) { state.apps.count { it.selected } }
    val filteredApps = remember(state.apps, state.showSystemApps, query) {
        val needle = query.trim()
        state.apps.filter { entry ->
            val matchesSystem = state.showSystemApps || !entry.isSystem
            val matchesQuery = needle.isEmpty() ||
                entry.label.contains(needle, ignoreCase = true) ||
                entry.packageName.contains(needle, ignoreCase = true) ||
                entry.uid.toString().contains(needle)
            matchesSystem && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.app_name),
            title = state.title,
            subtitle = state.subtitle,
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = callbacks.onBack,
            actions = { heroPalette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_more_vert_24,
                    contentDescription = state.title,
                    heroPalette = heroPalette,
                    onClick = { showActionsSheet = true },
                )
            },
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = "$selectedCount / ${state.apps.size}",
                    heroPalette = heroPalette,
                )
                if (state.showModeControls) {
                    val modeText = when (state.proxyMode) {
                        AppProxyMode.OFF -> stringResource(R.string.off)
                        AppProxyMode.PROXY -> stringResource(R.string.route_proxy)
                        AppProxyMode.BYPASS -> stringResource(R.string.bypass_apps)
                    }
                    MikuHeroGlassPill(
                        text = modeText,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                if (state.showModeControls) {
                    val modeIndex = when (state.proxyMode) {
                        AppProxyMode.OFF -> 0
                        AppProxyMode.PROXY -> 1
                        AppProxyMode.BYPASS -> 2
                    }
                    MikuSegmentedTabs(
                        tabs = listOf(
                            stringResource(R.string.off),
                            stringResource(R.string.route_proxy),
                            stringResource(R.string.bypass_apps),
                        ),
                        selectedIndex = modeIndex,
                        onSelectTab = { idx ->
                            callbacks.onSelectProxyMode(
                                when (idx) {
                                    0 -> AppProxyMode.OFF
                                    1 -> AppProxyMode.PROXY
                                    else -> AppProxyMode.BYPASS
                                }
                            )
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.showSystemApps,
                            onClick = { callbacks.onToggleSystemApps(!state.showSystemApps) },
                            label = {
                                Text(
                                    text = stringResource(R.string.show_system_apps),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primary,
                                selectedLabelColor = onPrimaryContrast,
                            ),
                        )

                        if (state.showModeControls) {
                            FilterChip(
                                selected = false,
                                onClick = { showAutoSelectConfirm = true },
                                label = {
                                    Text(
                                        text = stringResource(R.string.auto_select_proxy_apps),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }

                    state.statusMessage?.let { msg ->
                        MikuSoftCard(
                            onClick = callbacks.onDismissStatus,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(android.R.string.ok),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.apps.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_list_permission_denied),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Button(
                                        onClick = callbacks.onOpenAppSettings,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = primary,
                                            contentColor = onPrimaryContrast,
                                        ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.open_app_settings),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filteredApps, key = { it.packageName }) { entry ->
                                AppEntryRow(
                                    entry = entry,
                                    onClick = { callbacks.onToggleApp(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MikuSoftCard(shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppSheetActionRow(
                            iconRes = R.drawable.ic_baseline_refresh_24,
                            title = stringResource(R.string.invert_selections),
                            onClick = {
                                showActionsSheet = false
                                callbacks.onInvertSelections()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        AppSheetActionRow(
                            iconRes = R.drawable.ic_action_delete,
                            title = stringResource(R.string.clear_selections),
                            onClick = {
                                showActionsSheet = false
                                callbacks.onClearSelections()
                            },
                        )
                    }
                }

                MikuSoftCard(shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AppSheetActionRow(
                            iconRes = R.drawable.ic_content_copy,
                            title = stringResource(R.string.action_export_clipboard),
                            onClick = {
                                showActionsSheet = false
                                callbacks.onExportClipboard()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        AppSheetActionRow(
                            iconRes = R.drawable.ic_cloud_download,
                            title = stringResource(R.string.action_import),
                            onClick = {
                                showActionsSheet = false
                                callbacks.onImportClipboard()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAutoSelectConfirm) {
        AlertDialog(
            onDismissRequest = { showAutoSelectConfirm = false },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.auto_select_proxy_apps_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoSelectConfirm = false
                        callbacks.onAutoSelectProxyApps()
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
                TextButton(onClick = { showAutoSelectConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun AppEntryRow(
    entry: AppEntryUi,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon by produceState<ImageBitmap?>(initialValue = null, key1 = entry.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(entry.packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    MikuSoftCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon!!,
                    contentDescription = entry.label,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_security_24)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${entry.packageName} (${entry.uid})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Checkbox(
                checked = entry.selected,
                onCheckedChange = { onClick() },
            )
        }
    }
}

@Composable
private fun AppSheetActionRow(
    iconRes: Int,
    title: String,
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
        MikuSettingIconBadge(iconRes = iconRes)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
