package top.nkbe.mikubox.ui.page.sheets

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.rotate
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
import com.neko.widget.BannerSettingsCharacterImageView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.theme.rememberMikuHeroPalette

@Composable
fun MikuSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 18.dp)
                .navigationBarsPadding(),
        ) {
            // M3E drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MikuCompactSheetBanner(
    title: String,
    subtitle: String,
) {
    val palette = rememberMikuHeroPalette()
    val charRes = remember {
        BannerSettingsCharacterImageView.resolveDrawable(DataStore.bannerSettingsCharacter)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(palette.gradientStart, palette.gradientMid, palette.gradientEnd)
                )
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Image(
            painter = painterResource(id = charRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(64.dp)
                .alpha(0.9f),
        )
        Column(modifier = Modifier.padding(end = 68.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.onHeroMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OtherMenuSheetContent(
    currentOrder: Int,
    onActionClick: (Int) -> Unit,
) {
    var serverExpanded by remember { mutableStateOf(false) }
    var testExpanded by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme

    MikuSheetContainer {
        MikuCompactSheetBanner(
            title = stringResource(R.string.menu_configuration),
            subtitle = stringResource(R.string.uwu_author_summary),
        )

        Spacer(Modifier.height(14.dp))

        // Quick 2-column expandable / action cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetExpandToggleTile(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_action_dns,
                title = stringResource(R.string.action_group_setting_1),
                expanded = serverExpanded,
                onClick = { serverExpanded = !serverExpanded },
            )
            SheetExpandToggleTile(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_baseline_speed_24,
                title = stringResource(R.string.action_group_setting_2),
                expanded = testExpanded,
                onClick = { testExpanded = !testExpanded },
            )
        }

        AnimatedVisibility(
            visible = serverExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            MikuSoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                cornerRadius = 20.dp,
                containerColor = colors.surfaceContainerLow,
            ) {
                SheetActionRow(
                    iconRes = R.drawable.ic_baseline_update_24,
                    label = stringResource(R.string.update_current_subscription),
                    onClick = { onActionClick(R.id.action_update_subscription) },
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                SheetActionRow(
                    iconRes = R.drawable.ic_delete_empty,
                    label = stringResource(R.string.clear_traffic_statistics),
                    onClick = { onActionClick(R.id.action_clear_traffic_statistics) },
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                SheetActionRow(
                    iconRes = R.drawable.ic_action_delete,
                    label = stringResource(R.string.remove_duplicate),
                    onClick = { onActionClick(R.id.action_remove_duplicate) },
                )
            }
        }

        AnimatedVisibility(
            visible = testExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            MikuSoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                cornerRadius = 20.dp,
                containerColor = colors.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TonalActionPill(
                            modifier = Modifier.weight(1f),
                            iconRes = R.drawable.ic_baseline_cast_connected_24,
                            label = stringResource(R.string.connection_test_tcp_ping),
                            highlighted = false,
                            onClick = { onActionClick(R.id.action_connection_tcp_ping) },
                        )
                        TonalActionPill(
                            modifier = Modifier.weight(1f),
                            iconRes = R.drawable.ic_baseline_speed_24,
                            label = stringResource(R.string.connection_test_url_test),
                            highlighted = true,
                            onClick = { onActionClick(R.id.action_connection_url_test) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SheetActionRow(
                        iconRes = R.drawable.ic_delete_empty,
                        label = stringResource(R.string.connection_test_clear_results),
                        onClick = { onActionClick(R.id.action_connection_test_clear_results) },
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))
                    SheetActionRow(
                        iconRes = R.drawable.ic_action_delete,
                        label = stringResource(R.string.connection_test_delete_unavailable),
                        onClick = { onActionClick(R.id.action_connection_test_delete_unavailable) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sort Order M3E Card
        MikuSoftCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = colors.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.group_order),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                SheetRadioRow(
                    iconRes = R.drawable.ic_baseline_low_priority_24,
                    label = stringResource(R.string.group_order_origin),
                    selected = currentOrder == GroupOrder.ORIGIN,
                    onClick = { onActionClick(R.id.action_order_origin) },
                )
                SheetRadioRow(
                    iconRes = R.drawable.ic_order_alphabetical_ascending,
                    label = stringResource(R.string.group_order_by_name),
                    selected = currentOrder == GroupOrder.BY_NAME,
                    onClick = { onActionClick(R.id.action_order_by_name) },
                )
                SheetRadioRow(
                    iconRes = R.drawable.ic_baseline_timelapse_24,
                    label = stringResource(R.string.group_order_by_delay),
                    selected = currentOrder == GroupOrder.BY_DELAY,
                    onClick = { onActionClick(R.id.action_order_by_delay) },
                )
            }
        }
    }
}

@Composable
fun ProfileMenuSheetContent(
    onActionClick: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val manualItems = remember {
        listOf(
            Triple(R.id.action_new_vless, "VLESS", R.drawable.baseline_flight_takeoff_24),
            Triple(R.id.action_new_vmess, null, R.drawable.ic_baseline_cast_connected_24),
            Triple(R.id.action_new_ss, null, R.drawable.ic_action_lock),
            Triple(R.id.action_new_trojan, null, R.drawable.ic_baseline_security_24),
            Triple(R.id.action_new_hysteria, null, R.drawable.ic_baseline_speed_24),
            Triple(R.id.action_new_tuic, null, R.drawable.ic_baseline_stream_24),
            Triple(R.id.action_new_anytls, null, R.drawable.ic_notification_enhanced_encryption),
            Triple(R.id.action_new_shadowtls, null, R.drawable.ic_certificate_outline),
            Triple(R.id.action_new_wg, null, R.drawable.ic_baseline_vpn_key_24),
            Triple(R.id.action_new_socks, null, R.drawable.ic_maps_directions_boat),
            Triple(R.id.action_new_http, null, R.drawable.ic_wan),
            Triple(R.id.action_new_ssh, null, R.drawable.ic_device_developer_mode),
            Triple(R.id.action_new_naive, null, R.drawable.ic_baseline_layers_24),
            Triple(R.id.action_new_mieru, null, R.drawable.ic_baseline_transform_24),
            Triple(R.id.action_new_juicity, null, R.drawable.ic_baseline_compare_arrows_24),
            Triple(R.id.action_new_trojan_go, null, R.drawable.ic_baseline_security_24),
            Triple(R.id.action_new_config, null, R.drawable.ic_code_braces),
            Triple(R.id.action_new_chain, null, R.drawable.ic_baseline_link_24),
        )
    }

    MikuSheetContainer {
        Text(
            text = stringResource(R.string.add_profile),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        // Quick import 3-up pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TonalActionPill(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_action_qr,
                label = stringResource(R.string.add_profile_methods_scan_qr_code),
                highlighted = true,
                onClick = { onActionClick(R.id.action_scan_qr_code) },
            )
            TonalActionPill(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_content_paste,
                label = stringResource(R.string.action_import),
                highlighted = false,
                onClick = { onActionClick(R.id.action_import_clipboard) },
            )
            TonalActionPill(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.ic_action_file,
                label = stringResource(R.string.action_import_file),
                highlighted = false,
                onClick = { onActionClick(R.id.action_import_file) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.add_profile_methods_manual_settings),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val rows = manualItems.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { (id, rawLabel, iconRes) ->
                        val title = rawLabel ?: when (id) {
                            R.id.action_new_vmess -> stringResource(R.string.action_vmess)
                            R.id.action_new_ss -> stringResource(R.string.action_shadowsocks)
                            R.id.action_new_trojan -> stringResource(R.string.action_trojan)
                            R.id.action_new_hysteria -> stringResource(R.string.action_hysteria)
                            R.id.action_new_tuic -> stringResource(R.string.action_tuic)
                            R.id.action_new_anytls -> stringResource(R.string.action_anytls)
                            R.id.action_new_shadowtls -> stringResource(R.string.action_shadowtls)
                            R.id.action_new_wg -> stringResource(R.string.action_wireguard)
                            R.id.action_new_socks -> stringResource(R.string.action_socks)
                            R.id.action_new_http -> stringResource(R.string.action_http)
                            R.id.action_new_ssh -> stringResource(R.string.action_ssh)
                            R.id.action_new_naive -> stringResource(R.string.action_naive)
                            R.id.action_new_mieru -> stringResource(R.string.action_mieru)
                            R.id.action_new_juicity -> stringResource(R.string.action_juicity)
                            R.id.action_new_trojan_go -> stringResource(R.string.action_trojan_go)
                            R.id.action_new_config -> stringResource(R.string.custom_config)
                            R.id.action_new_chain -> stringResource(R.string.proxy_chain)
                            else -> ""
                        }
                        MikuSoftCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            containerColor = colors.surfaceContainerLow,
                            onClick = { onActionClick(id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            tint = colors.primary,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class NavDestination(
    val id: Int,
    val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

@Composable
fun MikuNavigationSheetContent(
    selectedNavId: Int,
    showTraffic: Boolean,
    onSelectNav: (Int) -> Unit,
) {
    val primaryItems = remember {
        listOf(
            NavDestination(R.id.nav_configuration, R.string.menu_configuration, R.drawable.ic_baseline_home_24),
            NavDestination(R.id.nav_group, R.string.menu_group, R.drawable.ic_youtube_subscription),
            NavDestination(R.id.nav_route, R.string.menu_route, R.drawable.ic_baseline_add_road_24),
            NavDestination(R.id.nav_settings, R.string.settings, R.drawable.ic_action_settings),
        )
    }
    val secondaryItems = remember(showTraffic) {
        buildList {
            if (showTraffic) {
                add(NavDestination(R.id.nav_traffic, R.string.menu_traffic, R.drawable.ic_chart_line))
            }
            add(NavDestination(R.id.nav_tools, R.string.menu_tools, R.drawable.baseline_construction_24))
            add(NavDestination(R.id.nav_logcat, R.string.menu_log, R.drawable.ic_baseline_bug_report_24))
            add(NavDestination(R.id.nav_about, R.string.menu_about, R.drawable.ic_baseline_info_24))
        }
    }

    MikuSheetContainer {
        MikuCompactSheetBanner(
            title = "${stringResource(R.string.app_name)} · ${BuildConfig.VERSION_NAME}",
            subtitle = stringResource(R.string.uwu_author_summary),
        )

        Spacer(Modifier.height(14.dp))

        // Primary 2x2 navigation grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            primaryItems.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { dest ->
                        val active = dest.id == selectedNavId
                        val bg = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                        val fg = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        MikuSoftCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 18.dp,
                            containerColor = bg,
                            borderColor = if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            },
                            onClick = { onSelectNav(dest.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = dest.iconRes),
                                    contentDescription = null,
                                    tint = if (active) MaterialTheme.colorScheme.primary else fg,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(dest.labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = fg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        MikuSoftCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            secondaryItems.forEachIndexed { idx, dest ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                SheetActionRow(
                    iconRes = dest.iconRes,
                    label = stringResource(dest.labelRes),
                    selected = dest.id == selectedNavId,
                    onClick = { onSelectNav(dest.id) },
                )
            }
        }
    }
}

@Composable
private fun SheetExpandToggleTile(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rot by animateFloatAsState(if (expanded) 90f else 0f, label = "ArrowRot")
    MikuSoftCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        containerColor = if (expanded) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
        borderColor = if (expanded) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant.copy(alpha = 0.45f),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = "›",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.rotate(rot),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.expandable_summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TonalActionPill(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (highlighted) colors.primary else colors.secondaryContainer
    val fg = if (highlighted) colors.onPrimary else colors.onSecondaryContainer
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        contentColor = fg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetRadioRow(
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}
