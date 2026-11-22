package top.nkbe.mikubox.ui.page.tools

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import moe.matsuri.nb4a.utils.WebViewUtil
import top.nkbe.mikubox.ui.component.LocalMikuBottomDockDp
import top.nkbe.mikubox.ui.component.MikuHeroGlassIconButton
import top.nkbe.mikubox.ui.component.MikuHeroGlassPill
import top.nkbe.mikubox.ui.component.MikuSettingIconBadge
import top.nkbe.mikubox.ui.component.MikuSoftCard
import top.nkbe.mikubox.ui.component.MikuSoftPanelHero
import top.nkbe.mikubox.ui.theme.mikuContrastColor

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebviewScreen(
    initialUrl: String,
    onOpenMenu: () -> Unit,
    onUpdateUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var isClosed by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    val webView = remember {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WebView(context).apply {
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    WebViewUtil.onReceivedError(view, request, error)
                }
            }
            loadUrl(currentUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.onPause()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    val bottomDockDp = LocalMikuBottomDockDp.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContrast = remember(primary) { mikuContrastColor(primary) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MikuSoftPanelHero(
            kicker = stringResource(R.string.app_name),
            title = stringResource(R.string.menu_dashboard),
            subtitle = currentUrl,
            onOpenMenu = onOpenMenu,
            actions = { heroPalette ->
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_edit_color,
                    contentDescription = stringResource(R.string.set_panel_url),
                    heroPalette = heroPalette,
                    onClick = { showUrlDialog = true },
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_baseline_refresh_24,
                    contentDescription = stringResource(R.string.update),
                    heroPalette = heroPalette,
                    onClick = {
                        isClosed = false
                        webView.reload()
                    },
                )
                MikuHeroGlassIconButton(
                    iconRes = R.drawable.ic_navigation_close,
                    contentDescription = stringResource(R.string.mal_close),
                    heroPalette = heroPalette,
                    onClick = {
                        if (!isClosed) {
                            isClosed = true
                            webView.onPause()
                            webView.loadUrl("about:blank")
                        }
                    },
                )
            },
            pills = { heroPalette ->
                MikuHeroGlassPill(
                    text = if (isClosed) stringResource(R.string.mal_close) else "WebUI",
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = bottomDockDp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            ) {
                if (isClosed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MikuSoftCard(shape = RoundedCornerShape(24.dp)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MikuSettingIconBadge(iconRes = R.drawable.ic_baseline_refresh_24)
                                Text(
                                    text = stringResource(R.string.menu_dashboard),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = {
                                        isClosed = false
                                        webView.onResume()
                                        webView.loadUrl(currentUrl)
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primary,
                                        contentColor = onPrimaryContrast,
                                    ),
                                ) {
                                    Text(stringResource(R.string.start), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        var draft by remember(showUrlDialog) { mutableStateOf(currentUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.set_panel_url),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            currentUrl = trimmed
                            isClosed = false
                            onUpdateUrl(trimmed)
                            webView.onResume()
                            webView.loadUrl(trimmed)
                        }
                        showUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = onPrimaryContrast,
                    ),
                ) {
                    Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
