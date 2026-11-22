package top.nkbe.mikubox.ui.page.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class StunUiState(
    val server: String = "stun.voipgate.com:3478",
    val isTesting: Boolean = false,
    val result: String = "",
    val errorMessage: String? = null,
)

@Composable
fun StunScreen(
    state: StunUiState,
    onServerChange: (String) -> Unit,
    onStartTest: () -> Unit,
    onDismissError: () -> Unit,
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
            title = stringResource(R.string.stun_test),
            subtitle = stringResource(R.string.stun_test_summary),
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = onBack,
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = "RFC 5780",
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
                                MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_nat_24)
                                Text(
                                    text = stringResource(R.string.nat_stun_server_hint),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            OutlinedTextField(
                                value = state.server,
                                onValueChange = onServerChange,
                                singleLine = true,
                                enabled = !state.isTesting,
                                label = { Text(stringResource(R.string.nat_stun_server_hint)) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Button(
                                onClick = onStartTest,
                                enabled = !state.isTesting && state.server.isNotBlank(),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primary,
                                    contentColor = onPrimaryContrast,
                                ),
                            ) {
                                if (state.isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = onPrimaryContrast,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.stun_attest_loading),
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.start),
                                        fontWeight = FontWeight.Bold,
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
                                text = stringResource(R.string.nat_result_hint),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (state.result.isBlank()) {
                                Text(
                                    text = if (state.isTesting) {
                                        stringResource(R.string.stun_attest_loading)
                                    } else {
                                        "—"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = state.result,
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

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = {
                Text(
                    text = stringResource(R.string.error_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismissError,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = onPrimaryContrast,
                    ),
                ) {
                    Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
