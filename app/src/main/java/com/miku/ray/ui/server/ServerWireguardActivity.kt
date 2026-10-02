package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.miku.ray.AppConfig.WIREGUARD_LOCAL_MTU
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerWireguardBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.nullIfBlank
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.server.fields.AddressPortFields
import com.miku.ray.util.Utils
import com.miku.ray.util.showDeleteConfirmDialog

class ServerWireguardActivity : BaseActivity() {
    private val binding by lazy { ActivityServerWireguardBinding.inflate(layoutInflater) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
        && editGuid.isNotEmpty()
        && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.WIREGUARD.value)) ?: EConfigType.WIREGUARD
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var addressPortFields: AddressPortFields

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()

        addressPortFields = AddressPortFields(binding.root)

        setupToolbar(binding.toolbar, showHomeAsUp = true, title = (config?.configType ?: createConfigType).toString(), subtitle = getString(R.string.subtitle_server_config))

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        addressPortFields.bind(config)

        binding.etId.text = Utils.getEditable(config.secretKey.orEmpty())
        binding.etPublicKey.text = Utils.getEditable(config.publicKey.orEmpty())
        binding.etPresharedKey.visibility = View.VISIBLE
        binding.etPresharedKey.text = Utils.getEditable(config.preSharedKey.orEmpty())
        binding.etReserved1.text = Utils.getEditable(config.reserved ?: "0,0,0")
        binding.etLocalAddress.text = Utils.getEditable(config.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4)
        binding.etLocalMtu.text = Utils.getEditable(config.mtu?.toString() ?: WIREGUARD_LOCAL_MTU)
        binding.etFm.text = Utils.getEditable(config.finalMask)
        return true
    }

    private fun clearServer(): Boolean {
        addressPortFields.clear()
        binding.etId.text = null
        binding.etPublicKey.text = null
        binding.etReserved1.text = Utils.getEditable("0,0,0")
        binding.etLocalAddress.text = Utils.getEditable(WIREGUARD_LOCAL_ADDRESS_V4)
        binding.etLocalMtu.text = Utils.getEditable(WIREGUARD_LOCAL_MTU)
        binding.etFm.text = null
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(addressPortFields.remarksText)) {
            snackbarError(getString(R.string.server_lab_remarks), title = getString(R.string.title_alerter_error))
            return false
        }
        if (TextUtils.isEmpty(addressPortFields.addressText)) {
            snackbarError(getString(R.string.server_lab_address), title = getString(R.string.title_alerter_error))
            return false
        }
        if (Utils.parseInt(addressPortFields.portText) <= 0) {
            snackbarError(getString(R.string.server_lab_port), title = getString(R.string.title_alerter_error))
            return false
        }
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)

        if (TextUtils.isEmpty(binding.etId.text.toString())) {
            snackbarError(getString(R.string.server_lab_id), title = getString(R.string.title_alerter_error))
            return false
        }

        saveCommon(config)

        config.description = AngConfigManager.generateDescription(config)

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        val saveError = MikuProfileFormSaver.save(this, editGuid, config)
        if (saveError != null) {
            snackbarError(
                getString(R.string.mihomo_config_parse_failed, saveError),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun saveCommon(config: ProfileItem) {
        addressPortFields.save(config)

        config.secretKey = binding.etId.text.toString().trim()
        config.publicKey = binding.etPublicKey.text.toString().trim()
        config.preSharedKey = binding.etPresharedKey.text.toString().trim()
        config.reserved = binding.etReserved1.text.toString().trim()
        config.localAddress = binding.etLocalAddress.text.toString().trim()
        config.mtu = Utils.parseInt(binding.etLocalMtu.text.toString())
        config.finalMask = binding.etFm.text?.toString()?.trim()?.nullIfBlank()
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (editGuid != MmkvManager.getSelectServer() && !MmkvManager.isServerPinned(editGuid)) {
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                    showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                        MmkvManager.removeServer(editGuid)
                        SettingsChangeManager.makeSetupGroupTab()
                        toastSuccess(R.string.toast_delete_success)
                        finish()
                    }
                } else {
                    MmkvManager.removeServer(editGuid)
                    SettingsChangeManager.makeSetupGroupTab()
                    toastSuccess(R.string.toast_delete_success)
                    finish()
                }
            } else if (MmkvManager.isServerPinned(editGuid)) {
                snackbarError(getString(R.string.toast_pinned_server_delete_blocked), title = getString(R.string.title_alerter_error))
            } else {
                snackbarError(getString(R.string.toast_action_not_allowed), title = getString(R.string.title_alerter_error))
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        val delButton = menu.findItem(R.id.del_config)
        val saveButton = menu.findItem(R.id.save_config)

        if (editGuid.isNotEmpty()) {
            if (isRunning) {
                delButton?.isVisible = false
                saveButton?.isVisible = false
            }
        } else {
            delButton?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }
        R.id.save_config -> {
            saveServer()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
