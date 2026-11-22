package top.nkbe.mikubox.ui.page.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSegmentedTabs
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class HostCheckerUiState(
    val host: String = "",
    val proxy: String = "",
    val isDirect: Boolean = true,
    val selectedMethod: String = "GET",
    val methods: List<String> = emptyList(),
    val isChecking: Boolean = false,
    val logs: List<String> = emptyList(),
)

@Composable
fun HostCheckerScreen(
    state: HostCheckerUiState,
    onHostChange: (String) -> Unit,
    onProxyChange: (String) -> Unit,
    onSelectDirect: (Boolean) -> Unit,
    onSelectMethod: (String) -> Unit,
    onCheck: () -> Unit,
    onClearLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.tools_network),
            title = stringResource(R.string.hostchecker_title),
            subtitle = stringResource(R.string.hostchecker_summay),
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = onBack,
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = state.selectedMethod,
                    heroPalette = heroPalette,
                )
                MikuHeroGlassPill(
                    text = stringResource(
                        if (state.isDirect) R.string.hostchecker_mode_direct else R.string.hostchecker_mode_proxy
                    ),
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
                                MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_security_24)
                                Text(
                                    text = stringResource(R.string.hostchecker_mode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            MikuSegmentedTabs(
                                tabs = listOf(
                                    stringResource(R.string.hostchecker_mode_direct),
                                    stringResource(R.string.hostchecker_mode_proxy),
                                ),
                                selectedIndex = if (state.isDirect) 0 else 1,
                                onSelectTab = { idx -> onSelectDirect(idx == 0) },
                            )

                            OutlinedTextField(
                                value = state.host,
                                onValueChange = onHostChange,
                                singleLine = true,
                                enabled = !state.isChecking,
                                label = { Text(stringResource(R.string.hostchecker_hint_url)) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (!state.isDirect) {
                                OutlinedTextField(
                                    value = state.proxy,
                                    onValueChange = onProxyChange,
                                    singleLine = true,
                                    enabled = !state.isChecking,
                                    label = { Text(stringResource(R.string.hostchecker_hint_ip)) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Text(
                                text = stringResource(R.string.hostchecker_method),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.methods.forEach { method ->
                                    FilterChip(
                                        selected = method == state.selectedMethod,
                                        onClick = { onSelectMethod(method) },
                                        label = { Text(method, fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primary,
                                            selectedLabelColor = onPrimaryContrast,
                                        ),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = onCheck,
                                    enabled = !state.isChecking,
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primary,
                                        contentColor = onPrimaryContrast,
                                    ),
                                ) {
                                    if (state.isChecking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = onPrimaryContrast,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = stringResource(R.string.hostchecker_check),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                OutlinedButton(
                                    onClick = onClearLogs,
                                    enabled = state.logs.isNotEmpty() && !state.isChecking,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = stringResource(R.string.clear_profiles),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
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
                                text = stringResource(R.string.hostchecker_output),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (state.logs.isEmpty()) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = state.logs.joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
