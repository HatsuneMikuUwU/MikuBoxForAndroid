package com.miku.ray.handler

import android.app.Service
import com.miku.ray.AppConfig
import com.miku.ray.util.SearchBarChipMode
import com.miku.ray.core.CoreServiceManager
import com.miku.ray.core.PolicyGroupTrafficRegistry
import com.miku.ray.extension.delay
import com.miku.ray.extension.toSpeedString
import com.miku.ray.util.LogUtil
import com.miku.ray.util.MessageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object TrafficController {

    private const val QUERY_INTERVAL_MS = 3000L

    interface Listener {
        fun onTraffic(
            proxyUplink: Long,
            proxyDownlink: Long,
            directUplink: Long,
            directDownlink: Long,
            intervalMs: Long,
        )
    }

    @Volatile private var listener: Listener? = null
    @Volatile private var lastTickTime: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start() {
        if (job?.isActive == true) return
        job = null
        lastTickTime = System.currentTimeMillis()
        job = scope.launch {
            while (isActive) {
                tick()
                delay(QUERY_INTERVAL_MS)
            }
        }
        LogUtil.i(AppConfig.TAG, "TrafficController: started")
    }

    fun stop() {
        job?.cancel()
        job = null
        listener = null
        lastTickTime = 0L
        LogUtil.i(AppConfig.TAG, "TrafficController: stopped")
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val intervalMs = if (lastTickTime == 0L) QUERY_INTERVAL_MS else (now - lastTickTime)
        lastTickTime = now

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L
        val memberTraffic = mutableMapOf<String, LongArray>()

        runCatching {
            CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                when {
                    stat.tag == AppConfig.TAG_DIRECT -> {
                        when (stat.direction) {
                            AppConfig.UPLINK -> directUplink += stat.value
                            AppConfig.DOWNLINK -> directDownlink += stat.value
                        }
                    }

                    stat.tag != AppConfig.TAG_BLOCKED -> {
                        when (stat.direction) {
                            AppConfig.UPLINK -> proxyUplink += stat.value
                            AppConfig.DOWNLINK -> proxyDownlink += stat.value
                        }
                        PolicyGroupTrafficRegistry.guidForTag(stat.tag)?.let { memberGuid ->
                            val entry = memberTraffic.getOrPut(memberGuid) { LongArray(2) }
                            when (stat.direction) {
                                AppConfig.UPLINK -> entry[0] += stat.value
                                AppConfig.DOWNLINK -> entry[1] += stat.value
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "TrafficController: queryAllOutboundTrafficStats failed", e)
            return
        }

        runCatching {
            listener?.onTraffic(proxyUplink, proxyDownlink, directUplink, directDownlink, intervalMs)
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "TrafficController: listener failed", e)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_REALTIME_TRAFFIC_IP, false) == true) {
            val sinceLastQueryInSeconds = intervalMs / 1000.0
            val upSpeed = ((proxyUplink + directUplink) / sinceLastQueryInSeconds).toLong()
            val downSpeed = ((proxyDownlink + directDownlink) / sinceLastQueryInSeconds).toLong()
            val speedText = "↑ ${upSpeed.toSpeedString()}  ↓ ${downSpeed.toSpeedString()}"
            getService()?.let { svc ->
                MessageUtil.sendMsg2UI(svc, AppConfig.MSG_TRAFFIC_SPEED_UPDATED, speedText)
            }
        }

        if (proxyUplink + proxyDownlink <= 0L) return

        if (SearchBarChipMode.current() in setOf(
                SearchBarChipMode.TOTAL_TRAFFIC,
                SearchBarChipMode.DUAL_SWIPE
        )) {
            MmkvManager.addDailyTraffic(proxyUplink, proxyDownlink)
            MmkvManager.addTotalTrafficAllTime(proxyUplink, proxyDownlink)
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) != true) return
        val guid = MmkvManager.getSelectServer() ?: return
        MmkvManager.addProfileTraffic(guid, proxyUplink, proxyDownlink)

        memberTraffic.forEach { (memberGuid, bytes) ->
            MmkvManager.addProfileTraffic(memberGuid, bytes[0], bytes[1])
        }

        getService()?.let { svc ->
            MessageUtil.sendMsg2UI(svc, AppConfig.MSG_TRAFFIC_UPDATED, guid)
        }
    }

    private fun getService(): Service? =
    CoreServiceManager.serviceControl?.getService()
}
