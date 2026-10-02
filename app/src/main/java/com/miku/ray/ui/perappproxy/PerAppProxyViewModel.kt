package com.miku.ray.ui.perappproxy

import androidx.lifecycle.ViewModel
import com.miku.ray.AppConfig
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PerAppProxyViewModel : ViewModel() {
    private val _blacklist = MutableStateFlow<Set<String>>(
        MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toSet()
            ?: emptySet(),
    )
    val blacklist: StateFlow<Set<String>> = _blacklist.asStateFlow()

    fun contains(packageName: String): Boolean = packageName in _blacklist.value

    fun getAll(): Set<String> = _blacklist.value

    fun add(packageName: String): Boolean {
        if (packageName in _blacklist.value) return false
        _blacklist.value = _blacklist.value + packageName
        save()
        return true
    }

    fun remove(packageName: String): Boolean {
        if (packageName !in _blacklist.value) return false
        _blacklist.value = _blacklist.value - packageName
        save()
        return true
    }

    fun toggle(packageName: String) {
        if (contains(packageName)) remove(packageName) else add(packageName)
    }

    fun addAll(packages: Collection<String>) {
        val next = _blacklist.value + packages.toSet()
        if (next.size == _blacklist.value.size) return
        _blacklist.value = next
        save()
    }

    fun removeAll(packages: Collection<String>) {
        val next = _blacklist.value - packages.toSet()
        if (next.size == _blacklist.value.size) return
        _blacklist.value = next
        save()
    }

    fun clear() {
        if (_blacklist.value.isEmpty()) return
        _blacklist.value = emptySet()
        save()
    }

    private fun save() {
        MmkvManager.encodeSettings(
            AppConfig.PREF_PER_APP_PROXY_SET,
            _blacklist.value.toMutableSet(),
        )
        SettingsChangeManager.makeRestartService()
    }
}
