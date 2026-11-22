package top.nkbe.mikubox.ui.page.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class HotspotProxyUiState(
    val localIp: String = "127.0.0.1",
    val isRunning: Boolean = false,
    val port: Int = 0,
    val isBound: Boolean = false,
)

@Composable
fun HotspotProxyScreen(
    state: HotspotProxyUiState,
    onToggleProxy: () -> Unit,
    onCopyIp: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }
    val errorBg = MaterialTheme.colorScheme.error
    val onErrorContrast = remember(errorBg) { mikuContrastColor(errorBg) }

    val statusText = if (state.isRunning) {
        stringResource(R.string.status_running_port, state.port)
    } else {
        stringResource(R.string.status_stopped)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.tools_network),
            title = stringResource(R.string.hotspot_proxy_title),
            subtitle = stringResource(R.string.hotspot_proxy_summay),
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = onBack,
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = statusText,
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
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MikuSettingIconBadge(iconRes = R.drawable.ic_wifi)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.label_service_status),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(if (state.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline),
                                        )
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.label_local_ip),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = state.localIp,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                OutlinedButton(
                                    onClick = onCopyIp,
                                    shape = CircleShape,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_content_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.btn_copy_ip),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            Button(
                                onClick = onToggleProxy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isRunning) errorBg else primary,
                                    contentColor = if (state.isRunning) onErrorContrast else onPrimaryContrast,
                                ),
                            ) {
                                Text(
                                    text = stringResource(if (state.isRunning) R.string.btn_stop else R.string.btn_start),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                item {
                    MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.label_guide),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val steps = listOf(
                                R.string.guide_step_1,
                                R.string.guide_step_2,
                                R.string.guide_step_3,
                                R.string.guide_step_4,
                                R.string.guide_step_5,
                                R.string.guide_step_6,
                            )
                            steps.forEach { stepRes ->
                                Text(
                                    text = stringResource(stepRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                            Text(
                                text = stringResource(R.string.guide_conclusion),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
