package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.miku.ray.util.showDeleteConfirmDialog
import com.blacksquircle.ui.editorkit.utils.EditorTheme
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerCustomConfigBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.fmt.CustomFmt
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

class ServerCustomConfigActivity : BaseActivity() {
    private val binding by lazy { ActivityServerCustomConfigBinding.inflate(layoutInflater) }

    private lateinit var visualEditor: VisualConfigEditor
    private var visualMode = false
    private var selectedGroup = AppConfig.DEFAULT_SUBSCRIPTION_ID

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
        && editGuid.isNotEmpty()
        && editGuid == MmkvManager.getSelectServer()
    }

    /** The protocol card this editor was opened from, when it was a card. */
    private val createConfigType by lazy { intent.getIntExtra("createConfigType", -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()

        // The per-protocol cards promise a per-protocol form; the honest title
        // names the protocol instead of the generic editor when one was tapped.
        val protocol = EConfigType.fromInt(createConfigType)
        val title = when {
            protocol != null && protocol != EConfigType.CUSTOM -> "${getString(R.string.profile_editor_title)} · ${protocol.name}"
            com.miku.ray.MikuProfiles.impl != null -> getString(R.string.profile_editor_title)
            else -> EConfigType.CUSTOM.toString()
        }
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = title, subtitle = getString(R.string.subtitle_server_config))

        if (!Utils.getDarkModeStatus(this)) {
            binding.editor.colorScheme = EditorTheme.INTELLIJ_LIGHT
        }
        binding.editor.language = JsonLanguage()
        selectedGroup = savedInstanceState?.getString("profileGroup") ?: com.miku.ray.MikuProfiles.impl?.get(editGuid)?.groupId ?: intent.getStringExtra("groupId")?.takeIf { it.isNotBlank() } ?: AppConfig.DEFAULT_SUBSCRIPTION_ID
        val config = MmkvManager.decodeServerConfig(editGuid)
        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
            binding.editor.setTextContent(Utils.getEditable(templateFor(protocol) ?: DEFAULT_TEMPLATE))
        }
        savedInstanceState?.getString("yamlDraft")?.let { binding.editor.setTextContent(Utils.getEditable(it)) }
        savedInstanceState?.getString("nameDraft")?.let { binding.etRemarks.setText(it) }
        visualEditor = VisualConfigEditor(this) { yaml -> binding.editor.setTextContent(Utils.getEditable(yaml)) }
        binding.visualEditorContainer.addView(visualEditor)
        fun refreshGroupLabel() {
            val group = com.miku.ray.MikuProfiles.impl?.groups()?.firstOrNull { it.id == selectedGroup }
            binding.profileGroup.text = getString(R.string.profile_editor_group, group?.name ?: "Miku")
        }
        refreshGroupLabel()
        binding.profileGroup.setOnClickListener {
            ProfileGroupPicker.choose(this, selectedGroup) { selectedGroup = it.id; refreshGroupLabel() }
        }
        setVisualMode(savedInstanceState?.getBoolean("visualMode", true) ?: true)
        binding.editorMode.addOnButtonCheckedListener { _, id, checked ->
            if (checked && (id == R.id.mode_visual) != visualMode) setVisualMode(id == R.id.mode_visual)
        }
    }

    private fun setVisualMode(enabled: Boolean) {
        if (enabled) {
            try { visualEditor.load(binding.editor.text.toString()) }
            catch (error: Exception) {
                visualEditor.visibility = android.view.View.GONE
                binding.rawEditorCard.visibility = android.view.View.VISIBLE
                visualMode = false
                binding.editorMode.check(R.id.mode_yaml)
                snackbarError(getString(R.string.mihomo_config_parse_failed, error.message.orEmpty()), title = getString(R.string.title_alerter_error))
                return
            }
        }
        visualMode = enabled
        visualEditor.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.rawEditorCard.visibility = if (enabled) android.view.View.GONE else android.view.View.VISIBLE
        binding.editorMode.check(if (enabled) R.id.mode_visual else R.id.mode_yaml)
        invalidateOptionsMenu()
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        val raw = com.miku.ray.MikuProfiles.impl?.get(editGuid)?.config ?: MmkvManager.decodeServerRaw(editGuid)
        val configContent = raw.orEmpty()

        binding.editor.setTextContent(Utils.getEditable(configContent))
        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        return true
    }

    private fun saveServer(): Boolean {
        if (TextUtils.isEmpty(binding.etRemarks.text.toString())) {
            snackbarError(
                getString(R.string.server_lab_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        com.miku.ray.MikuProfiles.impl?.let { store ->
            return try {
                val savedId = store.save(editGuid.takeIf { it.isNotBlank() }, binding.etRemarks.text.toString(), binding.editor.text.toString())
                store.moveToGroup(savedId, selectedGroup)
                SettingsChangeManager.makeSetupGroupTab()
                toastSuccess(R.string.toast_success)
                finish()
                true
            } catch (e: Exception) {
                snackbarError(e.message.orEmpty(), title = getString(R.string.title_alerter_error))
                false
            }
        }

        val profileItem = try {
            CustomFmt.parse(binding.editor.text.toString())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse custom configuration", e)
            snackbarDefault("${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", title = getString(R.string.title_alerter_info))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.CUSTOM)
        binding.etRemarks.text.let {
            config.remarks = if (it.isNullOrEmpty()) profileItem.remarks.orEmpty() else it.toString()
        }
        config.server = profileItem.server
        config.serverPort = profileItem.serverPort
        config.network = profileItem.network
        config.security = profileItem.security
        config.description = AngConfigManager.generateDescription(config)

        MmkvManager.encodeServerConfig(editGuid, config)
        MmkvManager.encodeServerRaw(editGuid, binding.editor.text.toString())
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isNotEmpty()) {
            if (MmkvManager.isServerPinned(editGuid)) {
                snackbarError(getString(R.string.toast_pinned_server_delete_blocked), title = getString(R.string.title_alerter_error))
                return true
            }
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_config_dialog_comfirm_message) {
                MmkvManager.removeServer(editGuid)
                SettingsChangeManager.makeSetupGroupTab()
                toastSuccess(R.string.toast_delete_success)
                finish()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("profileGroup", selectedGroup)
        outState.putString("yamlDraft", binding.editor.text.toString())
        outState.putString("nameDraft", binding.etRemarks.text.toString())
        outState.putBoolean("visualMode", visualMode)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        0x6d03 -> { setVisualMode(!visualMode); true }
        0x6d02 -> {
            ProfileGroupPicker.choose(this, selectedGroup) { selectedGroup = it.id }
            true
        }
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

    private companion object {
        const val DEFAULT_TEMPLATE = "mode: rule\nrules:\n  - MATCH,DIRECT\n"

        /**
         * Per-protocol seeds for the add-config cards. Each card used to open
         * the same blank editor, so the label promised a form that never
         * appeared; now the editor opens on a valid Mihomo document shaped
         * for the tapped protocol and the user fills in the server specifics.
         */
        fun templateFor(type: EConfigType?): String? = when (type) {
            EConfigType.VMESS -> """
                # VMess outbound — fill in server, port and uuid.
                proxies:
                  - name: vmess
                    type: vmess
                    server: example.com
                    port: 443
                    uuid: 00000000-0000-0000-0000-000000000000
                    alterId: 0
                    cipher: auto
                    tls: true
                    servername: example.com
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.VLESS -> """
                # VLESS outbound — fill in server, port and uuid.
                proxies:
                  - name: vless
                    type: vless
                    server: example.com
                    port: 443
                    uuid: 00000000-0000-0000-0000-000000000000
                    tls: true
                    servername: example.com
                    flow: xtls-rprx-vision
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.TROJAN -> """
                # Trojan outbound — fill in server, port and password.
                proxies:
                  - name: trojan
                    type: trojan
                    server: example.com
                    port: 443
                    password: password
                    sni: example.com
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.SHADOWSOCKS -> """
                # Shadowsocks outbound — fill in server, port, cipher and password.
                proxies:
                  - name: shadowsocks
                    type: ss
                    server: example.com
                    port: 8388
                    cipher: aes-256-gcm
                    password: password
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.SOCKS -> """
                # SOCKS5 outbound — fill in server and port; auth is optional.
                proxies:
                  - name: socks
                    type: socks5
                    server: example.com
                    port: 1080
                    # username: user
                    # password: pass
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.HTTP -> """
                # HTTP outbound — fill in server and port; auth and TLS optional.
                proxies:
                  - name: http
                    type: http
                    server: example.com
                    port: 8080
                    # username: user
                    # password: pass
                    # tls: true
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.WIREGUARD -> """
                # WireGuard outbound — fill in server, keys and the local address.
                proxies:
                  - name: wireguard
                    type: wireguard
                    server: example.com
                    port: 51820
                    ip: 172.16.0.2
                    private-key: private-key
                    public-key: peer-public-key
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.HYSTERIA2 -> """
                # Hysteria2 outbound — fill in server, port and password.
                proxies:
                  - name: hysteria2
                    type: hysteria2
                    server: example.com
                    port: 443
                    password: password
                mode: rule
                rules:
                  - MATCH,DIRECT
            """.trimIndent() + "\n"
            EConfigType.POLICYGROUP -> """
                # Proxy group — list the proxies or groups it should choose from.
                proxies: []
                proxy-groups:
                  - name: my-group
                    type: select
                    proxies:
                      - DIRECT
                mode: rule
                rules:
                  - MATCH,my-group
            """.trimIndent() + "\n"
            EConfigType.PROXYCHAIN -> """
                # Relay chain — traffic passes through each listed proxy in order.
                proxies: []
                proxy-groups:
                  - name: relay-chain
                    type: relay
                    proxies:
                      - first-hop
                      - second-hop
                mode: rule
                rules:
                  - MATCH,relay-chain
            """.trimIndent() + "\n"
            else -> null
        }
    }

}
