package top.uwu.mikubox.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.uwu.mikubox.R
import top.uwu.mikubox.core.AppSettings
import top.uwu.mikubox.core.MihomoCoreSettings
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
        binding.rowDns.setOnClickListener {
            startActivity(Intent(this, DnsActivity::class.java))
        }
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

        binding.rowMode.setOnClickListener { pickMode() }
        binding.rowLog.setOnClickListener { pickLog() }
        binding.rowTunStack.setOnClickListener { pickStack() }
        binding.rowTestUrl.setOnClickListener { editTestUrl() }
        binding.rowTestTimeout.setOnClickListener { editTestTimeout() }

        binding.swAllowLan.isChecked = MihomoCoreSettings.allowLan(this)
        binding.rowAllowLan.setOnClickListener {
            val enabled = !binding.swAllowLan.isChecked
            binding.swAllowLan.isChecked = enabled
            MihomoCoreSettings.setAllowLan(this, enabled)
        }
        binding.swIpv6.isChecked = MihomoCoreSettings.ipv6(this)
        binding.rowIpv6.setOnClickListener {
            val enabled = !binding.swIpv6.isChecked
            binding.swIpv6.isChecked = enabled
            MihomoCoreSettings.setIpv6(this, enabled)
        }
        binding.swAutoconnect.isChecked = MihomoCoreSettings.autoConnectOnStart(this)
        binding.rowAutoconnect.setOnClickListener {
            val enabled = !binding.swAutoconnect.isChecked
            binding.swAutoconnect.isChecked = enabled
            MihomoCoreSettings.setAutoConnectOnStart(this, enabled)
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
        binding.tvModeValue.setText(modeLabel(MihomoCoreSettings.mode(this)))
        binding.tvLogValue.setText(logLabel(MihomoCoreSettings.logLevel(this)))
        binding.tvStackValue.setText(stackLabel(MihomoCoreSettings.tunStack(this)))
        binding.tvTestUrlValue.text = MihomoCoreSettings.testUrl(this)
        binding.tvTestTimeoutValue.text = getString(R.string.settings_test_timeout_value, MihomoCoreSettings.testTimeout(this))
    }

    private fun modeLabel(mode: MihomoCoreSettings.ProxyMode): Int = when (mode) {
        MihomoCoreSettings.ProxyMode.RULE -> R.string.mode_rule
        MihomoCoreSettings.ProxyMode.GLOBAL -> R.string.mode_global
        MihomoCoreSettings.ProxyMode.DIRECT -> R.string.mode_direct
        else -> R.string.mode_follow
    }

    private fun logLabel(level: MihomoCoreSettings.LogLevel): Int = when (level) {
        MihomoCoreSettings.LogLevel.SILENT -> R.string.log_silent
        MihomoCoreSettings.LogLevel.WARNING -> R.string.log_warning
        MihomoCoreSettings.LogLevel.DEBUG -> R.string.log_debug
        else -> R.string.log_info
    }

    private fun stackLabel(stack: MihomoCoreSettings.TunStack): Int = when (stack) {
        MihomoCoreSettings.TunStack.GVISOR -> R.string.stack_gvisor
        MihomoCoreSettings.TunStack.MIXED -> R.string.stack_mixed
        else -> R.string.stack_system
    }

    private fun pickMode() {
        val modes = MihomoCoreSettings.ProxyMode.values()
        val labels = modes.map { getString(modeLabel(it)) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_mode)
            .setSingleChoiceItems(labels, modes.indexOf(MihomoCoreSettings.mode(this))) { dialog, which ->
                MihomoCoreSettings.setMode(this, modes[which])
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickLog() {
        val levels = MihomoCoreSettings.LogLevel.values()
        val labels = levels.map { getString(logLabel(it)) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_log)
            .setSingleChoiceItems(labels, levels.indexOf(MihomoCoreSettings.logLevel(this))) { dialog, which ->
                MihomoCoreSettings.setLogLevel(this, levels[which])
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickStack() {
        val stacks = MihomoCoreSettings.TunStack.values()
        val labels = stacks.map { getString(stackLabel(it)) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_tun_stack)
            .setSingleChoiceItems(labels, stacks.indexOf(MihomoCoreSettings.tunStack(this))) { dialog, which ->
                MihomoCoreSettings.setTunStack(this, stacks[which])
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editTestUrl() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(MihomoCoreSettings.testUrl(this@SettingsActivity))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_test_url)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    MihomoCoreSettings.setTestUrl(this, it)
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editTestTimeout() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(MihomoCoreSettings.testTimeout(this@SettingsActivity).toString())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_test_timeout)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let {
                    MihomoCoreSettings.setTestTimeout(this, it)
                    render()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
