package top.nkbe.mikubox.ui.page.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import moe.matsuri.nb4a.Protocols
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.theme.MikuTheme
import top.nkbe.mikubox.ui.viewmodel.ProfileGroupChip
import top.nkbe.mikubox.ui.viewmodel.ProfilesUiState
import top.nkbe.mikubox.ui.viewmodel.ProfilesViewModel
import java.util.Calendar

data class ProfilesScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onSpeedTest: () -> Unit,
    val onOpenSubscriptions: () -> Unit,
    val onAddProfile: () -> Unit,
    val onOpenMoreMenu: () -> Unit,
    val onSelectProfile: (ProxyEntity) -> Unit,
    val onEditProfile: (ProxyEntity) -> Unit,
    val onShareProfileQr: (ProxyEntity) -> Unit,
    val onCopyProfileLink: (ProxyEntity) -> Unit,
    val onDeleteProfile: (ProxyEntity) -> Unit,
    val onShowError: (String) -> Unit,
)

@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    callbacks: ProfilesScreenCallbacks,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val columns = if (state.doubleColumn) 2 else 1
    val bottomDockDp = LocalMikuBottomDockDp.current
    val listBottomPadding = if (bottomDockDp > 0.dp) bottomDockDp + 20.dp else 160.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Miku Soft Panel Hero Header
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                MikuProfilesHero(
                    state = state,
                    onOpenMenu = callbacks.onOpenMenu,
                    onSpeedTest = callbacks.onSpeedTest,
                    onToggleSearch = viewModel::toggleSearch,
                    onOpenSubscriptions = callbacks.onOpenSubscriptions,
                    onAddProfile = callbacks.onAddProfile,
                    onOpenMoreMenu = callbacks.onOpenMoreMenu,
                )
            }

            // 2. Overlapping rounded sheet top + inline search + Group Chips
            item(key = "sheet_header", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 14.dp, bottom = 2.dp),
                ) {
                    AnimatedVisibility(
                        visible = state.searchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_apps),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        )
                    }

                    GroupChipsRow(
                        groups = state.groups,
                        selectedGroupId = state.selectedGroupId,
                        onSelectGroup = viewModel::selectGroup,
                    )
                }
            }

            // 3. Empty state
            if (!state.isLoading && state.profiles.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "♪",
                                fontSize = 36.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.group_status_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // 4. Node Cards
                items(
                    items = state.profiles,
                    key = { it.id },
                ) { entity ->
                    val isSelected = entity.id == state.selectedProxyId
                    val horizontalPad = if (state.doubleColumn) 6.dp else 16.dp
                    Box(
                        modifier = Modifier
                            .offset(y = (-20).dp)
                            .padding(horizontal = horizontalPad),
                    ) {
                        ProfileNodeCard(
                            entity = entity,
                            isSelected = isSelected,
                            compact = state.doubleColumn,
                            showAddress = state.alwaysShowAddress && !state.doubleColumn,
                            onSelect = {
                                viewModel.selectProxy(entity)
                                callbacks.onSelectProfile(entity)
                            },
                            onEdit = { callbacks.onEditProfile(entity) },
                            onShareQr = { callbacks.onShareProfileQr(entity) },
                            onCopyLink = { callbacks.onCopyProfileLink(entity) },
                            onDelete = { callbacks.onDeleteProfile(entity) },
                            onShowError = callbacks.onShowError,
                        )
                    }
                }
            }

            // Bottom inset spacer for navigation bar
            item(key = "nav_spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun MikuProfilesHero(
    state: ProfilesUiState,
    onOpenMenu: () -> Unit,
    onSpeedTest: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onAddProfile: () -> Unit,
    onOpenMoreMenu: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradientStart = lerp(primary, Color.White, 0.12f)
    val gradientEnd = lerp(primary, Color.Black, 0.30f)

    val greetingRes = remember { timeGreetingRes() }
    val displayName = state.customProfileName.ifBlank {
        stringResource(R.string.uwu_banner_title)
    }
    val heroTitle = stringResource(R.string.uwu_banner_title_custom, displayName)
    val activeGroup = state.groups.firstOrNull { it.id == state.selectedGroupId }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(gradientStart, primary, gradientEnd),
                )
            )
            .statusBarsPadding()
            .padding(bottom = 42.dp),
    ) {
        // Subtle musical note watermark
        Text(
            text = "♪",
            fontSize = 112.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.11f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 96.dp, bottom = 6.dp),
        )

        // Miku character cutout (synced with Settings Banner Character)
        Image(
            painter = painterResource(id = state.bannerCharacterRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 10.dp)
                .size(width = 128.dp, height = 132.dp)
                .alpha(0.94f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // Top action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HeroGlassIconButton(
                    iconRes = R.drawable.ic_navigation_menu,
                    contentDescription = stringResource(R.string.menu_configuration),
                    onClick = onOpenMenu,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroGlassIconButton(
                        iconRes = R.drawable.ic_search_black_24,
                        contentDescription = stringResource(R.string.search_apps),
                        onClick = onToggleSearch,
                    )
                    HeroGlassIconButton(
                        iconRes = R.drawable.ic_youtube_subscription,
                        contentDescription = stringResource(R.string.menu_group),
                        onClick = onOpenSubscriptions,
                    )
                    HeroGlassIconButton(
                        iconRes = R.drawable.ic_action_note_add,
                        contentDescription = stringResource(R.string.add_profile),
                        onClick = onAddProfile,
                    )
                    HeroGlassIconButton(
                        iconRes = R.drawable.ic_baseline_more_vert_24,
                        contentDescription = stringResource(R.string.action_group_setting_1),
                        onClick = onOpenMoreMenu,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Time-based greeting kicker
            Text(
                text = stringResource(greetingRes).trim(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )

            // "Hi, master" expressive title
            Text(
                text = heroTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 112.dp),
            )

            Spacer(Modifier.height(10.dp))

            // Live summary glass pills
            Row(
                modifier = Modifier.padding(end = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.selectedProxyName.isNotBlank()) {
                    HeroGlassPill(
                        text = state.selectedProxyName,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (activeGroup != null) {
                    HeroGlassPill(
                        text = stringResource(R.string.sub_summary_nodes, activeGroup.count),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroGlassIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HeroGlassPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun GroupChipsRow(
    groups: List<ProfileGroupChip>,
    selectedGroupId: Long,
    onSelectGroup: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = groups, key = { it.id }) { grp ->
            val selected = grp.id == selectedGroupId
            val bg = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val fg = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val badgeBg = if (selected) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }

            Surface(
                onClick = { onSelectGroup(grp.id) },
                shape = CircleShape,
                color = bg,
                contentColor = fg,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = grp.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                        contentColor = fg,
                    ) {
                        Text(
                            text = grp.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileNodeCard(
    entity: ProxyEntity,
    isSelected: Boolean,
    compact: Boolean,
    showAddress: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShareQr: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    onShowError: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val ext = MikuTheme.extendedColors

    val containerColor = if (isSelected) {
        ext.softPanel
    } else {
        colors.surfaceContainerLow
    }
    val borderColor = if (isSelected) {
        colors.primary.copy(alpha = 0.55f)
    } else {
        colors.outlineVariant.copy(alpha = 0.45f)
    }

    MikuSoftCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        borderColor = borderColor,
        cornerRadius = 20.dp,
        onClick = onSelect,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Glowing selection dot
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) colors.primary
                            else colors.onSurfaceVariant.copy(alpha = 0.28f)
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = entity.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Clean overflow button
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_more_vert_24),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        if (entity.type != ProxyEntity.TYPE_CHAIN && entity.nekoBean == null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_qr_nfc)) },
                                onClick = {
                                    menuExpanded = false
                                    onShareQr()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_clipboard)) },
                                onClick = {
                                    menuExpanded = false
                                    onCopyLink()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = colors.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Protocol pill
                    Surface(
                        shape = CircleShape,
                        color = colors.primary.copy(alpha = 0.12f),
                        contentColor = colors.primary,
                    ) {
                        Text(
                            text = entity.displayType(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    if (showAddress && !compact) {
                        val addr = entity.displayAddress()
                        if (addr.isNotBlank()) {
                            Text(
                                text = addr,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Color-coded latency pill
                LatencyBadge(
                    entity = entity,
                    onShowError = onShowError,
                )
            }
        }
    }
}

@Composable
private fun LatencyBadge(
    entity: ProxyEntity,
    onShowError: (String) -> Unit,
) {
    if (entity.status <= 0) return

    val ext = MikuTheme.extendedColors
    val colors = MaterialTheme.colorScheme

    val (label, bg, fg, clickableError) = when {
        entity.status == 1 && entity.ping > 0 -> {
            val ms = entity.ping
            when {
                ms <= 100 -> Quad(
                    stringResource(R.string.available, ms),
                    ext.success.copy(alpha = 0.15f),
                    ext.success,
                    null,
                )
                ms <= 220 -> Quad(
                    stringResource(R.string.available, ms),
                    ext.warningContainer,
                    ext.warning,
                    null,
                )
                else -> Quad(
                    stringResource(R.string.available, ms),
                    colors.error.copy(alpha = 0.14f),
                    colors.error,
                    null,
                )
            }
        }
        entity.status == 3 -> {
            val err = entity.error ?: "<?>"
            val friendly = Protocols.genFriendlyMsg(err)
            val txt = if (friendly != err) friendly else stringResource(R.string.unavailable)
            Quad(txt, colors.error.copy(alpha = 0.14f), colors.error, err)
        }
        else -> {
            val err = entity.error ?: stringResource(R.string.unavailable)
            Quad(err, colors.error.copy(alpha = 0.14f), colors.error, err)
        }
    }

    Surface(
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        modifier = if (clickableError != null) {
            Modifier.clickable { onShowError(clickableError) }
        } else {
            Modifier
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun timeGreetingRes(): Int {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> R.string.uwu_greeting_morning
        in 12..16 -> R.string.uwu_greeting_afternoon
        in 17..19 -> R.string.uwu_greeting_evening
        in 20..22 -> R.string.uwu_greeting_night
        else -> R.string.uwu_greeting_late_night
    }
}
