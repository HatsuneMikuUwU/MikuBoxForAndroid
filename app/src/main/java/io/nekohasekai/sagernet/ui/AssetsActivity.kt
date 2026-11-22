package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.startFilesForResult
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.route.AssetItemUi
import top.nkbe.mikubox.ui.page.route.AssetsScreen
import top.nkbe.mikubox.ui.page.route.AssetsUiState
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssetsActivity : ThemedActivity() {

    private val builtInAssetNames = setOf("geoip.db", "geosite.db")
    private val updatingNames = mutableSetOf<String>()
    private var uiState by mutableStateOf(AssetsUiState())

    private val rulesProviders = listOf(
        RuleAssetsProvider("SagerNet/sing-geoip", "SagerNet/sing-geosite"),
        RuleAssetsProvider("soffchen/sing-geoip", "soffchen/sing-geosite"),
        RuleAssetsProvider("Chocolate4U/Iran-sing-box-rules"),
        RuleAssetsProvider("L11R/antizapret-sing-box-geo"),
        RuleAssetsProvider("malikshi/sing-box-geo"),
    )

    private val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file == null) return@registerForActivityResult
        val fileName = contentResolver.query(file, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }?.takeIf { it.isNotBlank() } ?: file.pathSegments.lastOrNull()
            ?.substringAfterLast('/')
            ?.substringAfter(':')
            .orEmpty()

        if (!fileName.endsWith(".db")) {
            uiState = uiState.copy(errorMessage = getString(R.string.route_not_asset, fileName))
            return@registerForActivityResult
        }

        val dir = assetDir()
        runOnDefaultDispatcher {
            val outFile = File(dir, fileName).apply {
                parentFile?.mkdirs()
            }
            contentResolver.openInputStream(file)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            File(outFile.parentFile, outFile.nameWithoutExtension + ".version.txt").apply {
                if (isFile) delete()
                createNewFile()
                FileWriter(this).use { it.write("Custom") }
            }
            onMainDispatcher {
                reloadAssets()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMikuContent {
            AssetsScreen(
                state = uiState,
                onBack = { finish() },
                onImportFile = { startFilesForResult(importFile, "*/*") },
                onRefresh = { reloadAssets() },
                onUpdateAsset = { name -> startUpdate(name) },
                onDeleteAsset = { name -> deleteCustomAsset(name) },
                onDismissStatus = { uiState = uiState.copy(statusMessage = null) },
                onDismissError = { uiState = uiState.copy(errorMessage = null) },
            )
        }

        reloadAssets()
    }

    override fun onResume() {
        super.onResume()
        reloadAssets()
    }

    private fun assetDir(): File = getExternalFilesDir(null) ?: filesDir

    private fun reloadAssets() {
        val dir = assetDir()
        val extraFiles = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".db") && it.name !in builtInAssetNames }
            .orEmpty()

        val allFiles = buildList {
            add(File(dir, "geoip.db"))
            add(File(dir, "geosite.db"))
            addAll(extraFiles)
        }

        val items = allFiles.map { file ->
            val versionFile = File(file.parentFile, "${file.nameWithoutExtension}.version.txt")
            val localVersion = if (file.isFile) {
                if (versionFile.isFile) {
                    try {
                        versionFile.readText().trim()
                    } catch (_: Throwable) {
                        "<unknown>"
                    }
                } else {
                    "Unknown-" + DateFormat.getDateFormat(app).format(Date(file.lastModified()))
                }
            } else {
                "<unknown>"
            }
            AssetItemUi(
                fileName = file.name,
                localVersion = localVersion,
                isBuiltIn = file.name in builtInAssetNames,
                isUpdating = file.name in updatingNames,
            )
        }

        uiState = uiState.copy(assets = items)
    }

    private fun deleteCustomAsset(fileName: String) {
        if (fileName in builtInAssetNames) return
        val dir = assetDir()
        runOnDefaultDispatcher {
            File(dir, fileName).delete()
            File(dir, fileName.substringBeforeLast('.') + ".version.txt").delete()
            onMainDispatcher {
                reloadAssets()
            }
        }
    }

    private fun startUpdate(fileName: String) {
        if (fileName in updatingNames) return
        val item = uiState.assets.firstOrNull { it.fileName == fileName } ?: return
        val dir = assetDir()
        val file = File(dir, fileName)
        val versionFile = File(dir, "${file.nameWithoutExtension}.version.txt")

        updatingNames.add(fileName)
        reloadAssets()

        runOnDefaultDispatcher {
            val result = runCatching {
                updateAsset(file, versionFile, item.localVersion)
            }
            onMainDispatcher {
                updatingNames.remove(fileName)
                reloadAssets()
                result.onFailure { err ->
                    uiState = uiState.copy(errorMessage = err.readableMessage)
                }
            }
        }
    }

    private suspend fun updateAsset(file: File, versionFile: File, localVersion: String) {
        if (DataStore.rulesProvider == 5) {
            return updateCustomAsset(file, versionFile)
        }
        val fileName = file.name
        val providerIndex = DataStore.rulesProvider.coerceIn(0, rulesProviders.lastIndex)
        val repo = rulesProviders[providerIndex].repoByFileName[fileName]
            ?: error("Unsupported asset $fileName")

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }

        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()

            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name")

            if (tagName == localVersion) {
                onMainDispatcher {
                    uiState = uiState.copy(statusMessage = getString(R.string.route_asset_no_update))
                }
                return
            }

            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val assetToDownload = releaseAssets.find { it.getStr("name") == fileName }
                ?: error("File $fileName not found in release ${release["url"]}")
            val browserDownloadUrl = assetToDownload.getStr("browser_download_url")

            response = client.newRequest().apply {
                setURL(browserDownloadUrl)
            }.execute()

            val cacheFile = File(file.parentFile, "$fileName.tmp")
            cacheFile.parentFile?.mkdirs()
            response.writeTo(cacheFile.canonicalPath)

            if (fileName.endsWith(".xz")) {
                Libcore.unxz(cacheFile.absolutePath, file.absolutePath)
                cacheFile.delete()
            } else {
                cacheFile.renameTo(file)
            }

            versionFile.writeText(tagName)

            onMainDispatcher {
                uiState = uiState.copy(statusMessage = getString(R.string.route_asset_updated))
            }
        } finally {
            client.close()
        }
    }

    private suspend fun updateCustomAsset(file: File, versionFile: File) {
        val fileName = file.name
        val url = when (fileName) {
            "geoip.db" -> DataStore.rulesGeoipUrl
            "geosite.db" -> DataStore.rulesGeositeUrl
            else -> return
        }
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }
        try {
            val response = client.newRequest().apply {
                setURL(url)
            }.execute()
            val cacheFile = File(file.parentFile, "$fileName.tmp")
            cacheFile.parentFile?.mkdirs()
            response.writeTo(cacheFile.canonicalPath)
            cacheFile.renameTo(file)

            val currentDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            versionFile.writeText(currentDate)

            onMainDispatcher {
                uiState = uiState.copy(statusMessage = getString(R.string.route_asset_updated))
            }
        } finally {
            client.close()
        }
    }

    private data class RuleAssetsProvider(
        val repoByFileName: Map<String, String>,
    ) {
        constructor(
            geoipRepo: String,
            geositeRepo: String = geoipRepo,
        ) : this(
            mapOf(
                "geoip.db" to geoipRepo,
                "geosite.db" to geositeRepo,
            )
        )
    }
}
