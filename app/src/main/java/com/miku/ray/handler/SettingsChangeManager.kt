package com.miku.ray.handler

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _setupGroupTab = MutableStateFlow(false)
    private val _refreshDisplayPrefs = MutableStateFlow(false)

    fun makeRestartService() {
        _restartService.value = true
    }

    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    fun makeSetupGroupTab() {
        _setupGroupTab.value = true
    }

    fun consumeSetupGroupTab(): Boolean {
        val v = _setupGroupTab.value
        _setupGroupTab.value = false
        return v
    }

    fun makeRefreshDisplayPrefs() {
        _refreshDisplayPrefs.value = true
    }

    fun consumeRefreshDisplayPrefs(): Boolean {
        val v = _refreshDisplayPrefs.value
        _refreshDisplayPrefs.value = false
        return v
    }

    private val _uiCustomizationChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val uiCustomizationChanged: SharedFlow<Unit> = _uiCustomizationChanged

    fun notifyUiCustomizationChanged() {
        _uiCustomizationChanged.tryEmit(Unit)
    }

    private val _recreateVersion = MutableStateFlow(0L)
    val recreateVersion: StateFlow<Long> = _recreateVersion

    fun requestRecreate() {
        _recreateVersion.value += 1
    }
}
