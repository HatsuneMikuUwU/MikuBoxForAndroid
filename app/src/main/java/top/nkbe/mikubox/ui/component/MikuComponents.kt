package top.nkbe.mikubox.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.widget.BannerSettingsCharacterImageView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import top.nkbe.mikubox.ui.theme.MikuHeroPalette
import top.nkbe.mikubox.ui.theme.mikuContrastColor
import top.nkbe.mikubox.ui.theme.rememberMikuHeroPalette

/**
 * Clean Miku × Material 3 Expressive surface card (22dp radius, subtle outline, no heavy shadow).
 */
@Composable
fun MikuSoftCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}

/**
 * Pill-shaped badge with optional leading status dot.
 */
@Composable
fun MikuStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    dotColor: Color? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Clean section header with optional trailing action slot.
 */
@Composable
fun MikuSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Rounded expressive usage progress bar with spring animation.
 */
@Composable
fun MikuUsageProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "MikuUsageProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(height)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
        }
    }
}

@Composable
fun MikuHeroGlassIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    heroPalette: MikuHeroPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = heroPalette.glassContainer,
        border = BorderStroke(1.dp, heroPalette.glassBorder),
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = heroPalette.onHero,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun MikuHeroGlassPill(
    text: String,
    heroPalette: MikuHeroPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = heroPalette.glassContainer,
            border = BorderStroke(1.dp, heroPalette.glassBorder),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = heroPalette.onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = heroPalette.glassContainer,
            border = BorderStroke(1.dp, heroPalette.glassBorder),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = heroPalette.onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun MikuSoftPanelHero(
    title: String,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    @DrawableRes characterRes: Int = remember {
        BannerSettingsCharacterImageView.resolveDrawable(DataStore.bannerSettingsCharacter)
    },
    actions: @Composable RowScope.(MikuHeroPalette) -> Unit = {},
    pills: @Composable RowScope.(MikuHeroPalette) -> Unit = {},
) {
    val palette = rememberMikuHeroPalette()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.gradientStart, palette.gradientMid, palette.gradientEnd),
                )
            )
            .statusBarsPadding()
            .padding(bottom = 40.dp),
    ) {
        Text(
            text = "♪",
            fontSize = 108.sp,
            fontWeight = FontWeight.Black,
            color = palette.watermarkTint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 94.dp, bottom = 6.dp),
        )

        Image(
            painter = painterResource(id = characterRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 8.dp)
                .size(width = 118.dp, height = 132.dp)
                .alpha(0.93f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_navigation_menu,
                    contentDescription = stringResource(R.string.menu_configuration),
                    heroPalette = palette,
                    onClick = onOpenMenu,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions(palette)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!kicker.isNullOrBlank()) {
                Text(
                    text = kicker,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onHeroMuted,
                    modifier = Modifier.padding(end = 112.dp),
                )
                Spacer(Modifier.height(2.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 112.dp),
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onHeroMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 112.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.padding(end = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pills(palette)
            }
        }
    }
}

@Composable
fun MikuSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val activeFg = remember(primary) { mikuContrastColor(primary) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                val bg by animateColorAsState(
                    targetValue = if (selected) primary else Color.Transparent,
                    label = "MikuTabBg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) activeFg else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "MikuTabFg",
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onSelectTab(index) },
                    shape = CircleShape,
                    color = bg,
                    contentColor = fg,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MikuSettingIconBadge(
    @DrawableRes iconRes: Int,
    enabled: Boolean = true,
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
