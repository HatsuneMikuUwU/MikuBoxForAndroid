package com.miku.ray.ui.main

import com.miku.ray.dto.RealPingProgress
import com.miku.ray.dto.RealPingResult
import com.miku.ray.dto.RealPingSummary
import com.miku.ray.dto.TestProgressInfo

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateRestart : MainServiceEvent()
    data class StateStartSuccess(val restarted: Boolean) : MainServiceEvent()
    data class StateStartFailure(val message: String?) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()

    data class MeasureDelayResult(val text: String, val requestId: String) : MainServiceEvent()
    data class MeasureDelayCancelled(val requestId: String) : MainServiceEvent()
    data class MeasureIpResult(val ip: String?, val requestId: String = "") : MainServiceEvent()

    data class MeasureConfigResult(val result: RealPingResult?, val rawGuid: String?) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: RealPingProgress?) : MainServiceEvent()
    data class MeasureConfigFinish(val summary: RealPingSummary?, val requestId: String = "") : MainServiceEvent()

    data class CountryCodeSuccess(val guid: String, val requestId: String = "") : MainServiceEvent()
    data class CountryCodeNotify(val info: TestProgressInfo?, val requestId: String = "") : MainServiceEvent()
    data class CountryCodeFinish(val requestId: String = "") : MainServiceEvent()

    data class TrafficUpdated(val guid: String) : MainServiceEvent()
    data class TrafficSpeedUpdated(val speedText: String) : MainServiceEvent()

    data object SubUpdateFinish : MainServiceEvent()
}
