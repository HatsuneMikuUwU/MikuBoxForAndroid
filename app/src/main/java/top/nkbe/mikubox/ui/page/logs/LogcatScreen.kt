package top.nkbe.mikubox.ui.page.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sagernet.R
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.MikuTheme
import top.nkbe.mikubox.ui.theme.mikuContrastColor

enum class LogFilter(val label: String) {
    ALL("All"),
    INFO("Info"),
    WARN("Warn"),
    ERROR("Error"),
}

data class LogcatScreenCallbacks(
    val onOpenMenu: () -> Unit,
    val onRefresh: () -> Unit,
    val onSendLog: () -> Unit,
    val onClearLog: () -> Unit,
)

@Composable
fun LogcatScreen(
    lines: List<String>,
    callbacks: LogcatScreenCallbacks,
) {
    val bottomDockDp = LocalMikuBottomDockDp.current
    val listBottomPadding = if (bottomDockDp > 0.dp) bottomDockDp + 24.dp else 160.dp

    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLines = remember(lines, selectedFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        lines.filter { line ->
            val matchLevel = when (selectedFilter) {
                LogFilter.ALL -> true
                LogFilter.INFO -> line.contains("INFO[") || line.contains("[Info]")
                LogFilter.WARN -> line.contains("WARN[") || line.contains("[Warning]")
                LogFilter.ERROR -> line.contains("ERROR[") || line.contains("[Error]")
            }
            val matchQuery = q.isEmpty() || line.lowercase().contains(q)
            matchLevel && matchQuery
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.lastIndex)
        }
    }

    val colors = MaterialTheme.colorScheme
    val ext = MikuTheme.extendedColors
    val primary = colors.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        MikuSoftPanelHero(
            title = stringResource(R.string.menu_log),
            subtitle = stringResource(R.string.logcat_summary),
            onOpenMenu = callbacks.onOpenMenu,
            actions = { palette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_refresh_24,
                    contentDescription = stringResource(R.string.group_update),
                    heroPalette = palette,
                    onClick = callbacks.onRefresh,
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_social_share,
                    contentDescription = stringResource(R.string.logcat),
                    heroPalette = palette,
                    onClick = callbacks.onSendLog,
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_action_delete,
                    contentDescription = stringResource(R.string.clear_logcat),
                    heroPalette = palette,
                    onClick = callbacks.onClearLog,
                )
            },
            pills = { palette ->
                MikuHeroGlassPill(
                    text = "${filteredLines.size} lines",
                    heroPalette = palette,
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_apps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerLow,
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant.copy(alpha = 0.5f),
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    Surface(
                        shape = CircleShape,
                        color = if (selected) primary else colors.surfaceContainerHigh,
                        contentColor = if (selected) onPrimaryContrast else colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { selectedFilter = filter },
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            MikuSoftCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = listBottomPadding),
                containerColor = colors.surfaceContainerLow,
                borderColor = colors.outlineVariant.copy(alpha = 0.45f),
                cornerRadius = 22.dp,
            ) {
                if (filteredLines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.logcat_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            itemsIndexed(
                                items = filteredLines,
                                key = { idx, _ -> idx },
                            ) { _, line ->
                                val lineColor = when {
                                    line.contains("ERROR[") || line.contains("[Error]") -> colors.error
                                    line.contains("WARN[") || line.contains("[Warning]") -> ext.warning
                                    line.contains("INFO[") || line.contains("[Info]") -> ext.success
                                    else -> colors.onSurfaceVariant
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                    ),
                                    color = lineColor,
                                )
                            }
                            item(key = "log_tail_spacer") {
                                Spacer(Modifier.height(8.dp).navigationBarsPadding())
                            }
                        }
                    }
                }
            }
        }
    }
}
