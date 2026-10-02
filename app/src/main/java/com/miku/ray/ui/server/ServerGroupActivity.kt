package com.miku.ray.ui.server

import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import com.miku.ray.util.showDeleteConfirmDialog
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityServerGroupBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.util.Utils

class ServerGroupActivity : BaseActivity() {
    private val binding by lazy { ActivityServerGroupBinding.inflate(layoutInflater) }

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
        && editGuid.isNotEmpty()
        && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }

    private val subIds = mutableListOf<String>()
    private val displayList = mutableListOf<String>()

    private val policyGroupTypes: Array<out String> by lazy {
        resources.getStringArray(R.array.policy_group_type)
    }

    private val fallbackSuggestions: List<String> by lazy {
        (AppConfig.BUILTIN_OUTBOUND_TAGS + SettingsManager.getProfileRemarks(
                excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP)
        )).filter { it != AppConfig.TAG_PROXY }
    }

    private val boolEntries: Array<out String> by lazy { resources.getStringArray(R.array.bool_dropdown_entries) }
    private val boolValues: Array<out String> by lazy { resources.getStringArray(R.array.bool_dropdown_values) }

    private fun boolEntryFor(value: Boolean): String {
        val idx = boolValues.indexOf(value.toString())
        return boolEntries.getOrElse(if (idx >= 0) idx else 1) { value.toString() }
    }

    private fun boolValueFrom(text: String?): Boolean {
        val idx = Utils.arrayFind(boolEntries, text.orEmpty())
        return boolValues.getOrElse(if (idx >= 0) idx else 1) { "false" } == "true"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.serverScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = EConfigType.POLICYGROUP.toString(), subtitle = getString(R.string.subtitle_server_config))

        val config = MmkvManager.decodeServerConfig(editGuid)

        populateSubscriptionSpinner()
        populateFallbackSuggestions()

        binding.spPolicyGroupType.setOnItemClickListener { _, _, _, _ -> updateFallbackVisibility() }
        binding.chkPolicyGroupTestOutbounds.setOnItemClickListener { _, _, _, _ -> updateFallbackVisibility() }

        if (config != null) {
            bindingServer(config)
        } else {
            clearServer()
        }
        updateFallbackVisibility()
    }

    private fun populateFallbackSuggestions() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fallbackSuggestions)
        binding.spPolicyGroupFallback.setAdapter(adapter)
    }

    private fun updateFallbackVisibility() {
        val typePos = policyGroupTypes.indexOf(binding.spPolicyGroupType.text.toString()).let { if (it >= 0) it else 0 }
        val strategyType = BalancerStrategyType.from(typePos.toString())
        val supportsObservatory = strategyType.supportsObservatory
        binding.layoutPolicyGroupTestOutbounds.visibility = if (supportsObservatory) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutPolicyGroupFallback.visibility =
        if (supportsObservatory && boolValueFrom(binding.chkPolicyGroupTestOutbounds.text.toString())) android.view.View.VISIBLE else android.view.View.GONE

        val usesObservatory = strategyType.requiresObservatory
        || (supportsObservatory && boolValueFrom(binding.chkPolicyGroupTestOutbounds.text.toString()))
        val usesBurstObservatory = strategyType.requiresBurstObservatory
        binding.layoutPolicyGroupObservatoryPing.visibility = if (usesObservatory) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutPolicyGroupObservatoryLoad.visibility = if (usesBurstObservatory) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun bindingServer(config: ProfileItem): Boolean {
        binding.etRemarks.text = Utils.getEditable(config.remarks)
        binding.etPolicyGroupFilter.text = Utils.getEditable(config.policyGroupFilter)

        val typeIndex = config.policyGroupType?.toIntOrNull() ?: 0
        if (typeIndex in policyGroupTypes.indices) {
            binding.spPolicyGroupType.setText(policyGroupTypes[typeIndex], false)
        }

        val pos = subIds.indexOf(config.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
        if (pos in displayList.indices) {
            binding.spPolicyGroupSubId.setText(displayList[pos], false)
        }

        val supportsObservatory = BalancerStrategyType.from(config.policyGroupType).supportsObservatory
        binding.chkPolicyGroupTestOutbounds.setText(
            boolEntryFor(config.policyGroupTestOutbounds != false || !supportsObservatory), false
        )
        binding.spPolicyGroupFallback.setText(config.policyGroupFallbackTag.orEmpty(), false)

        binding.etPolicyGroupObservatoryLeastPingInterval.text =
        Utils.getEditable(config.policyGroupObservatoryLeastPingInterval ?: AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
        binding.etPolicyGroupObservatoryLeastLoadInterval.text =
        Utils.getEditable(config.policyGroupObservatoryLeastLoadInterval ?: AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
        binding.spPolicyGroupObservatoryLeastLoadMethod.setText(
            config.policyGroupObservatoryLeastLoadMethod ?: AppConfig.OBSERVATORY_LEAST_LOAD_METHOD, false
        )
        binding.etPolicyGroupObservatoryLeastLoadSampling.text =
        Utils.getEditable(config.policyGroupObservatoryLeastLoadSampling ?: AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
        binding.etPolicyGroupObservatoryLeastLoadTimeout.text =
        Utils.getEditable(config.policyGroupObservatoryLeastLoadTimeout ?: AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)

        updateFallbackVisibility()

        return true
    }

    private fun clearServer(): Boolean {
        binding.etRemarks.text = null
        binding.etPolicyGroupFilter.text = null

        if (policyGroupTypes.isNotEmpty()) {
            binding.spPolicyGroupType.setText(policyGroupTypes[0], false)
        }

        if (subscriptionId.isNotNullEmpty()) {
            val pos = subIds.indexOf(subscriptionId).let { if (it >= 0) it else 0 }
            if (pos in displayList.indices) {
                binding.spPolicyGroupSubId.setText(displayList[pos], false)
            }
        } else if (displayList.isNotEmpty()) {
            binding.spPolicyGroupSubId.setText(displayList[0], false)
        }

        binding.chkPolicyGroupTestOutbounds.setText(boolEntryFor(true), false)
        binding.spPolicyGroupFallback.setText("", false)

        binding.etPolicyGroupObservatoryLeastPingInterval.text = Utils.getEditable(AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
        binding.etPolicyGroupObservatoryLeastLoadInterval.text = Utils.getEditable(AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
        binding.spPolicyGroupObservatoryLeastLoadMethod.setText(AppConfig.OBSERVATORY_LEAST_LOAD_METHOD, false)
        binding.etPolicyGroupObservatoryLeastLoadSampling.text = Utils.getEditable(AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING)
        binding.etPolicyGroupObservatoryLeastLoadTimeout.text = Utils.getEditable(AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)

        updateFallbackVisibility()
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

        val typePos = policyGroupTypes.indexOf(binding.spPolicyGroupType.text.toString()).let { if (it >= 0) it else 0 }
        val strategyType = BalancerStrategyType.from(typePos.toString())
        val usesObservatory = strategyType.requiresObservatory
        || (strategyType.supportsObservatory && boolValueFrom(binding.chkPolicyGroupTestOutbounds.text.toString()))
        val usesBurstObservatory = strategyType.requiresBurstObservatory

        val pingInterval = binding.etPolicyGroupObservatoryLeastPingInterval.text.toString().trim()
        if (usesObservatory && !AppConfig.OBSERVATORY_DURATION_PATTERN.matches(pingInterval)) {
            snackbarError(getString(R.string.toast_invalid_observatory_duration), title = getString(R.string.title_alerter_error))
            return false
        }
        val loadInterval = binding.etPolicyGroupObservatoryLeastLoadInterval.text.toString().trim()
        val loadTimeout = binding.etPolicyGroupObservatoryLeastLoadTimeout.text.toString().trim()
        if (usesBurstObservatory
            && (!AppConfig.OBSERVATORY_DURATION_PATTERN.matches(loadInterval) || !AppConfig.OBSERVATORY_DURATION_PATTERN.matches(loadTimeout))
        ) {
            snackbarError(getString(R.string.toast_invalid_observatory_duration), title = getString(R.string.title_alerter_error))
            return false
        }
        val loadSampling = binding.etPolicyGroupObservatoryLeastLoadSampling.text.toString().trim()
        if (usesBurstObservatory && (loadSampling.toIntOrNull()?.let { it > 0 } != true)) {
            snackbarError(getString(R.string.toast_invalid_observatory_sampling), title = getString(R.string.title_alerter_error))
            return false
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.POLICYGROUP)
        config.remarks = binding.etRemarks.text.toString().trim()
        config.policyGroupFilter = binding.etPolicyGroupFilter.text.toString().trim()

        val selectedTypeStr = binding.spPolicyGroupType.text.toString()
        config.policyGroupType = typePos.toString()

        val selectedSubStr = binding.spPolicyGroupSubId.text.toString()
        val selPos = displayList.indexOf(selectedSubStr)
        config.policyGroupSubscriptionId = if (selPos >= 0 && selPos < subIds.size) subIds[selPos] else null

        config.policyGroupTestOutbounds = boolValueFrom(binding.chkPolicyGroupTestOutbounds.text?.toString())
        config.policyGroupFallbackTag = binding.spPolicyGroupFallback.text.toString().trim().takeIf { it.isNotEmpty() }

        config.policyGroupObservatoryLeastPingInterval = pingInterval.takeIf { it.isNotEmpty() }
        config.policyGroupObservatoryLeastLoadInterval = loadInterval.takeIf { it.isNotEmpty() }
        config.policyGroupObservatoryLeastLoadMethod =
        binding.spPolicyGroupObservatoryLeastLoadMethod.text.toString().trim().takeIf { it.isNotEmpty() }
        config.policyGroupObservatoryLeastLoadSampling = loadSampling.takeIf { it.isNotEmpty() }
        config.policyGroupObservatoryLeastLoadTimeout = loadTimeout.takeIf { it.isNotEmpty() }

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        config.description = "$selectedTypeStr - $selectedSubStr - ${config.policyGroupFilter}"

        MmkvManager.encodeServerConfig(editGuid, config)
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

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        displayList.clear()
        subIds.clear()

        displayList.add(getString(R.string.filter_config_all))
        subIds.add("")

        subs.forEach { sub ->
            val name = when {
                sub.subscription.remarks.isNotBlank() -> sub.subscription.remarks
                else -> sub.guid
            }
            displayList.add(name)
            subIds.add(sub.guid)
        }

        val subAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayList)
        binding.spPolicyGroupSubId.setAdapter(subAdapter)
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
