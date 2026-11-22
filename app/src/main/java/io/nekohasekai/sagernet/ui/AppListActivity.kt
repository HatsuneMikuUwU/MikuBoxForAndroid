package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.apps.AppEntryUi
import top.nkbe.mikubox.ui.page.apps.AppSelectorCallbacks
import top.nkbe.mikubox.ui.page.apps.AppSelectorScreen
import top.nkbe.mikubox.ui.page.apps.AppSelectorUiState

class AppListActivity : ThemedActivity() {

    private var loader: Job? = null
    private val selectedUids = mutableSetOf<Int>()
    private var rawApps: List<AppEntryUi> = emptyList()

    private var uiState by mutableStateOf(
        AppSelectorUiState(
            title = "",
            subtitle = "",
            isLoading = true,
            showSystemApps = false,
            showModeControls = false,
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiState = uiState.copy(
            title = getString(R.string.select_apps),
            subtitle = getString(R.string.menu_route),
        )

        setMikuContent {
            AppSelectorScreen(
                state = uiState,
                callbacks = AppSelectorCallbacks(
                    onBack = { finish() },
                    onToggleApp = { entry -> toggleApp(entry) },
                    onToggleSystemApps = { show ->
                        uiState = uiState.copy(showSystemApps = show)
                    },
                    onSelectProxyMode = {},
                    onAutoSelectProxyApps = {},
                    onInvertSelections = { invertSelections() },
                    onClearSelections = { clearSelections() },
                    onExportClipboard = {
                        val success = SagerNet.trySetPrimaryClip("false\n${DataStore.routePackages}")
                        uiState = uiState.copy(
                            statusMessage = getString(
                                if (success) R.string.action_export_msg else R.string.action_export_err
                            ),
                        )
                    },
                    onImportClipboard = { importFromClipboard() },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                        )
                    },
                    onDismissStatus = {
                        uiState = uiState.copy(statusMessage = null)
                    },
                ),
            )
        }

        loadApps()
    }

    override fun onDestroy() {
        loader?.cancel()
        super.onDestroy()
    }

    private fun cachedApps() = PackageCache.installedPackages.toMutableMap().apply {
        remove(BuildConfig.APPLICATION_ID)
    }

    private fun initSelectedUids(str: String = DataStore.routePackages) {
        selectedUids.clear()
        val apps = cachedApps()
        for (line in str.lineSequence()) {
            val uid = apps[line]?.applicationInfo?.uid ?: continue
            selectedUids.add(uid)
        }
    }

    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launch {
            uiState = uiState.copy(isLoading = true)
            val loaded = withContext(Dispatchers.IO) {
                PackageCache.reload()
                initSelectedUids()
                val pm = packageManager
                cachedApps().mapNotNull { (pkg, info) ->
                    coroutineContext[Job]?.ensureActive()
                    val appInfo = info.applicationInfo ?: return@mapNotNull null
                    val uid = appInfo.uid
                    AppEntryUi(
                        packageName = pkg,
                        label = appInfo.loadLabel(pm).toString(),
                        uid = uid,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        selected = uid in selectedUids,
                    )
                }.sortedWith(compareBy({ !it.selected }, { it.label }))
            }
            rawApps = loaded
            uiState = uiState.copy(isLoading = false, apps = loaded)
        }
    }

    private fun toggleApp(entry: AppEntryUi) {
        if (entry.uid in selectedUids) {
            selectedUids.remove(entry.uid)
        } else {
            selectedUids.add(entry.uid)
        }
        val updated = rawApps.map { it.copy(selected = it.uid in selectedUids) }
        rawApps = updated
        DataStore.routePackages = updated.filter { it.selected }.joinToString("\n") { it.packageName }
        uiState = uiState.copy(apps = updated)
    }

    private fun invertSelections() {
        val allUids = rawApps.map { it.uid }.toSet()
        val next = (allUids - selectedUids).toMutableSet()
        selectedUids.clear()
        selectedUids.addAll(next)
        val updated = rawApps
            .map { it.copy(selected = it.uid in selectedUids) }
            .sortedWith(compareBy({ !it.selected }, { it.label }))
        rawApps = updated
        DataStore.routePackages = updated.filter { it.selected }.joinToString("\n") { it.packageName }
        uiState = uiState.copy(apps = updated)
    }

    private fun clearSelections() {
        selectedUids.clear()
        DataStore.routePackages = ""
        val updated = rawApps
            .map { it.copy(selected = false) }
            .sortedBy { it.label }
        rawApps = updated
        uiState = uiState.copy(apps = updated)
    }

    private fun importFromClipboard() {
        val text = SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrEmpty()) {
            val idx = text.indexOf('\n')
            val imported = if (idx < 0) "" else text.substring(idx + 1)
            DataStore.routePackages = imported
            initSelectedUids(imported)
            val updated = rawApps
                .map { it.copy(selected = it.uid in selectedUids) }
                .sortedWith(compareBy({ !it.selected }, { it.label }))
            rawApps = updated
            uiState = uiState.copy(
                apps = updated,
                statusMessage = getString(R.string.action_import_msg),
            )
            return
        }
        uiState = uiState.copy(statusMessage = getString(R.string.action_import_err))
    }
}
