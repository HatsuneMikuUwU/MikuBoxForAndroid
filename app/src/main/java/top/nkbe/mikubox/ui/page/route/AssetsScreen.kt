package top.nkbe.mikubox.ui.page.route

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@Immutable
data class AssetItemUi(
    val fileName: String,
    val localVersion: String,
    val isBuiltIn: Boolean,
    val isUpdating: Boolean,
)

@Immutable
data class AssetsUiState(
    val assets: List<AssetItemUi> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onBack: () -> Unit,
    onImportFile: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateAsset: (String) -> Unit,
    onDeleteAsset: (String) -> Unit,
    onDismissStatus: () -> Unit,
    onDismissError: () -> Unit,
) {
    var confirmDeleteName by remember { mutableStateOf<String?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.menu_route),
            title = stringResource(R.string.route_assets),
            subtitle = stringResource(R.string.route_manage_assets),
            navigationIconRes = R.drawable.baseline_arrow_back_24,
            onOpenMenu = onBack,
            actions = { heroPalette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_refresh_24,
                    contentDescription = stringResource(R.string.update),
                    heroPalette = heroPalette,
                    onClick = onRefresh,
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_file_file_upload,
                    contentDescription = stringResource(R.string.action_import_file),
                    heroPalette = heroPalette,
                    onClick = onImportFile,
                )
            },
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = "${state.assets.size}",
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
                state.statusMessage?.let { msg ->
                    item(key = "status_banner") {
                        MikuSoftCard(
                            onClick = onDismissStatus,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(android.R.string.ok),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                items(state.assets, key = { it.fileName }) { asset ->
                    MikuSoftCard(shape = RoundedCornerShape(22.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_rule_folder_24)

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = asset.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.route_asset_status, asset.localVersion),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                if (asset.isBuiltIn) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(enabled = !asset.isUpdating) {
                                                onUpdateAsset(asset.fileName)
                                            },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = onPrimaryContrast,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.update),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                                            .clickable { confirmDeleteName = asset.fileName },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_action_delete),
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            if (asset.isUpdating) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDeleteName?.let { fileName ->
        AlertDialog(
            onDismissRequest = { confirmDeleteName = null },
            title = { Text(fileName, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_route_prompt)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteName = null
                        onDeleteAsset(fileName)
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
                TextButton(onClick = { confirmDeleteName = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.error_title), fontWeight = FontWeight.Bold) },
            text = { Text(error) },
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
