package top.nkbe.mikubox.ui.page.tools

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor
import java.util.Locale

@Immutable
data class SpeedTestUiState(
    val liveMbps: Float = 0f,
    val isRunning: Boolean = false,
    val downloadText: String = "",
    val uploadText: String = "",
    val pingText: String = "",
    val jitterText: String = "",
)

@Composable
fun SpeedTestScreen(
    state: SpeedTestUiState,
    onStartFull: () -> Unit,
    onStop: () -> Unit,
    onTestDownload: () -> Unit,
    onTestUpload: () -> Unit,
    onTestPing: () -> Unit,
    onTestJitter: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }
    val errorBg = MaterialTheme.colorScheme.error
    val onErrorContrast = remember(errorBg) { mikuContrastColor(errorBg) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.tools_network),
            title = stringResource(R.string.speedtest),
            subtitle = stringResource(R.string.summary_speed_test),
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = onBack,
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = String.format(Locale.US, "%.1f Mbps", state.liveMbps),
                    heroPalette = heroPalette,
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SpeedGauge(speedMbps = state.liveMbps)

                            Button(
                                onClick = if (state.isRunning) onStop else onStartFull,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isRunning) errorBg else primary,
                                    contentColor = if (state.isRunning) onErrorContrast else onPrimaryContrast,
                                ),
                            ) {
                                Text(
                                    text = stringResource(if (state.isRunning) R.string.stop else R.string.start),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SpeedMetricCard(
                            title = "Ping",
                            value = state.pingText,
                            iconRes = R.drawable.ic_baseline_speed_24,
                            enabled = !state.isRunning,
                            onClick = onTestPing,
                            modifier = Modifier.weight(1f),
                        )
                        SpeedMetricCard(
                            title = "Jitter",
                            value = state.jitterText,
                            iconRes = R.drawable.ic_baseline_shutter_speed_24,
                            enabled = !state.isRunning,
                            onClick = onTestJitter,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SpeedMetricCard(
                            title = stringResource(R.string.st_download),
                            value = state.downloadText,
                            iconRes = R.drawable.ic_cloud_download,
                            enabled = !state.isRunning,
                            onClick = onTestDownload,
                            modifier = Modifier.weight(1f),
                        )
                        SpeedMetricCard(
                            title = stringResource(R.string.st_upload),
                            value = state.uploadText,
                            iconRes = R.drawable.ic_file_file_upload,
                            enabled = !state.isRunning,
                            onClick = onTestUpload,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedGauge(speedMbps: Float) {
    val maxScale = remember(speedMbps) {
        when {
            speedMbps > 500f -> 1000f
            speedMbps > 200f -> 500f
            speedMbps > 100f -> 200f
            else -> 100f
        }
    }
    val targetFraction = (speedMbps / maxScale).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SpeedGaugeSweep",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val strokeWidth = 18.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            drawArc(
                color = activeColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.2f", speedMbps),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Mbps · 0–${maxScale.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedMetricCard(
    title: String,
    value: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MikuSoftCard(
        onClick = if (enabled) onClick else null,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MikuSettingIconBadge(iconRes = iconRes, enabled = enabled)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
