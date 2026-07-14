package top.uwu.mikubox.ui

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.uwu.mikubox.R
import top.uwu.mikubox.core.MihomoCore
import top.uwu.mikubox.core.MihomoDnsSettings
import top.uwu.mikubox.databinding.ActivityDnsBinding

/** Editor for the user-configurable DNS block that overrides a profile's `dns:`. */
class DnsActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityDnsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.etDns.setText(MihomoDnsSettings.yaml(this))
        binding.swOverride.isChecked = MihomoDnsSettings.overrideEnabled(this)
        applyEnabled(binding.swOverride.isChecked)

        binding.swOverride.setOnCheckedChangeListener { _, checked ->
            MihomoDnsSettings.setOverrideEnabled(this, checked)
            applyEnabled(checked)
        }
        binding.btnSave.setOnClickListener {
            val yaml = binding.etDns.text?.toString().orEmpty()
            val error = MihomoCore.validateDns(yaml)
            if (error != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dns_invalid_title)
                    .setMessage(error)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@setOnClickListener
            }
            MihomoDnsSettings.setYaml(this, yaml)
            toast(R.string.toast_dns_saved)
        }
        binding.btnReset.setOnClickListener {
            MihomoDnsSettings.resetYaml(this)
            binding.etDns.setText(MihomoDnsSettings.DEFAULT_YAML)
            toast(R.string.toast_dns_reset)
        }
    }

    private fun applyEnabled(enabled: Boolean) {
        binding.tilDns.isEnabled = enabled
        binding.etDns.isEnabled = enabled
        binding.btnSave.isEnabled = enabled
        binding.btnReset.isEnabled = enabled
    }

    private fun toast(messageRes: Int) = Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
