package com.miku.ray.service

import android.content.Context
import android.os.SystemClock
import com.miku.ray.core.CoreConfigContextBuilder
import com.miku.ray.core.CoreConfigManager
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.dto.RealPingEvent
import com.miku.ray.enums.BalancerStrategyType
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.extension.isNotNullEmpty
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {

        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

private class RealPingProgressState(private val total: Int) {
    private var completed = 0
    private var lastProgressAt = SystemClock.elapsedRealtime()

    fun initial(): RealPingEvent.Progress = RealPingEvent.Progress(completed = 0, total = total)

    @Synchronized
    fun record(): RealPingEvent.Progress? {
        if (completed >= total) return null
        completed++

        val now = SystemClock.elapsedRealtime()
        if (completed < total && now - lastProgressAt < PROGRESS_UPDATE_INTERVAL_MS) return null
        lastProgressAt = now
        return RealPingEvent.Progress(completed = completed, total = total)
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
}

private data class RealPingProbeSource(
    val guid: String,
    val memberGuids: List<String>,
    val strategy: BalancerStrategyType?,
) {
    fun aggregate(delays: Map<String, Long>): Long {
        val liveDelays = memberGuids.mapNotNull { memberGuid ->
            delays[memberGuid]?.takeIf { it >= 0L }
        }
        if (liveDelays.isEmpty()) return -1L
        return when (strategy) {
            BalancerStrategyType.LEAST_PING -> liveDelays.min()
            BalancerStrategyType.LEAST_LOAD,
            BalancerStrategyType.RANDOM,
            BalancerStrategyType.ROUND_ROBIN,
            null -> liveDelays.average().roundToLong()
        }
    }
}

private data class RealPingProbePlan(
    val sources: List<RealPingProbeSource>,
    val probeGuids: List<String>,
    val sourcesByMemberGuid: Map<String, List<RealPingProbeSource>>,
) {
    companion object {
        fun build(guids: List<String>): RealPingProbePlan {
            val sources = guids.map { guid ->
                val profile = MmkvManager.decodeServerConfig(guid)
                if (profile?.configType == EConfigType.POLICYGROUP) {
                    RealPingProbeSource(
                        guid = guid,
                        memberGuids = CoreConfigContextBuilder.resolvePolicyGroupGuids(profile),
                        strategy = BalancerStrategyType.from(profile.policyGroupType),
                    )
                } else {
                    RealPingProbeSource(guid, listOf(guid), strategy = null)
                }
            }
            val sourcesByMemberGuid = mutableMapOf<String, MutableList<RealPingProbeSource>>()
            sources.forEach { source ->
                source.memberGuids.forEach { memberGuid ->
                    sourcesByMemberGuid.getOrPut(memberGuid, ::mutableListOf).add(source)
                }
            }
            return RealPingProbePlan(
                sources = sources,
                probeGuids = sources.flatMap { it.memberGuids }.distinct(),
                sourcesByMemberGuid = sourcesByMemberGuid,
            )
        }
    }
}

class RealPingWorkerService(
    private val context: Context,
    guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val guids = guids.distinct()
    private val requestedGuids = this.guids.toHashSet()
    private val job = SupervisorJob()
    private val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))
    private val finished = AtomicBoolean(false)
    private val resultLock = Any()
    private val probeDelays = mutableMapOf<String, Long>()
    private val sourceDelays = mutableMapOf<String, Long>()

    fun start() {
        scope.launch {
            val plan = RealPingProbePlan.build(guids)
            val progress = RealPingProgressState(plan.probeGuids.size)
            onEvent(progress.initial())
            emitCompletedSources(plan.sources.filter { it.memberGuids.isEmpty() })

            val jobs = plan.probeGuids.map { guid ->
                scope.launch {
                    val delayMillis = safelyProbe(guid)
                    currentCoroutineContext().ensureActive()
                    synchronized(resultLock) { probeDelays[guid] = delayMillis }
                    emitPolicyGroupMemberResult(guid, delayMillis)
                    emitCompletedSources(plan.sourcesByMemberGuid[guid].orEmpty())
                    progress.record()?.let(onEvent)
                }
            }
            jobs.joinAll()
            if (finished.compareAndSet(false, true)) {
                onEvent(summary())
            }
        }
    }

    fun cancel(): RealPingEvent.Finish {
        finished.set(true)
        job.cancel()
        return summary()
    }

    private fun emitPolicyGroupMemberResult(guid: String, delayMillis: Long) {
        if (guid in requestedGuids) return
        if (!finished.get()) onEvent(RealPingEvent.Result(guid, delayMillis))
    }

    private fun emitCompletedSources(sources: List<RealPingProbeSource>) {
        val results = synchronized(resultLock) {
            sources.mapNotNull { source ->
                if (source.guid in sourceDelays ||
                    source.memberGuids.any { it !in probeDelays }
                ) {
                    return@mapNotNull null
                }
                val delayMillis = source.aggregate(probeDelays)
                sourceDelays[source.guid] = delayMillis
                RealPingEvent.Result(source.guid, delayMillis)
            }
        }
        if (!finished.get()) results.forEach(onEvent)
    }

    private fun summary(): RealPingEvent.Finish = synchronized(resultLock) {
        RealPingEvent.Finish(
            live = sourceDelays.values.count { it >= 0L },
            completed = sourceDelays.size,
            total = guids.size,
        )
    }

    private suspend fun safelyProbe(guid: String): Long = try {
        if (onlyTcp) startTcping(guid) else startRealPing(guid)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        -1L
    }

    private suspend fun startRealPing(guid: String): Long {
        com.miku.ray.MikuProfiles.impl?.let { profiles ->
            val raw = profiles.get(guid)?.config ?: return -1L
            return com.miku.ray.MikuDiagnostics.impl?.delay(raw, SettingsManager.getDelayTestUrl()) ?: -1L
        }
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val tcpTime = SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
            if (tcpTime <= -1L) return -1L
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) return -1L
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(
                configResult.content,
                SettingsManager.getDelayTestUrl(),
            )
        }
    }

    private fun startTcping(guid: String): Long {
        com.miku.ray.MikuProfiles.impl?.let { profiles ->
            val raw = profiles.get(guid)?.config ?: return -1L
            return com.miku.ray.MikuDiagnostics.impl?.tcp(raw) ?: -1L
        }
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            return SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
        }
        return -1L
    }
}
