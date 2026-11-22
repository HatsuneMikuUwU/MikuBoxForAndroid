package top.nkbe.mikubox.ui.page.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSegmentedTabs
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.MikuTheme
import kotlin.math.roundToInt

data class SettingsScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onOpenAppManager: () -> Unit,
    val onOpenGlobalCustomConfig: () -> Unit,
    val onSetAutoConnect: (Boolean) -> Unit,
    val onSetServiceMode: (String) -> Unit,
    val onSetTunImplementation: (Int) -> Unit,
    val onSetMtu: (Int) -> Unit,
    val onSetSpeedInterval: (Int) -> Unit,
    val onSetProfileTrafficStatistics: (Boolean) -> Unit,
    val onSetShowDirectSpeed: (Boolean) -> Unit,
    val onSetShowGroupInNotification: (Boolean) -> Unit,
    val onSetConfirmProfileDelete: (Boolean) -> Unit,
    val onSetAlwaysShowAddress: (Boolean) -> Unit,
    val onSetMeteredNetwork: (Boolean) -> Unit,
    val onSetAcquireWakeLock: (Boolean) -> Unit,
    val onSetHideFromRecentApps: (Boolean) -> Unit,
    val onSetLogLevel: (Int) -> Unit,
    val onSetLogBufSize: (Int) -> Unit,
    val onSetProxyApps: (Boolean) -> Unit,
    val onSetBypassLan: (Boolean) -> Unit,
    val onSetBypassLanInCore: (Boolean) -> Unit,
    val onSetTrafficSniffing: (Int) -> Unit,
    val onSetResolveDestination: (Boolean) -> Unit,
    val onSetIpv6Mode: (Int) -> Unit,
    val onSetRulesProvider: (Int) -> Unit,
    val onSetRulesGeositeUrl: (String) -> Unit,
    val onSetRulesGeoipUrl: (String) -> Unit,
    val onSetRemoteDns: (String) -> Unit,
    val onSetDomainStrategyRemote: (String) -> Unit,
    val onSetDirectDns: (String) -> Unit,
    val onSetDomainStrategyServer: (String) -> Unit,
    val onSetEnableDnsRouting: (Boolean) -> Unit,
    val onSetEnableFakeDns: (Boolean) -> Unit,
    val onSetMixedPort: (Int) -> Unit,
    val onSetAppendHttpProxy: (Boolean) -> Unit,
    val onSetAllowAccess: (Boolean) -> Unit,
    val onSetConnectionTestConfig: (String, Int) -> Unit,
    val onSetEnableClashApi: (Boolean) -> Unit,
    val onSetNetworkChangeResetConnections: (Boolean) -> Unit,
    val onSetWakeResetConnections: (Boolean) -> Unit,
    val onSetGlobalAllowInsecure: (Boolean) -> Unit,
    val onSetAllowInsecureOnRequest: (Boolean) -> Unit,
    val onSetAppTlsVersion: (String) -> Unit,
    val onClearCache: () -> Unit,

    // Wardrobe callbacks
    val onCheckUpdate: () -> Unit,
    val onSetAppTheme: (Int) -> Unit,
    val onSetDynamicTheme: (Boolean) -> Unit,
    val onSetNightTheme: (Int) -> Unit,
    val onSetTrueBlack: (Boolean) -> Unit,
    val onSetBoldFont: (Boolean) -> Unit,
    val onSetFontScale: (Int) -> Unit,
    val onApplyDpi: (Int) -> Unit,
    val onResetDpi: () -> Unit,
    val onSetLanguage: (String) -> Unit,
    val onSetSoundOnConnect: (Boolean) -> Unit,
    val onSetShowSplashScreen: (Boolean) -> Unit,
    val onSetShowWelcomeAnim: (Boolean) -> Unit,
    val onSetBannerCharacter: (String) -> Unit,
    val onSetCustomProfileName: (String) -> Unit,
    val onSetShowBottomBar: (Boolean) -> Unit,
    val onSetBlurBottomStatus: (Boolean) -> Unit,
    val onSetBlurBottomBlobAnim: (Boolean) -> Unit,
    val onSetBlurBottomRadius: (Int) -> Unit,
    val onSetBlurBottomAlpha: (Int) -> Unit,
    val onSetConnectionTestWithIp: (Boolean) -> Unit,
    val onSetShowIpInTwoLine: (Boolean) -> Unit,
    val onSetShowFabTimer: (Boolean) -> Unit,
    val onSetFabStyle: (Int) -> Unit,
    val onSetDoubleColumnProfileList: (Boolean) -> Unit,
    val onSetShowQuickActions: (Boolean) -> Unit,
    val onSetShowSnowflakes: (Boolean) -> Unit,
    val onSetDisableBottomSheet: (Boolean) -> Unit,
    val onSetDisableBottomSheetHome: (Boolean) -> Unit,
    val onSetDisableParticlesSheet: (Boolean) -> Unit,
    val onPickSheetBanner: () -> Unit,
    val onDeleteSheetBanner: () -> Unit,
    val onToggleWeatherInfo: (Boolean) -> Unit,
    val onSetManualWeatherEnabled: (Boolean) -> Unit,
    val onSetManualWeatherCity: (String) -> Unit,
    val onSetShowWeatherCondition: (Boolean) -> Unit,
    val onSetHideWeatherCity: (Boolean) -> Unit,
)

private sealed interface ActiveSettingDialog {
    data class SingleChoice(
        val title: String,
        val options: List<ChoiceOption>,
        val selectedValue: String,
        val subtitle: String? = null,
        val extraActionLabel: String? = null,
        val onExtraAction: (() -> Unit)? = null,
        val onSelect: (String) -> Unit,
    ) : ActiveSettingDialog

