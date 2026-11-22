package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ParcelizeBridge
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.KeyValuePair
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ktx.triggerFullRestart
import io.nekohasekai.sagernet.ktx.tryToShow
import moe.matsuri.nb4a.utils.Util
import org.json.JSONArray
import org.json.JSONObject
import top.nkbe.mikubox.ui.component.mikuComposeView
import top.nkbe.mikubox.ui.page.tools.BackupImportCandidate
import top.nkbe.mikubox.ui.page.tools.ToolsCallbacks
import top.nkbe.mikubox.ui.page.tools.ToolsScreen
import top.nkbe.mikubox.ui.page.tools.ToolsUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolsFragment : ToolbarFragment() {

    private var pendingExportContent: String = ""
    private var pendingImportJson: JSONObject? = null
    private var uiState by mutableStateOf(ToolsUiState())

    private val exportSettings =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        requireActivity().contentResolver.openOutputStream(data)!!.bufferedWriter().use {
                            it.write(pendingExportContent)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }
        }

    private val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) {
            runOnDefaultDispatcher {
                prepareImport(file)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return mikuComposeView {
            ToolsScreen(
                state = uiState,
                callbacks = ToolsCallbacks(
                    onOpenMenu = {
                        (activity as? NavigationHost)?.showNavigationSheet()
                            ?: activity?.onBackPressedDispatcher?.onBackPressed()
                    },
                    onLaunchTool = { activityClass ->
                        startActivity(Intent(requireContext(), activityClass))
                    },
                    onExportBackup = { profiles, rules, settings ->
                        runOnDefaultDispatcher {
                            pendingExportContent = doBackup(profiles, rules, settings)
                            onMainDispatcher {
                                startFilesForResult(exportSettings, backupFileName())
                            }
                        }
                    },
                    onShareBackup = { profiles, rules, settings ->
                        runOnDefaultDispatcher {
                            val payload = doBackup(profiles, rules, settings)
                            app.cacheDir.mkdirs()
                            val cacheFile = File(app.cacheDir, backupFileName())
                            cacheFile.writeText(payload)
                            onMainDispatcher {
                                startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("application/json")
                                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            .putExtra(
                                                Intent.EXTRA_STREAM,
                                                FileProvider.getUriForFile(
                                                    app,
                                                    BuildConfig.APPLICATION_ID + ".cache",
                                                    cacheFile,
                                                ),
                                            ),
                                        app.getString(R.string.abc_shareactionprovider_share_with),
                                    )
                                )
                            }
                        }
                    },
                    onPickImportFile = {
                        startFilesForResult(importFile, "*/*")
                    },
                    onConfirmImport = { profiles, rules, settings ->
                        val payload = pendingImportJson ?: return@ToolsCallbacks
                        uiState = uiState.copy(importCandidate = null, isImporting = true)
                        SagerNet.stopService()
                        runOnDefaultDispatcher {
                            runCatching {
                                finishImport(payload, profiles, rules, settings)
                                triggerFullRestart(requireContext())
                            }.onFailure { err ->
                                Logs.w(err)
                                onMainDispatcher {
                                    alert(err.readableMessage).tryToShow()
                                }
                            }
                            pendingImportJson = null
                            onMainDispatcher {
                                uiState = uiState.copy(isImporting = false)
                            }
                        }
                    },
                    onDismissImport = {
                        pendingImportJson = null
                        uiState = uiState.copy(importCandidate = null)
                    },
                    onResetSettings = {
                        DataStore.configurationStore.reset()
                        triggerFullRestart(requireContext())
                    },
                ),
            )
        }
    }

    private fun backupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "mikubox_backup_$stamp.json"
    }

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        writeToParcel(parcel, 0)
        return try {
            Util.b64EncodeUrlSafe(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    private fun doBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val out = JSONObject().apply {
            put("version", 1)
            if (profile) {
                put("profiles", JSONArray().apply {
                    SagerDatabase.proxyDao.getAll().forEach {
                        put(it.toBase64Str())
                    }
                })
                put("groups", JSONArray().apply {
                    SagerDatabase.groupDao.allGroups().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (rule) {
                put("rules", JSONArray().apply {
                    SagerDatabase.rulesDao.allRules().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
            if (setting) {
                put("settings", JSONArray().apply {
                    PublicDatabase.kvPairDao.all().forEach {
                        put(it.toBase64Str())
                    }
                })
            }
        }
        return out.toStringPretty()
    }

    private suspend fun prepareImport(file: Uri) {
        val fileName = requireContext().contentResolver.query(file, null, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
            ?.takeIf { it.isNotBlank() } ?: file.pathSegments.lastOrNull()
            ?.substringAfterLast('/')
            ?.substringAfter(':')
            .orEmpty()

        if (!fileName.endsWith(".json", ignoreCase = true)) {
            onMainDispatcher {
                snackbar(getString(R.string.backup_not_file, fileName)).show()
            }
            return
        }

        suspend fun invalid() = onMainDispatcher {
            snackbar(getString(R.string.invalid_backup_file)).show()
        }

        val content = try {
            JSONObject((requireContext().contentResolver.openInputStream(file) ?: return).use {
                it.bufferedReader().readText()
            })
        } catch (e: Exception) {
            Logs.w(e)
            invalid()
            return
        }

        val version = content.optInt("version", 0)
        if (version != 1) {
            invalid()
            return
        }

        val candidate = BackupImportCandidate(
            hasProfiles = content.has("profiles"),
            hasRules = content.has("rules"),
            hasSettings = content.has("settings"),
        )
        pendingImportJson = content
        onMainDispatcher {
            uiState = uiState.copy(importCandidate = candidate)
        }
    }

    private fun finishImport(
        content: JSONObject,
        profile: Boolean,
        rule: Boolean,
        setting: Boolean,
    ) {
        if (profile && content.has("profiles")) {
            val profiles = mutableListOf<ProxyEntity>()
            val jsonProfiles = content.getJSONArray("profiles")
            for (i in 0 until jsonProfiles.length()) {
                val data = Util.b64Decode(jsonProfiles[i] as String)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                profiles.add(ProxyEntity.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.proxyDao.reset()
            SagerDatabase.proxyDao.insert(profiles)

            val groups = mutableListOf<ProxyGroup>()
            val jsonGroups = content.getJSONArray("groups")
            for (i in 0 until jsonGroups.length()) {
                val data = Util.b64Decode(jsonGroups[i] as String)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                groups.add(ProxyGroup.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            SagerDatabase.groupDao.reset()
            SagerDatabase.groupDao.insert(groups)
        }
        if (rule && content.has("rules")) {
            val rules = mutableListOf<RuleEntity>()
            val jsonRules = content.getJSONArray("rules")
            for (i in 0 until jsonRules.length()) {
                val data = Util.b64Decode(jsonRules[i] as String)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                rules.add(ParcelizeBridge.createRule(parcel))
                parcel.recycle()
            }
            SagerDatabase.rulesDao.reset()
            SagerDatabase.rulesDao.insert(rules)
        }
        if (setting && content.has("settings")) {
            val settings = mutableListOf<KeyValuePair>()
            val jsonSettings = content.getJSONArray("settings")
            for (i in 0 until jsonSettings.length()) {
                val data = Util.b64Decode(jsonSettings[i] as String)
                val parcel = Parcel.obtain()
                parcel.unmarshall(data, 0, data.size)
                parcel.setDataPosition(0)
                settings.add(KeyValuePair.CREATOR.createFromParcel(parcel))
                parcel.recycle()
            }
            PublicDatabase.kvPairDao.reset()
            PublicDatabase.kvPairDao.insert(settings)
        }
    }
}
