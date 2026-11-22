package top.nkbe.mikubox.ui.page.about

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.MikuTheme
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class InstalledPluginInfo(
    val title: String,
    val version: String,
    val packageName: String,
)

@Immutable
data class AboutUiState(
    val appVersionDisplay: String,
    val singBoxVersion: String,
    val plugins: List<InstalledPluginInfo>,
    val showIgnoreBattery: Boolean,
    val licenseText: String,
)

data class AboutScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onOpenPluginDetails: (String) -> Unit,
    val onRequestIgnoreBattery: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    state: AboutUiState,
    callbacks: AboutScreenCallbacks,
) {
    val bottomDockDp = LocalMikuBottomDockDp.current
    val listBottomPadding = if (bottomDockDp > 0.dp) bottomDockDp + 24.dp else 160.dp

    var mikuCardExpanded by rememberSaveable { mutableStateOf(false) }
    var licenseExpanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
        ) {
            item(key = "about_hero") {
                MikuSoftPanelHero(
                    title = stringResource(R.string.menu_about),
                    kicker = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.uwu_author_summary),
                    onOpenMenu = callbacks.onOpenMenu,
                    pills = { palette ->
                        MikuHeroGlassPill(
                            text = "v${BuildConfig.VERSION_NAME}",
                            heroPalette = palette,
                        )
                        if (state.singBoxVersion.isNotBlank()) {
                            MikuHeroGlassPill(
                                text = "sing-box ${state.singBoxVersion}",
                                heroPalette = palette,
                            )
                        }
                    },
                )
            }

            item(key = "about_body") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Miku Visual Showcase Banner
                    MikuShowcaseBannerCard(
                        appVersionDisplay = state.appVersionDisplay,
                        singBoxVersion = state.singBoxVersion,
                        onOpenUrl = callbacks.onOpenUrl,
                    )

                    // 2. Dual Author Spotlight Cards (two independent links)
                    AboutAuthorSpotlightSection(onOpenUrl = callbacks.onOpenUrl)

                    // 3. Core & Runtime Card
                    AboutCardSection(title = stringResource(R.string.uwu_about_version_title)) {
                        AboutActionRow(
                            iconRes = R.drawable.ic_baseline_update_24,
                            title = stringResource(R.string.app_version),
                            subtitle = state.appVersionDisplay,
                            onClick = {
                                callbacks.onOpenUrl("https://github.com/HatsuneMikuUwU/MikuBoxForAndroid/releases")
                            },
                        )
                        AboutDivider()
                        AboutActionRow(
                            iconRes = R.drawable.ic_package_variant_closed,
                            title = stringResource(R.string.version_x, "sing-box"),
                            subtitle = state.singBoxVersion,
                        )
                        AboutDivider()
                        AboutActionRow(
                            iconRes = R.drawable.ic_baseline_card_giftcard_24,
                            title = stringResource(R.string.donate_to_original_author),
                            subtitle = stringResource(R.string.donate_info),
                            onClick = {
                                callbacks.onOpenUrl("https://matsuridayo.github.io/index_docs/#donate")
                            },
                        )

                        state.plugins.forEach { plugin ->
                            AboutDivider()
                            AboutActionRow(
                                iconRes = R.drawable.ic_baseline_nfc_24,
                                title = plugin.title,
                                subtitle = plugin.version,
                                onClick = { callbacks.onOpenPluginDetails(plugin.packageName) },
                            )
                        }

                        if (state.showIgnoreBattery) {
                            AboutDivider()
                            AboutActionRow(
                                iconRes = R.drawable.ic_baseline_running_with_errors_24,
                                title = stringResource(R.string.ignore_battery_optimizations),
                                subtitle = stringResource(R.string.ignore_battery_optimizations_sum),
                                onClick = callbacks.onRequestIgnoreBattery,
                            )
                        }
                    }

                    // 4. Project & Community Links Card
                    AboutCardSection(title = stringResource(R.string.project)) {
                        AboutActionRow(
                            iconRes = R.drawable.ic_github,
                            title = stringResource(R.string.uwu_mikubox),
                            subtitle = "github.com/HatsuneMikuUwU/MikuBoxForAndroid",
                            onClick = {
                                callbacks.onOpenUrl("https://github.com/HatsuneMikuUwU/MikuBoxForAndroid")
                            },
                        )
                        AboutDivider()
                        AboutActionRow(
                            iconRes = R.drawable.ic_qu_shadowsocks_foreground,
                            title = stringResource(R.string.uwu_hatsune),
                            subtitle = "${stringResource(R.string.telegram)} · @uwuowoumuChannel",
                            onClick = { callbacks.onOpenUrl("https://t.me/uwuowoumuChannel") },
                        )
                        AboutDivider()
                        AboutActionRow(
                            iconRes = R.drawable.ic_github,
                            title = stringResource(R.string.uwu_hsskyboy),
                            subtitle = "nkbe.top · github.com/HSSkyBoy",
                            onClick = { callbacks.onOpenUrl("https://github.com/HSSkyBoy") },
                        )
                    }

                    // 5. Credits Card
                    AboutCardSection(title = stringResource(R.string.uwu_big_thanks)) {
                        AboutActionRow(
                            iconRes = R.drawable.ic_github,
                            title = stringResource(R.string.uwu_nekobox),
                            subtitle = stringResource(R.string.github),
                            onClick = {
                                callbacks.onOpenUrl("https://github.com/MatsuriDayo/NekoBoxForAndroid")
                            },
                        )
                        AboutDivider()
                        AboutActionRow(
                            iconRes = R.drawable.ic_qu_shadowsocks_foreground,
                            title = stringResource(R.string.uwu_hsskyboy),
                            subtitle = "${stringResource(R.string.telegram)} · @np_nbcn",
                            onClick = { callbacks.onOpenUrl("https://t.me/np_nbcn") },
                        )
                    }

                    // 6. Hatsune Miku Profile Card (Expandable with Miku Artwork & Colorful Tags)
                    MikuSoftCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MikuTheme.extendedColors.softPanel,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        cornerRadius = 24.dp,
                        onClick = { mikuCardExpanded = !mikuCardExpanded },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        ),
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.uwu_banner_profile),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.uwu_about_miku_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.expandable_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    painter = painterResource(
                                        if (mikuCardExpanded) R.drawable.uwu_arrow_down else R.drawable.uwu_arrow
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            AnimatedVisibility(
                                visible = mikuCardExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.uwu_banner_image),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                    )
                                    Text(
                                        text = stringResource(R.string.uwu_about_miku_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_gender_title).trim(),
                                            stringResource(R.string.uwu_miku_gender_summary),
                                            colorIndex = 0,
                                        )
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_birthday_title).trim(),
                                            stringResource(R.string.uwu_miku_birthday_summary),
                                            colorIndex = 1,
                                        )
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_age_title).trim(),
                                            stringResource(R.string.uwu_miku_age_summary),
                                            colorIndex = 2,
                                        )
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_voice_title).trim(),
                                            stringResource(R.string.uwu_miku_voice_summary),
                                            colorIndex = 0,
                                        )
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_weight_title).trim(),
                                            stringResource(R.string.uwu_miku_weight_summary),
                                            colorIndex = 1,
                                        )
                                        MikuFactChip(
                                            stringResource(R.string.uwu_miku_height_title).trim(),
                                            stringResource(R.string.uwu_miku_height_summary),
                                            colorIndex = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. License Card (Expandable)
                    if (state.licenseText.isNotBlank()) {
                        MikuSoftCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            cornerRadius = 22.dp,
                            onClick = { licenseExpanded = !licenseExpanded },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MikuSettingIconBadge(iconRes = R.drawable.ic_action_copyright)
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.license),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.oss_licenses),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(
                                            if (licenseExpanded) R.drawable.uwu_arrow_down else R.drawable.uwu_arrow
                                        ),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                AnimatedVisibility(
                                    visible = licenseExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = state.licenseText,
                                            modifier = Modifier.padding(top = 12.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "about_nav_spacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MikuShowcaseBannerCard(
    appVersionDisplay: String,
    singBoxVersion: String,
    onOpenUrl: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.5.dp, primary.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.uwu_banner_image_about),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color(0xCC091614),
                                Color(0xF0081412),
                            )
                        )
                    ),
            )

            Text(
                text = "♪",
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = primary.copy(alpha = 0.28f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, primary, CircleShape),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.uwu_banner_profile),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(
                            text = "sing-box $singBoxVersion · $appVersionDisplay",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShowcaseActionPill(
                        iconRes = R.drawable.ic_github,
                        label = stringResource(R.string.github),
                        onClick = { onOpenUrl("https://github.com/HatsuneMikuUwU/MikuBoxForAndroid") },
                    )
                    ShowcaseActionPill(
                        iconRes = R.drawable.ic_baseline_update_24,
                        label = stringResource(R.string.app_version),
                        onClick = { onOpenUrl("https://github.com/HatsuneMikuUwU/MikuBoxForAndroid/releases") },
                    )
                    ShowcaseActionPill(
                        iconRes = R.drawable.ic_qu_shadowsocks_foreground,
                        label = stringResource(R.string.telegram),
                        onClick = { onOpenUrl("https://t.me/uwuowoumuChannel") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowcaseActionPill(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun AboutAuthorSpotlightSection(
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.uwu_mikubox_author_title),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthorSpotlightCard(
                name = "HatsuneMikuUwU",
                subtitle = "github.com/HatsuneMikuUwU",
                avatarRes = R.drawable.uwu_banner_profile,
                fallbackIconRes = null,
                containerColor = MikuTheme.extendedColors.softPanel,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                onClick = { onOpenUrl("https://github.com/HatsuneMikuUwU") },
                modifier = Modifier.weight(1f),
            )

            AuthorSpotlightCard(
                name = "NkBe",
                subtitle = "nkbe.top",
                avatarRes = null,
                fallbackIconRes = R.drawable.ic_baseline_person_24,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                onClick = { onOpenUrl("https://www.nkbe.top") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AuthorSpotlightCard(
    name: String,
    subtitle: String,
    @DrawableRes avatarRes: Int?,
    @DrawableRes fallbackIconRes: Int?,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MikuSoftCard(
        modifier = modifier,
        containerColor = containerColor,
        borderColor = borderColor,
        cornerRadius = 22.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (avatarRes != null) {
                    Image(
                        painter = painterResource(avatarRes),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                } else if (fallbackIconRes != null) {
                    MikuSettingIconBadge(iconRes = fallbackIconRes)
                }

                Icon(
                    painter = painterResource(R.drawable.uwu_arrow),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AboutCardSection(
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
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun AboutActionRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MikuSettingIconBadge(iconRes = iconRes)
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
        if (onClick != null) {
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
private fun MikuFactChip(
    label: String,
    value: String,
    colorIndex: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val container = remember(colorIndex, scheme) {
        when (colorIndex % 3) {
            0 -> scheme.primaryContainer
            1 -> scheme.secondaryContainer
            else -> scheme.tertiaryContainer
        }
    }
    val content = remember(container) { mikuContrastColor(container) }

    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