    data class TextInput(
        val title: String,
        val initialValue: String,
        val label: String? = null,
        val hint: String? = null,
        val keyboardType: KeyboardType = KeyboardType.Text,
        val onConfirm: (String) -> Unit,
    ) : ActiveSettingDialog

    data object ColorPicker : ActiveSettingDialog
    data object MtuPicker : ActiveSettingDialog
    data object UrlTestConfig : ActiveSettingDialog
    data object DpiConfig : ActiveSettingDialog

    data class Confirm(
        val title: String,
        val message: String,
        val confirmText: String,
        val dismissText: String,
        val onConfirm: () -> Unit,
    ) : ActiveSettingDialog
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    callbacks: SettingsScreenCallbacks,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeDialog by remember { mutableStateOf<ActiveSettingDialog?>(null) }

    val bottomDockDp = LocalMikuBottomDockDp.current
    val listBottomPadding = if (bottomDockDp > 0.dp) bottomDockDp + 24.dp else 160.dp

    val resources = context.resources

    // Resolve lookup tables cleanly
    val serviceModes = remember(context) {
        val entries = resources.getStringArray(R.array.service_modes)
        val values = resources.getStringArray(R.array.service_mode_values)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val tunModes = remember(context) {
        val entries = resources.getStringArray(R.array.tun_implementation)
        val values = resources.getStringArray(R.array.int_array_3)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val speedIntervals = remember(context) {
        val entries = resources.getStringArray(R.array.notification_entry)
        val values = resources.getStringArray(R.array.notification_value)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val logLevels = remember(context) {
        val entries = resources.getStringArray(R.array.log_level)
        val values = resources.getStringArray(R.array.int_array_5)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val sniffingOptions = remember(context) {
        val entries = resources.getStringArray(R.array.traffic_sniffing_values)
        val values = resources.getStringArray(R.array.int_array_2)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val ipv6Options = remember(context) {
        val entries = resources.getStringArray(R.array.ipv6_mode)
        val values = resources.getStringArray(R.array.int_array_4)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val rulesProviders = remember(context) {
        val entries = resources.getStringArray(R.array.rules_dat_provider)
        val values = resources.getStringArray(R.array.int_array_6)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val dnsNetworkOptions = remember(context) {
        resources.getStringArray(R.array.dns_network_select).map { ChoiceOption(it, it) }
    }
    val tlsVersions = remember(context) {
        resources.getStringArray(R.array.app_tls_version).map { ChoiceOption(it, it) }
    }
    val nightModes = remember(context) {
        val entries = resources.getStringArray(R.array.night_mode)
        val values = resources.getStringArray(R.array.int_array_4)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val languageOptions = remember(context) {
        val entries = resources.getStringArray(R.array.language_entry)
        val values = resources.getStringArray(R.array.language_value)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val characterOptions = remember(context) {
        val entries = resources.getStringArray(R.array.banner_character_entries)
        val values = resources.getStringArray(R.array.banner_character_values)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }
    val fabStyleOptions = remember(context) {
        val entries = resources.getStringArray(R.array.fab_style_entry)
        val values = resources.getStringArray(R.array.int_array_2)
        entries.zip(values).map { ChoiceOption(it.first, it.second) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
        ) {
            item(key = "settings_hero") {
                MikuSettingsHero(
                    state = state,
                    selectedTab = selectedTab,
                    onOpenMenu = callbacks.onOpenMenu,
                )
            }

            item(key = "settings_sheet_tabs") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 4.dp),
                ) {
                    MikuSegmentedTabs(
                        tabs = listOf(
                            stringResource(R.string.settings),
                            stringResource(R.string.uwu_miku_ui),
                        ),
                        selectedIndex = selectedTab,
                        onSelectTab = { selectedTab = it },
                    )
                }
            }

            if (selectedTab == 0) {
                item(key = "general_tab_content") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-12).dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Subtle Maintainer Credit Pill (credits both authors clearly)
                        MikuMaintainerCard()

                        // 1. General Settings
                        SettingsGroupSection(title = stringResource(R.string.general_settings)) {
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_communication_phonelink_ring,
                                title = stringResource(R.string.auto_connect),
                                subtitle = stringResource(R.string.auto_connect_summary),
                                checked = state.persistAcrossReboot,
                                onCheckedChange = callbacks.onSetAutoConnect,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_device_developer_mode,
                                title = stringResource(R.string.service_mode),
                                valueText = serviceModes.labelFor(state.serviceMode),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.service_mode),
                                        options = serviceModes,
                                        selectedValue = state.serviceMode,
                                        onSelect = callbacks.onSetServiceMode,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_flip_camera_android_24,
                                title = stringResource(R.string.tun_implementation),
                                valueText = tunModes.labelFor(state.tunImplementation.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.tun_implementation),
                                        options = tunModes,
                                        selectedValue = state.tunImplementation.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetTunImplementation) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.baseline_public_24,
                                title = stringResource(R.string.mtu),
                                valueText = state.mtu.toString(),
                                onClick = { activeDialog = ActiveSettingDialog.MtuPicker },
                                onLongClick = { activeDialog = ActiveSettingDialog.MtuPicker },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_shutter_speed_24,
                                title = stringResource(R.string.speed_interval),
                                valueText = speedIntervals.labelFor(state.speedInterval.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.speed_interval),
                                        options = speedIntervals,
                                        selectedValue = state.speedInterval.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetSpeedInterval) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_traffic_cone,
                                title = stringResource(R.string.profile_traffic_statistics),
                                subtitle = stringResource(R.string.profile_traffic_statistics_summary),
                                checked = state.profileTrafficStatistics,
                                enabled = state.speedInterval != 0,
                                onCheckedChange = callbacks.onSetProfileTrafficStatistics,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_speed_24,
                                title = stringResource(R.string.show_direct_speed),
                                subtitle = stringResource(R.string.show_direct_speed_sum),
                                checked = state.showDirectSpeed,
                                onCheckedChange = callbacks.onSetShowDirectSpeed,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_bell_ring,
                                title = stringResource(R.string.show_group_in_notification),
                                checked = state.showGroupInNotification,
                                onCheckedChange = callbacks.onSetShowGroupInNotification,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_action_delete,
                                title = stringResource(R.string.title_pref_confirm_remove),
                                subtitle = stringResource(R.string.summary_pref_confirm_remove),
                                checked = state.confirmProfileDelete,
                                onCheckedChange = callbacks.onSetConfirmProfileDelete,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_center_focus_weak_24,
                                title = stringResource(R.string.always_show_address),
                                subtitle = stringResource(R.string.always_show_address_sum),
                                checked = state.alwaysShowAddress,
                                onCheckedChange = callbacks.onSetAlwaysShowAddress,
                            )
                            if (Build.VERSION.SDK_INT >= 28) {
                                SettingsGroupDivider()
                                SettingsSwitchRow(
                                    iconRes = R.drawable.ic_device_data_usage,
                                    title = stringResource(R.string.metered),
                                    subtitle = stringResource(R.string.metered_summary),
                                    checked = state.meteredNetwork,
                                    onCheckedChange = callbacks.onSetMeteredNetwork,
                                )
                            }
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.baseline_developer_board_24,
                                title = stringResource(R.string.acquire_wake_lock),
                                subtitle = stringResource(R.string.acquire_wake_lock_summary),
                                checked = state.acquireWakeLock,
                                onCheckedChange = callbacks.onSetAcquireWakeLock,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_eye_off,
                                title = stringResource(R.string.hide_from_recent_apps),
                                subtitle = stringResource(R.string.hide_from_recent_apps_summary),
                                checked = state.hideFromRecentApps,
                                onCheckedChange = callbacks.onSetHideFromRecentApps,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_bug_report_24,
                                title = stringResource(R.string.log_level),
                                subtitle = stringResource(R.string.log_level_help),
                                valueText = logLevels.labelFor(state.logLevel.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.log_level),
                                        subtitle = context.getString(R.string.log_level_help),
                                        options = logLevels,
                                        selectedValue = state.logLevel.toString(),
                                        extraActionLabel = "Buffer KB (${state.logBufSize.takeIf { it > 0 } ?: 50})",
                                        onExtraAction = {
                                            activeDialog = ActiveSettingDialog.TextInput(
                                                title = "Log buffer size (kb)",
                                                initialValue = (state.logBufSize.takeIf { it > 0 } ?: 50).toString(),
                                                keyboardType = KeyboardType.Number,
                                                onConfirm = { raw ->
                                                    val size = raw.toIntOrNull()?.takeIf { it > 0 } ?: 50
                                                    callbacks.onSetLogBufSize(size)
                                                },
                                            )
                                        },
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetLogLevel) },
                                    )
                                },
                                onLongClick = {
                                    activeDialog = ActiveSettingDialog.TextInput(
                                        title = "Log buffer size (kb)",
                                        initialValue = (state.logBufSize.takeIf { it > 0 } ?: 50).toString(),
                                        keyboardType = KeyboardType.Number,
                                        onConfirm = { raw ->
                                            val size = raw.toIntOrNull()?.takeIf { it > 0 } ?: 50
                                            callbacks.onSetLogBufSize(size)
                                        },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_layers_24,
                                title = stringResource(R.string.custom_config),
                                valueText = if (state.hasGlobalCustomConfig) "✓" else null,
                                onClick = callbacks.onOpenGlobalCustomConfig,
                            )
                        }

                        // 2. Route Settings
                        SettingsGroupSection(title = stringResource(R.string.cag_route)) {
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_navigation_apps,
                                title = stringResource(R.string.proxied_apps),
                                subtitle = stringResource(R.string.proxied_apps_summary),
                                checked = state.proxyApps,
                                onCheckedChange = callbacks.onSetProxyApps,
                                onRowClick = callbacks.onOpenAppManager,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_legend_toggle_24,
                                title = stringResource(R.string.route_opt_bypass_lan),
                                checked = state.bypassLan,
                                onCheckedChange = callbacks.onSetBypassLan,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_lan,
                                title = stringResource(R.string.bypass_lan_in_core),
                                checked = state.bypassLanInCore,
                                onCheckedChange = callbacks.onSetBypassLanInCore,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_manage_search_24,
                                title = stringResource(R.string.traffic_sniffing),
                                valueText = sniffingOptions.labelFor(state.trafficSniffing.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.traffic_sniffing),
                                        options = sniffingOptions,
                                        selectedValue = state.trafficSniffing.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetTrafficSniffing) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.baseline_wrap_text_24,
                                title = stringResource(R.string.resolve_destination),
                                subtitle = stringResource(R.string.resolve_destination_summary),
                                checked = state.resolveDestination,
                                onCheckedChange = callbacks.onSetResolveDestination,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_image_looks_6,
                                title = stringResource(R.string.ipv6),
                                valueText = ipv6Options.labelFor(state.ipv6Mode.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.ipv6),
                                        options = ipv6Options,
                                        selectedValue = state.ipv6Mode.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetIpv6Mode) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_rule_folder_24,
                                title = stringResource(R.string.route_rules_provider),
                                valueText = rulesProviders.labelFor(state.rulesProvider.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.route_rules_provider),
                                        options = rulesProviders,
                                        selectedValue = state.rulesProvider.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetRulesProvider) },
                                    )
                                },
                            )
                            AnimatedVisibility(
                                visible = state.rulesProvider == 5,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    SettingsGroupDivider()
                                    SettingsClickRow(
                                        iconRes = R.drawable.ic_cloud_download,
                                        title = stringResource(R.string.rules_geosite_url),
                                        subtitle = state.rulesGeositeUrl,
                                        onClick = {
                                            activeDialog = ActiveSettingDialog.TextInput(
                                                title = context.getString(R.string.rules_geosite_url),
                                                initialValue = state.rulesGeositeUrl,
                                                keyboardType = KeyboardType.Uri,
                                                onConfirm = callbacks.onSetRulesGeositeUrl,
                                            )
                                        },
                                    )
                                    SettingsGroupDivider()
                                    SettingsClickRow(
                                        iconRes = R.drawable.ic_cloud_download,
                                        title = stringResource(R.string.rules_geoip_url),
                                        subtitle = state.rulesGeoipUrl,
                                        onClick = {
                                            activeDialog = ActiveSettingDialog.TextInput(
                                                title = context.getString(R.string.rules_geoip_url),
                                                initialValue = state.rulesGeoipUrl,
                                                keyboardType = KeyboardType.Uri,
                                                onConfirm = callbacks.onSetRulesGeoipUrl,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // 3. DNS Settings
                        SettingsGroupSection(title = stringResource(R.string.cag_dns)) {
                            SettingsClickRow(
                                iconRes = R.drawable.ic_action_dns,
                                title = stringResource(R.string.remote_dns),
                                subtitle = state.remoteDns,
                                onClick = {
                                    activeDialog = ActiveSettingDialog.TextInput(
                                        title = context.getString(R.string.remote_dns),
                                        initialValue = state.remoteDns,
                                        keyboardType = KeyboardType.Uri,
                                        onConfirm = callbacks.onSetRemoteDns,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_domain,
                                title = stringResource(R.string.domain_strategy_for_remote),
                                valueText = state.domainStrategyForRemote,
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.domain_strategy_for_remote),
                                        options = dnsNetworkOptions,
                                        selectedValue = state.domainStrategyForRemote,
                                        onSelect = callbacks.onSetDomainStrategyRemote,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_action_dns,
                                title = stringResource(R.string.direct_dns),
                                subtitle = state.directDns,
                                onClick = {
                                    activeDialog = ActiveSettingDialog.TextInput(
                                        title = context.getString(R.string.direct_dns),
                                        initialValue = state.directDns,
                                        keyboardType = KeyboardType.Uri,
                                        onConfirm = callbacks.onSetDirectDns,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_domain,
                                title = stringResource(R.string.domain_strategy_for_server),
                                valueText = state.domainStrategyForServer,
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.domain_strategy_for_server),
                                        options = dnsNetworkOptions,
                                        selectedValue = state.domainStrategyForServer,
                                        onSelect = callbacks.onSetDomainStrategyServer,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_camera_24,
                                title = stringResource(R.string.enable_dns_routing),
                                subtitle = stringResource(R.string.dns_routing_message),
                                checked = state.enableDnsRouting,
                                onCheckedChange = callbacks.onSetEnableDnsRouting,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_action_lock,
                                title = stringResource(R.string.enable_fakedns),
                                subtitle = stringResource(R.string.fakedns_message),
                                checked = state.enableFakeDns,
                                onCheckedChange = callbacks.onSetEnableFakeDns,
                            )
                        }

                        // 4. Inbound Settings
                        SettingsGroupSection(title = stringResource(R.string.inbound_settings)) {
                            SettingsClickRow(
                                iconRes = R.drawable.ic_maps_directions_boat,
                                title = stringResource(R.string.port_proxy),
                                valueText = state.mixedPort.toString(),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.TextInput(
                                        title = context.getString(R.string.port_proxy),
                                        initialValue = state.mixedPort.toString(),
                                        keyboardType = KeyboardType.Number,
                                        onConfirm = { raw ->
                                            val port = raw.toIntOrNull()?.coerceIn(1, 65535) ?: return@TextInput
                                            callbacks.onSetMixedPort(port)
                                        },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_wan,
                                title = stringResource(R.string.append_http_proxy),
                                subtitle = stringResource(R.string.append_http_proxy_sum),
                                checked = state.appendHttpProxy,
                                onCheckedChange = callbacks.onSetAppendHttpProxy,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_nat_24,
                                title = stringResource(R.string.allow_access),
                                subtitle = stringResource(R.string.allow_access_sum),
                                checked = state.allowAccess,
                                onCheckedChange = callbacks.onSetAllowAccess,
                            )
                        }

                        // 5. Misc Settings
                        SettingsGroupSection(title = stringResource(R.string.cag_misc)) {
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_cast_connected_24,
                                title = stringResource(R.string.connection_test_url),
                                subtitle = "${state.connectionTestURL} · ${state.connectionTestConcurrent}x",
                                onClick = { activeDialog = ActiveSettingDialog.UrlTestConfig },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.baseline_construction_24,
                                title = stringResource(R.string.enable_clash_api),
                                subtitle = stringResource(R.string.enable_clash_api_summary),
                                checked = state.enableClashAPI,
                                onCheckedChange = callbacks.onSetEnableClashApi,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_flip_camera_android_24,
                                title = stringResource(R.string.network_change_reset_connections),
                                checked = state.networkChangeResetConnections,
                                onCheckedChange = callbacks.onSetNetworkChangeResetConnections,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_flip_camera_android_24,
                                title = stringResource(R.string.wake_reset_connections),
                                checked = state.wakeResetConnections,
                                onCheckedChange = callbacks.onSetWakeResetConnections,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_action_lock_open,
                                title = stringResource(R.string.global_allow_insecure),
                                checked = state.globalAllowInsecure,
                                onCheckedChange = callbacks.onSetGlobalAllowInsecure,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_certificate_outline,
                                title = stringResource(R.string.allow_insecure_on_request_sum),
                                checked = state.allowInsecureOnRequest,
                                onCheckedChange = callbacks.onSetAllowInsecureOnRequest,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_youtube_subscription,
                                title = stringResource(R.string.app_tls_version),
                                valueText = state.appTLSVersion,
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.app_tls_version),
                                        options = tlsVersions,
                                        selectedValue = state.appTLSVersion,
                                        onSelect = callbacks.onSetAppTlsVersion,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_action_delete,
                                title = stringResource(R.string.clear_cache),
                                subtitle = stringResource(R.string.clear_cache_summary),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.Confirm(
                                        title = context.getString(R.string.clear_cache),
                                        message = context.getString(R.string.clear_cache_confirm),
                                        confirmText = context.getString(android.R.string.ok),
                                        dismissText = context.getString(android.R.string.cancel),
                                        onConfirm = callbacks.onClearCache,
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                item(key = "wardrobe_tab_content") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-12).dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Sleek MikuBox Info + Co-Authors + Check Update Card
                        MikuWardrobeIdentityCard(
                            state = state,
                            onCheckUpdate = callbacks.onCheckUpdate,
                        )

                        // 1. Theme & Colors
                        SettingsGroupSection(title = stringResource(R.string.theme_category)) {
                            SettingsThemeColorRow(
                                enabled = !state.isDynamicTheme,
                                onClick = { activeDialog = ActiveSettingDialog.ColorPicker },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.pref_dynamic_theme_title),
                                subtitle = if (state.supportsDynamicTheme) {
                                    stringResource(R.string.pref_dynamic_theme_summary)
                                } else {
                                    stringResource(R.string.dynamic_theme_min_android_12)
                                },
                                checked = state.isDynamicTheme,
                                enabled = state.supportsDynamicTheme,
                                onCheckedChange = callbacks.onSetDynamicTheme,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_wb_sunny_24,
                                title = stringResource(R.string.night_mode),
                                valueText = nightModes.labelFor(state.nightTheme.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.night_mode),
                                        options = nightModes,
                                        selectedValue = state.nightTheme.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetNightTheme) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.pref_true_black_title),
                                subtitle = if (state.usingNightMode) {
                                    stringResource(R.string.pref_true_black_summary)
                                } else {
                                    stringResource(R.string.pref_true_black_only_in_night_mode)
                                },
                                checked = state.trueBlackEnabled,
                                enabled = state.usingNightMode,
                                onCheckedChange = callbacks.onSetTrueBlack,
                            )
                        }

                        // 2. Display & Typography
                        SettingsGroupSection(title = stringResource(R.string.display_category)) {
                            SettingsClickRow(
                                iconRes = R.drawable.ic_image_area,
                                title = stringResource(R.string.banner_settings_character_title),
                                valueText = characterOptions.labelFor(state.bannerCharacterKey),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.banner_settings_character_title),
                                        options = characterOptions,
                                        selectedValue = state.bannerCharacterKey,
                                        onSelect = callbacks.onSetBannerCharacter,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_image_edit,
                                title = stringResource(R.string.profile_name_custom_title),
                                subtitle = state.customProfileName.ifBlank {
                                    stringResource(R.string.uwu_banner_title)
                                },
                                onClick = {
                                    activeDialog = ActiveSettingDialog.TextInput(
                                        title = context.getString(R.string.profile_name_custom_title),
                                        initialValue = state.customProfileName,
                                        hint = context.getString(R.string.profile_name_custom_dialog),
                                        onConfirm = callbacks.onSetCustomProfileName,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_format_bold,
                                title = stringResource(R.string.pref_font_bold_title),
                                subtitle = stringResource(R.string.pref_font_bold_summary),
                                checked = state.boldFontEnabled,
                                onCheckedChange = callbacks.onSetBoldFont,
                            )
                            SettingsGroupDivider()
                            SettingsSliderRow(
                                iconRes = R.drawable.ic_format_size,
                                title = stringResource(R.string.pref_font_scale_title),
                                subtitle = stringResource(R.string.pref_font_scale_summary),
                                value = state.fontSize.toFloat(),
                                valueRange = 50f..150f,
                                steps = 19,
                                valueBadge = "${state.fontSize}%",
                                onValueChangeFinished = { v ->
                                    val snapped = ((v / 5f).roundToInt() * 5).coerceIn(50, 150)
                                    if (snapped != state.fontSize) {
                                        callbacks.onSetFontScale(snapped)
                                    }
                                },
                                onLongClick = {
                                    activeDialog = ActiveSettingDialog.Confirm(
                                        title = context.getString(R.string.reset_font_scale_title),
                                        message = context.getString(R.string.reset_font_scale_message),
                                        confirmText = context.getString(R.string.yes),
                                        dismissText = context.getString(R.string.no),
                                        onConfirm = { callbacks.onSetFontScale(100) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_cellphone_screenshot,
                                title = stringResource(R.string.pref_custom_dpi),
                                valueText = if (state.dpiValue > 0) {
                                    "${state.dpiValue} dpi"
                                } else {
                                    stringResource(R.string.language_system_default)
                                },
                                onClick = { activeDialog = ActiveSettingDialog.DpiConfig },
                                onLongClick = {
                                    val sysDpi = resources.displayMetrics.densityDpi
                                    activeDialog = ActiveSettingDialog.Confirm(
                                        title = context.getString(R.string.reset_dpi_title),
                                        message = context.getString(R.string.reset_dpi_message, sysDpi),
                                        confirmText = context.getString(R.string.yes),
                                        dismissText = context.getString(R.string.no),
                                        onConfirm = callbacks.onResetDpi,
                                    )
                                },
                            )
                        }

                        // 3. Home & Liquid Glass
                        SettingsGroupSection(title = stringResource(R.string.bottom_bar_category)) {
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.show_bottom_bar),
                                checked = state.showBottomBar,
                                onCheckedChange = callbacks.onSetShowBottomBar,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.blur_bottom_status_title),
                                subtitle = stringResource(R.string.blur_bottom_status_summary),
                                checked = state.blurBottomStatus,
                                onCheckedChange = callbacks.onSetBlurBottomStatus,
                            )
                            AnimatedVisibility(
                                visible = state.blurBottomStatus,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    SettingsGroupDivider()
                                    SettingsSwitchRow(
                                        iconRes = R.drawable.ic_toggle_switch,
                                        title = stringResource(R.string.blur_bottom_blob_anim_title),
                                        subtitle = stringResource(R.string.blur_bottom_blob_anim_summary),
                                        checked = state.blurBottomBlobAnim,
                                        onCheckedChange = callbacks.onSetBlurBottomBlobAnim,
                                    )
                                    SettingsGroupDivider()
                                    SettingsSliderRow(
                                        iconRes = R.drawable.ic_toggle_switch,
                                        title = stringResource(R.string.blur_bottom_radius_title),
                                        value = state.blurBottomRadius.toFloat(),
                                        valueRange = 0f..25f,
                                        steps = 24,
                                        valueBadge = "${state.blurBottomRadius}",
                                        onValueChangeFinished = { v ->
                                            callbacks.onSetBlurBottomRadius(v.roundToInt().coerceIn(0, 25))
                                        },
                                    )
                                    SettingsGroupDivider()
                                    SettingsSliderRow(
                                        iconRes = R.drawable.ic_toggle_switch,
                                        title = stringResource(R.string.blur_bottom_alpha_title),
                                        value = state.blurBottomAlpha.toFloat(),
                                        valueRange = 0f..100f,
                                        steps = 19,
                                        valueBadge = "${state.blurBottomAlpha}%",
                                        onValueChangeFinished = { v ->
                                            callbacks.onSetBlurBottomAlpha(v.roundToInt().coerceIn(0, 100))
                                        },
                                    )
                                }
                            }
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_ip_info,
                                title = stringResource(R.string.ip_info_bottom_bar_title),
                                subtitle = stringResource(R.string.ip_info_bottom_bar_summary),
                                checked = state.connectionTestWithIp,
                                onCheckedChange = callbacks.onSetConnectionTestWithIp,
                            )
                            AnimatedVisibility(
                                visible = state.connectionTestWithIp,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    SettingsGroupDivider()
                                    SettingsSwitchRow(
                                        iconRes = R.drawable.ic_toggle_switch,
                                        title = stringResource(R.string.ip_info_show_ip_in_two_line_title),
                                        subtitle = stringResource(R.string.ip_info_show_ip_in_two_line_summary),
                                        checked = state.showIpInTwoLine,
                                        onCheckedChange = callbacks.onSetShowIpInTwoLine,
                                    )
                                }
                            }
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.show_fab_timer_title),
                                subtitle = stringResource(R.string.show_fab_timer_summary),
                                checked = state.showFabTimer,
                                onCheckedChange = callbacks.onSetShowFabTimer,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_service_busy,
                                title = stringResource(R.string.fab_style),
                                valueText = fabStyleOptions.labelFor(state.fabStyle.toString()),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.fab_style),
                                        options = fabStyleOptions,
                                        selectedValue = state.fabStyle.toString(),
                                        onSelect = { v -> v.toIntOrNull()?.let(callbacks.onSetFabStyle) },
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_baseline_grid_3x3_24,
                                title = stringResource(R.string.double_column_profile_list_title),
                                subtitle = stringResource(R.string.double_column_profile_list_summary),
                                checked = state.doubleColumnProfileList,
                                onCheckedChange = callbacks.onSetDoubleColumnProfileList,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.show_quick_actions_title),
                                subtitle = stringResource(R.string.show_quick_actions_summary),
                                checked = state.showQuickActions,
                                onCheckedChange = callbacks.onSetShowQuickActions,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.show_snowflakes_title),
                                subtitle = stringResource(R.string.show_snowflakes_summary),
                                checked = state.showSnowflakes,
                                onCheckedChange = callbacks.onSetShowSnowflakes,
                            )
                        }

                        // 4. General Behavior, Language & Navigation Sheets
                        SettingsGroupSection(title = stringResource(R.string.opsional_category)) {
                            SettingsClickRow(
                                iconRes = R.drawable.ic_baseline_translate_24,
                                title = stringResource(R.string.language),
                                valueText = languageOptions.labelFor(
                                    state.appLanguageTag.takeIf { tag ->
                                        languageOptions.any { it.value == tag }
                                    } ?: ""
                                ),
                                onClick = {
                                    activeDialog = ActiveSettingDialog.SingleChoice(
                                        title = context.getString(R.string.language),
                                        options = languageOptions,
                                        selectedValue = state.appLanguageTag.takeIf { tag ->
                                            languageOptions.any { it.value == tag }
                                        } ?: "",
                                        onSelect = callbacks.onSetLanguage,
                                    )
                                },
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_audio,
                                title = stringResource(R.string.sound_connect_title),
                                subtitle = stringResource(R.string.sound_connect_summary),
                                checked = state.soundOnConnect,
                                onCheckedChange = callbacks.onSetSoundOnConnect,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.pref_show_splash_title),
                                subtitle = stringResource(R.string.pref_show_splash_summary),
                                checked = state.showSplashScreen,
                                onCheckedChange = callbacks.onSetShowSplashScreen,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_heart,
                                title = stringResource(R.string.title_show_welcome_anim),
                                subtitle = stringResource(R.string.summary_show_welcome_anim),
                                checked = state.showWelcomeAnim,
                                onCheckedChange = callbacks.onSetShowWelcomeAnim,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.pref_disable_bottom_sheet_fragment_title),
                                subtitle = stringResource(R.string.pref_disable_bottom_sheet_summary),
                                checked = state.disableBottomSheet,
                                onCheckedChange = callbacks.onSetDisableBottomSheet,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.pref_disable_bottom_sheet_home_title),
                                subtitle = stringResource(R.string.pref_disable_bottom_sheet_summary),
                                checked = state.disableBottomSheetHome,
                                onCheckedChange = callbacks.onSetDisableBottomSheetHome,
                            )
                            SettingsGroupDivider()
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_toggle_switch,
                                title = stringResource(R.string.disable_particles_title),
                                subtitle = stringResource(R.string.disable_particles_sheet_summary),
                                checked = state.disableParticlesSheet,
                                enabled = !(state.disableBottomSheet && state.disableBottomSheetHome),
                                onCheckedChange = callbacks.onSetDisableParticlesSheet,
                            )
                            SettingsGroupDivider()
                            SettingsClickRow(
                                iconRes = R.drawable.ic_image_area,
                                title = stringResource(R.string.banner_custom_sheet_title),
                                subtitle = stringResource(R.string.banner_custom_sheet_summary),
                                enabled = !(state.disableBottomSheet && state.disableBottomSheetHome),
                                onClick = callbacks.onPickSheetBanner,
                            )
                            if (!state.customSheetBannerUri.isNullOrEmpty()) {
                                SettingsGroupDivider()
                                SettingsClickRow(
                                    iconRes = R.drawable.ic_action_delete,
                                    title = stringResource(R.string.delete_custom_banner_sheet),
                                    subtitle = stringResource(R.string.delete_custom_banner_sheet_summary),
                                    onClick = {
                                        activeDialog = ActiveSettingDialog.Confirm(
                                            title = context.getString(R.string.delete_custom_banner_title),
                                            message = context.getString(R.string.delete_custom_banner_message),
                                            confirmText = context.getString(R.string.yes),
                                            dismissText = context.getString(R.string.no),
                                            onConfirm = callbacks.onDeleteSheetBanner,
                                        )
                                    },
                                )
                            }
                        }

                        // 5. Weather Pill (optional)
                        SettingsGroupSection(title = stringResource(R.string.category_weather)) {
                            SettingsSwitchRow(
                                iconRes = R.drawable.ic_weather_cloudy,
                                title = stringResource(R.string.show_weather_info),
                                subtitle = stringResource(R.string.show_weather_info_summary),
                                checked = state.showWeatherInfo,
                                onCheckedChange = callbacks.onToggleWeatherInfo,
                            )
                            AnimatedVisibility(
                                visible = state.showWeatherInfo,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    SettingsGroupDivider()
                                    SettingsSwitchRow(
                                        iconRes = R.drawable.ic_map_marker_outline,
                                        title = stringResource(R.string.weather_manual_mode),
                                        subtitle = stringResource(R.string.weather_manual_summary),
                                        checked = state.manualWeatherEnabled,
                                        onCheckedChange = callbacks.onSetManualWeatherEnabled,
                                    )
                                    if (state.manualWeatherEnabled) {
                                        SettingsGroupDivider()
                                        SettingsClickRow(
                                            iconRes = R.drawable.ic_city,
                                            title = stringResource(R.string.weather_city),
                                            subtitle = state.manualWeatherCity.ifBlank { null },
                                            onClick = {
                                                activeDialog = ActiveSettingDialog.TextInput(
                                                    title = context.getString(R.string.weather_city),
                                                    initialValue = state.manualWeatherCity,
                                                    hint = context.getString(R.string.weather_city_dialog),
                                                    onConfirm = callbacks.onSetManualWeatherCity,
                                                )
                                            },
                                        )
                                    }
                                    SettingsGroupDivider()
                                    SettingsSwitchRow(
                                        iconRes = R.drawable.ic_toggle_switch,
                                        title = stringResource(R.string.show_weather_condition),
                                        subtitle = stringResource(R.string.show_weather_condition_summary),
                                        checked = state.showWeatherCondition,
                                        onCheckedChange = callbacks.onSetShowWeatherCondition,
                                    )
                                    SettingsGroupDivider()
                                    SettingsSwitchRow(
                                        iconRes = R.drawable.ic_city,
                                        title = stringResource(R.string.hide_weather_city),
                                        subtitle = stringResource(R.string.hide_weather_city_summary),
                                        checked = state.hideWeatherCity,
                                        onCheckedChange = callbacks.onSetHideWeatherCity,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "nav_spacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    // Active M3E Compose Dialogs
    when (val dlg = activeDialog) {
        is ActiveSettingDialog.SingleChoice -> {
            MikuSingleChoiceDialog(
                title = dlg.title,
                options = dlg.options,
                selectedValue = dlg.selectedValue,
                subtitle = dlg.subtitle,
                extraActionLabel = dlg.extraActionLabel,
                onExtraAction = dlg.onExtraAction,
                onSelect = dlg.onSelect,
                onDismiss = { activeDialog = null },
            )
        }
        is ActiveSettingDialog.TextInput -> {
            MikuTextInputDialog(
                title = dlg.title,
                initialValue = dlg.initialValue,
                label = dlg.label,
                hint = dlg.hint,
                keyboardType = dlg.keyboardType,
                onConfirm = dlg.onConfirm,
                onDismiss = { activeDialog = null },
            )
        }
        ActiveSettingDialog.ColorPicker -> {
            MikuColorPickerDialog(
                title = stringResource(R.string.theme),
                selectedThemeId = state.appTheme,
                onSelectTheme = callbacks.onSetAppTheme,
                onDismiss = { activeDialog = null },
            )
        }
        ActiveSettingDialog.MtuPicker -> {
            MikuMtuDialog(
                currentMtu = state.mtu,
                onSelectMtu = callbacks.onSetMtu,
                onDismiss = { activeDialog = null },
            )
        }
        ActiveSettingDialog.UrlTestConfig -> {
            MikuUrlTestDialog(
                initialUrl = state.connectionTestURL,
                initialConcurrent = state.connectionTestConcurrent,
                onConfirm = callbacks.onSetConnectionTestConfig,
                onDismiss = { activeDialog = null },
            )
        }
        ActiveSettingDialog.DpiConfig -> {
            MikuDpiDialog(
                currentDpi = state.dpiValue,
                systemDefaultDpi = resources.displayMetrics.densityDpi,
                onApplyDpi = callbacks.onApplyDpi,
                onResetDpi = callbacks.onResetDpi,
                onDismiss = { activeDialog = null },
            )
        }
        is ActiveSettingDialog.Confirm -> {
            MikuConfirmDialog(
                title = dlg.title,
                message = dlg.message,
                confirmText = dlg.confirmText,
                dismissText = dlg.dismissText,
                onConfirm = dlg.onConfirm,
                onDismiss = { activeDialog = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun MikuSettingsHero(
    state: SettingsUiState,
    selectedTab: Int,
    onOpenMenu: () -> Unit,
) {
    MikuSoftPanelHero(
        title = if (selectedTab == 0) {
            stringResource(R.string.settings)
        } else {
            stringResource(R.string.uwu_miku_ui)
        },
        kicker = if (selectedTab == 0) {
            stringResource(R.string.uwu_banner_settings_title)
        } else {
            stringResource(R.string.uwu_banner_theme_title)
        },
        subtitle = if (selectedTab == 0) {
            stringResource(R.string.uwu_banner_settings_summary)
        } else {
            stringResource(R.string.uwu_miku_ui_summary)
        },
        characterRes = state.bannerCharacterRes,
        onOpenMenu = onOpenMenu,
        pills = { palette ->
            MikuHeroGlassPill(
                text = "v${BuildConfig.VERSION_NAME}",
                heroPalette = palette,
            )
        },
    )
}

@Composable
private fun MikuMaintainerCard() {
    MikuSoftCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MikuTheme.extendedColors.softPanel,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        cornerRadius = 20.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_copyright),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.uwu_maintainer_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.uwu_maintainer_summary),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MikuWardrobeIdentityCard(
    state: SettingsUiState,
    onCheckUpdate: () -> Unit,
) {
    MikuSoftCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        cornerRadius = 24.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.uwu_banner_image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${stringResource(R.string.uwu_mikubox_author_title)}: ${stringResource(R.string.uwu_author_summary)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stringResource(R.string.uwu_build_date_title)}: ${BuildConfig.BUILD_DATE}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onCheckUpdate,
                enabled = !state.isCheckingUpdate,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                if (state.isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.isCheckingUpdate) {
                        stringResource(R.string.check_update_checking)
                    } else {
                        "${stringResource(R.string.update)} · ${stringResource(R.string.update_summary)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        MikuSoftCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            cornerRadius = 22.dp,
            content = content,
        )
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SettingIconBadge(
    @DrawableRes iconRes: Int,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsThemeColorRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconBadge(iconRes = R.drawable.ic_baseline_color_lens_24, enabled = enabled)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.uwu_theme_settings_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsClickRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconBadge(iconRes = iconRes, enabled = enabled)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!valueText.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = valueText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.uwu_arrow),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onRowClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                if (onRowClick != null) {
                    onRowClick()
                } else {
                    onCheckedChange(!checked)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconBadge(iconRes = iconRes, enabled = enabled)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsSliderRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueBadge: String,
    onValueChangeFinished: (Float) -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var sliderPosition by remember(value) { mutableStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIconBadge(iconRes = iconRes, enabled = true)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = "${sliderPosition.roundToInt()}${valueBadge.filter { !it.isDigit() }}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onValueChangeFinished(sliderPosition) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),
        )
    }
}

private fun List<ChoiceOption>.labelFor(value: String): String {
    return firstOrNull { it.value == value }?.label ?: value
}
