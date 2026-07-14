package top.uwu.mikubox.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.uwu.mikubox.R
import top.uwu.mikubox.core.AppSettings
import top.uwu.mikubox.databinding.ActivitySettingsBinding
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import top.uwu.mikubox.core.BackupManager
import top.uwu.mikubox.profile.MihomoProfileStore
import top.uwu.mikubox.service.MihomoVpnSettings
import top.uwu.mikubox.service.MihomoVpnSettings.AppMode

class SettingsActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val nightModes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
    )

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { writeBackup(it) } }

    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { readBackup(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowTheme.setOnClickListener { pickTheme() }
        binding.rowMtu.setOnClickListener { editMtu() }
        binding.rowApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        binding.rowExport.setOnClickListener { exportBackup.launch("mikubox-backup.json") }
        binding.rowImport.setOnClickListener { importBackup.launch(arrayOf("application/json", "text/*")) }

        binding.swParticles.isChecked = AppSettings.particlesEnabled(this)
        binding.rowParticles.setOnClickListener {
            val enabled = !binding.swParticles.isChecked
            binding.swParticles.isChecked = enabled
            AppSettings.setParticlesEnabled(this, enabled)
        }

        binding.swBoot.isChecked = MihomoProfileStore.autoStart(this)
        binding.rowBoot.setOnClickListener {
            val enabled = !binding.swBoot.isChecked
            binding.swBoot.isChecked = enabled
            MihomoProfileStore.setAutoStart(this, enabled)
        }

    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.tvThemeValue.text = getString(themeLabel(AppSettings.nightMode(this)))
        binding.tvMtuValue.text = MihomoVpnSettings.mtu(this).toString()
        binding.tvAppsValue.setText(appModeLabel(MihomoVpnSettings.appMode(this)))
    }

    private fun appModeLabel(mode: AppMode): Int = when (mode) {
        AppMode.ALLOW_LIST -> R.string.per_app_mode_allow
        AppMode.DISALLOW_LIST -> R.string.per_app_mode_disallow
        else -> R.string.per_app_mode_all
    }

    private fun themeLabel(mode: Int): Int = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> R.string.settings_theme_light
        AppCompatDelegate.MODE_NIGHT_YES -> R.string.settings_theme_dark
        else -> R.string.settings_theme_system
    }

    private fun pickTheme() {
        val labels = nightModes.map { getString(themeLabel(it)) }.toTypedArray()
        val current = nightModes.indexOf(AppSettings.nightMode(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                AppSettings.setNightMode(this, nightModes[which])
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun writeBackup(uri: Uri) {
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use {
                it.write(BackupManager.export(this).toByteArray())
            } ?: error("no stream")
        }.isSuccess
        Toast.makeText(
            this,
            if (ok) R.string.toast_backup_exported else R.string.toast_backup_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun readBackup(uri: Uri) {
        val ok = runCatching {
            val json = contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("no stream")
            BackupManager.import(this, json)
        }.isSuccess
        if (ok) {
            AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this))
            render()
        }
        Toast.makeText(
            this,
            if (ok) R.string.toast_backup_imported else R.string.toast_backup_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun editMtu() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(MihomoVpnSettings.mtu(this@SettingsActivity).toString())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_mtu)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let {
                    MihomoVpnSettings.setMtu(this, it)
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
