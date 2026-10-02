package com.miku.ray.handler

import com.miku.ray.AppConfig
import com.miku.ray.dto.IPAPIInfo
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.JsonUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

object SpeedtestManager {

    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    fun getCountryCodeThroughProxy(httpPort: Int, timeoutMs: Int = 3500): String? {
        if (httpPort <= 0) return null

        val configuredUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
        .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL
        val url = configuredUrl.replace("{ip}", "", ignoreCase = true)
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = timeoutMs,
                httpPort = httpPort
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        return countryCode(ipInfo)
    }

    fun countryCode(info: IPAPIInfo): String? = listOf(
        info.country_code, info.countryCode, info.location?.country_code, info.country,
    ).asSequence().filterNotNull().map { it.trim().uppercase(java.util.Locale.ROOT) }
        .firstOrNull { it.matches(Regex("[A-Z]{2}")) }

    /**
     * The address this device exits with. Through the core's own inbound by
     * default, so what comes back is what a browser behind the tunnel would
     * see; with [direct] the probe goes out on the device's own connection,
     * which is how the home screen keeps the readout alive while the tunnel
     * is down. A temporary API outage must not erase a valid IP: independent
     * endpoints race in parallel so one slow endpoint cannot hold back the
     * answer.
     */
    suspend fun getRemoteIPInfo(direct: Boolean = false): String? =
        if (direct) fetchRemoteIPInfo(true) else ExitIpSnapshot.get { fetchRemoteIPInfo(false) }

    private suspend fun fetchRemoteIPInfo(direct: Boolean): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
        .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername: String?
        val proxyPassword: String?
        val httpPort: Int
        if (direct) {
            proxyUsername = null
            proxyPassword = null
            httpPort = 0
        } else {
            proxyUsername = SettingsManager.getSocksUsername()
            proxyPassword = SettingsManager.getSocksPassword()
            httpPort = SettingsManager.getHttpPort()
            if (httpPort == 0) return null
        }
        val urls = listOf(url.replace("{ip}", "", ignoreCase = true),
            "https://api.ipify.org?format=json", "https://api.ip.sb/geoip").distinct()

        val winner = CompletableDeferred<Pair<IPAPIInfo, String>>()
        val found = coroutineScope {
            val probes = urls.map { endpoint ->
                async(Dispatchers.IO) {
                    val candidate = runCatching {
                        val content = HttpUtil.getUrlContent(UrlContentRequest(
                            url = endpoint, timeout = PROBE_TIMEOUT_MS, httpPort = httpPort,
                            proxyUsername = proxyUsername, proxyPassword = proxyPassword,
                            quiet = true,
                        )) ?: return@runCatching null
                        JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java)
                    }.getOrNull() ?: return@async
                    val candidateIp = listOf(candidate.ip, candidate.clientIp, candidate.ip_addr, candidate.query)
                        .firstOrNull { !it.isNullOrBlank() && Utils.isPureIpAddress(it) }
                        ?: return@async
                    winner.complete(candidate to candidateIp)
                }
            }
            // Once any endpoint answers (or none does within the window), the
            // remaining probes are cancelled and their sockets abandoned. The
            // window has to cover a proxy-chained round trip — loopback inbound,
            // core dial, remote TLS — and a core that is still warming up
            // refuses the first wave of connections, so 3s/3.5s used to report
            // a failure that a retry a second later would not have seen.
            val result = withTimeoutOrNull(PROBE_WINDOW_MS) { winner.await() }
            probes.forEach { it.cancel() }
            if (result == null) LogUtil.w(message = "remote-IP probe window elapsed with no answer (direct=$direct, port=$httpPort)")
            result
        } ?: return null
        val (ipInfo, ip) = found

        val country = countryCode(ipInfo)

        val showIsp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_ISP_INFO, true)
        val isp = if (showIsp) {
            listOf(
                ipInfo.isp,
                ipInfo.organization,
                ipInfo.org,
                ipInfo.asn_organization,
                ipInfo.asOrg,
                ipInfo.asname
            ).firstOrNull { !it.isNullOrBlank() }
        } else {
            null
        }

        val flag = Utils.countryCodeToFlag(country)
        val flagPrefix = if (flag.isNotEmpty()) "$flag " else ""
        val ispSuffix = if (!isp.isNullOrBlank()) " · $isp" else ""
        return if (country.isNullOrBlank()) "$ip$ispSuffix" else "${flagPrefix}($country) $ip$ispSuffix"
    }

    /** Per-endpoint budget; a proxy-chained TLS round trip needs more than a direct one. */
    private const val PROBE_TIMEOUT_MS = 4_500

    /** Whole-race budget: the window [PROBE_TIMEOUT_MS] plus hand-off slack. */
    private const val PROBE_WINDOW_MS = 5_000L
}
