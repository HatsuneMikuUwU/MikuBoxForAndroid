package com.miku.ray.ui.preference.activity

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.remixicon.R as RemixR
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastError
import com.miku.ray.extension.toastInfo
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.MmkvPreferenceDataStore
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.preference.CategoryStyleHelper
import com.miku.ray.ui.preference.SearchPreferenceHighlighter
import com.miku.ray.util.showBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Notification display and connection sounds. */
class AlertSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_ui_alerts), subtitle = getString(R.string.subtitle_ui_alerts))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, AlertSettingsFragment())
            .commit()
        }
    }

    class AlertSettingsFragment : PreferenceFragmentCompat() {

        private val customConnectSound by lazy { findPreference<Preference>("action_pick_custom_connect_sound") }
        private val customDisconnectSound by lazy { findPreference<Preference>("action_pick_custom_disconnect_sound") }
        private val deleteCustomSounds by lazy { findPreference<Preference>("action_delete_custom_sounds") }

        private val pickCustomConnectSoundFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) saveCustomSound(uri, AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI, "connect_sound_")
        }

        private val pickCustomDisconnectSoundFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) saveCustomSound(uri, AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI, "disconnect_sound_")
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            preferenceManager.preferenceDataStore = MmkvPreferenceDataStore(triggersServiceRestart = false)
            addPreferencesFromResource(R.xml.pref_ui_alerts)
            initPreferenceSummaries()
            setupCustomSoundPreferences()
            CategoryStyleHelper.applyToFragment(this)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            SearchPreferenceHighlighter.applyFromIntent(this)
            applyEdgeToEdgeListInsets()
        }

        private fun setupCustomSoundPreferences() {
            updateCustomSoundSummaries()
            customConnectSound?.setOnPreferenceClickListener {
                pickCustomConnectSoundFile.launch(arrayOf("*/*"))
                true
            }
            customDisconnectSound?.setOnPreferenceClickListener {
                pickCustomDisconnectSoundFile.launch(arrayOf("*/*"))
                true
            }
            deleteCustomSounds?.setOnPreferenceClickListener {
                val hasCustomSound = listOf(
                    AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI,
                    AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI
                ).any { !MmkvManager.decodeSettingsString(it).isNullOrBlank() }
                if (!hasCustomSound) {
                    requireContext().toastInfo(getString(R.string.custom_sound_none_to_remove))
                    return@setOnPreferenceClickListener true
                }
                MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_pref_delete_custom_sounds)
                .setMessage(R.string.custom_sound_delete_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        deleteCustomSound(AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI)
                        deleteCustomSound(AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI)
                        updateCustomSoundSummaries()
                        requireContext().snackbarSuccess(
                            getString(R.string.custom_sounds_removed),
                            title = getString(R.string.title_alerter_success)
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
                true
            }
        }

        private fun updateCustomSoundSummaries() {
            customConnectSound?.summary = customSoundSummary(AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI)
            customDisconnectSound?.summary = customSoundSummary(AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI)
            deleteCustomSounds?.isEnabled = listOf(
                AppConfig.PREF_CUSTOM_CONNECT_SOUND_URI,
                AppConfig.PREF_CUSTOM_DISCONNECT_SOUND_URI
            ).any { !MmkvManager.decodeSettingsString(it).isNullOrBlank() }
        }

        private fun customSoundSummary(preferenceKey: String): String {
            val uriString = MmkvManager.decodeSettingsString(preferenceKey).orEmpty()
            return if (uriString.isBlank()) {
                getString(R.string.summary_pref_custom_connect_sound)
            } else {
                File(Uri.parse(uriString).path.orEmpty()).name.ifBlank { uriString }
            }
        }

        private fun saveCustomSound(sourceUri: Uri, preferenceKey: String, fileNamePrefix: String) {
            lifecycleScope.launch {
                val savedUri = withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = File(requireContext().filesDir, "sounds").apply { mkdirs() }
                        val name = queryDisplayName(sourceUri)?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        ?.takeIf { it.isNotBlank() } ?: "sound.m4a"
                        val extension = name.substringAfterLast('.', "m4a").lowercase()
                        val destination = File(directory, "$fileNamePrefix${System.currentTimeMillis()}.$extension")
                        requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                            destination.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Unable to read audio")
                        val savedUri = Uri.fromFile(destination)
                        val mediaPlayer = MediaPlayer()
                        try {
                            mediaPlayer.setDataSource(requireContext(), savedUri)
                            mediaPlayer.prepare()
                        } catch (error: Exception) {
                            destination.delete()
                            throw error
                        } finally {
                            mediaPlayer.release()
                        }
                        savedUri
                    }.getOrNull()
                }
                if (savedUri == null) {
                    requireContext().toastError(getString(R.string.custom_sound_invalid))
                } else {
                    deleteCustomSound(preferenceKey)
                    MmkvManager.encodeSettings(preferenceKey, savedUri.toString())
                    updateCustomSoundSummaries()
                    requireContext().toastSuccess(getString(R.string.custom_sound_added))
                }
            }
        }

        private suspend fun deleteCustomSound(preferenceKey: String) {
            val oldUri = MmkvManager.decodeSettingsString(preferenceKey)
            deleteOldFile(oldUri)
            MmkvManager.encodeSettings(preferenceKey, "")
        }

        private suspend fun deleteOldFile(uriString: String?) = withContext(Dispatchers.IO) {
            if (uriString.isNullOrEmpty()) return@withContext
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    File(uri.path!!).takeIf { it.exists() }?.delete()
                } else {
                    try { requireContext().contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        private fun queryDisplayName(uri: Uri): String? {
            return try {
                requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun initPreferenceSummaries() {
            fun traverse(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    when (val p = group.getPreference(i)) {
                        is androidx.preference.PreferenceGroup -> traverse(p)
                        is androidx.preference.ListPreference -> {
                            if (p.value == null && !p.entryValues.isNullOrEmpty()) {
                                p.value = p.entryValues[0].toString()
                            }
                            p.summary = p.entry ?: ""
                            p.setOnPreferenceChangeListener { pref, newValue ->
                                val lp = pref as androidx.preference.ListPreference
                                val idx = lp.findIndexOfValue(newValue as? String)
                                lp.summary = (if (idx >= 0) lp.entries[idx] else newValue) as CharSequence?
                                true
                            }
                        }
                        else -> {}
                    }
                }
            }
            preferenceScreen?.let { traverse(it) }
        }
    }
}
