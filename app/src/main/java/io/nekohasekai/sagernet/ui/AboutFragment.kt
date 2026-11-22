package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.utils.PackageCache
import libcore.Libcore
import moe.matsuri.nb4a.plugin.Plugins
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.about.AboutScreen
import top.nkbe.mikubox.ui.page.about.AboutScreenCallbacks
import top.nkbe.mikubox.ui.page.about.AboutUiState
import top.nkbe.mikubox.ui.page.about.InstalledPluginInfo

class AboutFragment : ToolbarFragment() {

    private var uiState by mutableStateOf(
        AboutUiState(
            appVersionDisplay = SagerNet.appVersionNameForDisplay,
            singBoxVersion = "",
            plugins = emptyList(),
            showIgnoreBattery = false,
            licenseText = "",
        )
    )

    private val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { (resultCode, _) ->
        if (resultCode == Activity.RESULT_OK) {
            refreshState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        refreshState()
        return mikuComposeView {
            AboutScreen(
                state = uiState,
                callbacks = AboutScreenCallbacks(
                    onOpenMenu = {
                        (activity as? NavigationHost)?.showNavigationSheet()
                            ?: activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onOpenUrl = { url ->
                        requireContext().launchCustomTab(url)
                    },
                    onOpenPluginDetails = { pkgName ->
                        startActivity(
                            Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", pkgName, null)
                            }
                        )
                    },
                    onRequestIgnoreBattery = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestIgnoreBatteryOptimizations.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    "package:${app.packageName}".toUri(),
                                )
                            )
                        }
                    },
                ),
            )
        }
    }

    private fun refreshState() {
        val ctx = context ?: return
        runOnDefaultDispatcher {
            val boxVersion = runCatching { Libcore.versionBox() }.getOrDefault("")
            val plugins = mutableListOf<InstalledPluginInfo>()
            runCatching {
                PackageCache.awaitLoadSync()
                for ((_, pkg) in PackageCache.installedPluginPackages) {
                    val pluginId = pkg.providers?.getOrNull(0)?.loadString(Plugins.METADATA_KEY_ID)
                    if (pluginId.isNullOrBlank()) continue
                    plugins += InstalledPluginInfo(
                        title = getString(R.string.version_x, pluginId) +
                            " (${Plugins.displayExeProvider(pkg.packageName)})",
                        version = "v${pkg.versionName.orEmpty()}",
                        packageName = pkg.packageName,
                    )
                }
            }.onFailure { Logs.w(it) }

            val needBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm != null && !pm.isIgnoringBatteryOptimizations(app.packageName)
            } else {
                false
            }

            val license = runCatching {
                ctx.assets.open("LICENSE").bufferedReader().use { it.readText() }
            }.getOrDefault("")

            onMainDispatcher {
                uiState = AboutUiState(
                    appVersionDisplay = SagerNet.appVersionNameForDisplay,
                    singBoxVersion = boxVersion,
                    plugins = plugins,
                    showIgnoreBattery = needBattery,
                    licenseText = license,
                )
            }
        }
    }
}
