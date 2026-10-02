package com.miku.ray.ui.routing

import androidx.lifecycle.ViewModel
import com.miku.ray.dto.entities.RulesetItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoutingSettingsViewModel : ViewModel() {
    private val _rulesets = MutableStateFlow<List<RulesetItem>>(emptyList())
    val rulesets: StateFlow<List<RulesetItem>> = _rulesets.asStateFlow()

    fun getAll(): List<RulesetItem> = _rulesets.value

    fun reload() {
        val loaded = MmkvManager.decodeRoutingRulesets() ?: mutableListOf()
        if (SettingsManager.ensureRoutingRulesetIds(loaded)) {
            MmkvManager.encodeRoutingRulesets(loaded)
        }
        _rulesets.value = loaded.toList()
    }

    fun update(position: Int, item: RulesetItem) {
        val current = _rulesets.value.toMutableList()
        if (position !in current.indices) return
        current[position] = item
        _rulesets.value = current
        SettingsManager.saveRoutingRuleset(position, item)
    }

    fun remove(position: Int) {
        val current = _rulesets.value.toMutableList()
        if (position !in current.indices) return
        current.removeAt(position)
        _rulesets.value = current
        SettingsManager.removeRoutingRuleset(position)
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        val current = _rulesets.value
        if (fromPosition !in current.indices || toPosition !in current.indices) return
        SettingsManager.swapRoutingRuleset(fromPosition, toPosition)
    }
}
