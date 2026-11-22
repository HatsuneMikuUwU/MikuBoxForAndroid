package top.nkbe.mikubox.ui.page.route

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

data class RouteScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onCreateRoute: () -> Unit,
    val onManageAssets: () -> Unit,
    val onOpenDocs: () -> Unit,
    val onEditRule: (Long) -> Unit,
    val onToggleRule: (Long, Boolean) -> Unit,
    val onMoveRule: (Long, Int) -> Unit,
    val onDeleteRule: (Long) -> Unit,
    val onResetRules: () -> Unit,
)

@Composable
fun RouteScreen(
    state: RouteUiState,
    callbacks: RouteScreenCallbacks,
) {
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRule by remember { mutableStateOf<RuleItemUi?>(null) }

    val bottomDockDp = LocalMikuBottomDockDp.current
    val enabledCount = remember(state.rules) { state.rules.count { it.enabled } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.app_name),
            title = stringResource(R.string.menu_route),
            subtitle = stringResource(R.string.cag_route),
            onOpenMenu = callbacks.onOpenMenu,
            actions = { heroPalette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_rule_folder_24,
                    contentDescription = stringResource(R.string.route_manage_assets),
                    heroPalette = heroPalette,
                    onClick = callbacks.onManageAssets,
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_refresh_24,
                    contentDescription = stringResource(R.string.route_reset),
                    heroPalette = heroPalette,
                    onClick = { showResetConfirm = true },
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_add_road_24,
                    contentDescription = stringResource(R.string.route_add),
                    heroPalette = heroPalette,
                    onClick = callbacks.onCreateRoute,
                )
            },
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = "$enabledCount / ${state.rules.size}",
                    heroPalette = heroPalette,
                )
                MikuHeroGlassPill(
                    text = stringResource(R.string.route_assets),
                    heroPalette = heroPalette,
                    onClick = callbacks.onManageAssets,
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
                item(key = "route_docs_banner") {
                    RouteDocsBanner(onClick = callbacks.onOpenDocs)
                }

                itemsIndexed(
                    items = state.rules,
                    key = { _, rule -> rule.id },
                ) { index, rule ->
                    RuleCard(
                        rule = rule,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.rules.lastIndex,
                        onToggle = { enabled -> callbacks.onToggleRule(rule.id, enabled) },
                        onEdit = { callbacks.onEditRule(rule.id) },
                        onMoveUp = { callbacks.onMoveRule(rule.id, -1) },
                        onMoveDown = { callbacks.onMoveRule(rule.id, 1) },
                        onRequestDelete = { pendingDeleteRule = rule },
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.route_reset),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_profiles_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        callbacks.onResetRules()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = mikuContrastColor(MaterialTheme.colorScheme.error),
                    ),
                ) {
                    Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    pendingDeleteRule?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            title = {
                Text(
                    text = target.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_route_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteRule = null
                        callbacks.onDeleteRule(target.id)
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
                TextButton(onClick = { pendingDeleteRule = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun RouteDocsBanner(onClick: () -> Unit) {
    MikuSoftCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_info_24)
            Text(
                text = stringResource(R.string.route_warn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: RuleItemUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val (outboundBg, outboundFg) = rememberOutboundBadgeColors(rule.outboundCode)

    MikuSoftCard(
        onClick = { onToggle(!rule.enabled) },
        shape = RoundedCornerShape(22.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = CircleShape,
                            color = outboundBg,
                            contentColor = outboundFg,
                        ) {
                            Text(
                                text = rule.outboundLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if (rule.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = rule.summary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
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
                RuleActionIconChip(
                    iconRes = R.drawable.uwu_arrow_down,
                    contentDescription = "Move Up",
                    enabled = canMoveUp,
                    iconRotation = 180f,
                    onClick = onMoveUp,
                )
                RuleActionIconChip(
                    iconRes = R.drawable.uwu_arrow_down,
                    contentDescription = "Move Down",
                    enabled = canMoveDown,
                    onClick = onMoveDown,
                )

                Spacer(Modifier.weight(1f))

                RuleActionPill(
                    iconRes = R.drawable.ic_image_edit,
                    label = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onEdit,
                )
                RuleActionIconChip(
                    iconRes = R.drawable.ic_action_delete,
                    contentDescription = stringResource(R.string.delete),
                    enabled = true,
                    tint = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    onClick = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun rememberOutboundBadgeColors(outboundCode: Long): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return remember(outboundCode, scheme) {
        when (outboundCode) {
            0L -> {
                val bg = scheme.primary
                bg to mikuContrastColor(bg)
            }
            -1L -> {
                val bg = scheme.secondaryContainer
                bg to mikuContrastColor(bg)
            }
            -2L -> {
                val bg = scheme.errorContainer
                bg to mikuContrastColor(bg)
            }
            else -> {
                val bg = scheme.tertiaryContainer
                bg to mikuContrastColor(bg)
            }
        }
    }
}

@Composable
private fun RuleActionIconChip(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    iconRotation: Float = 0f,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = containerColor,
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
                    .rotate(iconRotation),
            )
        }
    }
}

@Composable
private fun RuleActionPill(
    iconRes: Int,
    label: String,
    tint: Color,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
