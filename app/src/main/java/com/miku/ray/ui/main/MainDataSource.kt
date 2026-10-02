package com.miku.ray.ui.main

import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import kotlinx.coroutines.flow.SharedFlow

interface MainDataSource : AutoCloseable {
    val mainServiceEvent: SharedFlow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult
    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun sendMsg2Service(msgId: Int, content: String)
    fun requestIp(requestId: String) { sendMsg2Service(com.miku.ray.AppConfig.MSG_MEASURE_IP, "") }
    fun queryRunningState(onResult: (isRunning: Boolean) -> Unit)
    fun sendMsg2TestService(msg: TestServiceMessage, requestId: String? = null)
    fun sendMsg2CountryCodeTestService(msg: CountryCodeTestMessage)
    fun testCurrentServerRealPing(requestId: String)
}
