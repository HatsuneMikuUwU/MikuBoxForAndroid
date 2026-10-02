package com.miku.ray.util

import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.LOOPBACK
import com.miku.ray.BuildConfig
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.dto.UrlContentResponse
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

object HttpUtil {

    fun toIdnUrl(str: String): String {
        val url = URL(str)
        val host = url.host
        val asciiHost = IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        if (host != asciiHost) {
            return str.replace(host, asciiHost)
        } else {
            return str
        }
    }

    fun toIdnDomain(domain: String): String {
        if (Utils.isPureIpAddress(domain)) {
            return domain
        }

        if (domain.all { it.code < 128 }) {
            return domain
        }

        return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED)
    }

    fun resolveHostToIP(host: String, ipv6Preferred: Boolean = false): List<String>? {
        try {
            if (Utils.isPureIpAddress(host)) {
                return null
            }

            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) {
                return null
            }

            val sortedAddresses = if (ipv6Preferred) {
                addresses.sortedWith(compareByDescending { it is Inet6Address })
            } else {
                addresses.sortedWith(compareBy { it is Inet6Address })
            }

            val ipList = sortedAddresses.mapNotNull { it.hostAddress }

            LogUtil.i(AppConfig.TAG, "Resolved IPs for $host: ${ipList.joinToString()}")

            return ipList
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve host to IP", e)
            return null
        }
    }

    fun getUrlContent(request: UrlContentRequest): String? {
        val url = request.url ?: return null
        val client = buildOkHttpClient(request.timeout, request.httpPort, request.proxyUsername, request.proxyPassword, followRedirects = true)
        val requestBuilder = Request.Builder()
        .url(url)
        .get()
        .header("Connection", "close")
        if (request.httpPort != 0 && !request.proxyUsername.isNullOrBlank() && !request.proxyPassword.isNullOrBlank()) {
            requestBuilder.header("Proxy-Authorization", Credentials.basic(request.proxyUsername, request.proxyPassword))
        }
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "Failed to get URL content, code=${response.code}")
                    return null
                }
                return response.body?.string()
            }
        } catch (e: Exception) {
            // A probe that cannot reach its endpoint is the expected outcome on
            // a blocked network; naming the endpoint and the reason keeps the
            // export readable, where a stack trace per attempt read like a
            // crash and buried whatever the round was actually doing.
            if (request.quiet) {
                LogUtil.w(
                    message = "Failed to get URL content: ${url} (${e.javaClass.simpleName}: ${e.message})",
                )
            } else {
                LogUtil.e(AppConfig.TAG, "Failed to get URL content", e)
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun getUrlContentWithUserAgent(request: UrlContentRequest): String {
        return getUrlContentResponseWithUserAgent(request).content
    }

    @Throws(IOException::class)
    fun getUrlContentResponseWithUserAgent(request: UrlContentRequest): UrlContentResponse {
        var currentUrl = request.url
        var redirects = 0
        val maxRedirects = 3

        while (redirects++ < maxRedirects) {
            if (currentUrl == null) continue
            val client = buildOkHttpClient(request.timeout, request.httpPort, request.proxyUsername, request.proxyPassword, followRedirects = false)
            val finalUserAgent = if (request.userAgent.isNullOrBlank()) {
                "v2rayNG/${BuildConfig.VERSION_NAME}"
            } else {
                request.userAgent
            }
            val requestBuilder = Request.Builder()
            .url(currentUrl)
            .get()
            .header("User-agent", finalUserAgent)
            .header("Connection", "close")

            if (!request.hwid.isNullOrBlank()) {
                requestBuilder.header("X-HWID", request.hwid)
            }

            applyEmbeddedBasicAuthHeader(currentUrl, requestBuilder)

            val headersMap = JsonUtil.parseHeadersToMap(request.requestHeaders)
            for ((key, value) in headersMap) {
                LogUtil.d(AppConfig.TAG, "Adding custom header: $key = $value")
                try {
                    requestBuilder.header(key, value)
                } catch (_: IllegalArgumentException) {
                }
            }

            if (request.httpPort != 0 && !request.proxyUsername.isNullOrBlank() && !request.proxyPassword.isNullOrBlank()) {
                requestBuilder.header("Proxy-Authorization", Credentials.basic(request.proxyUsername, request.proxyPassword))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.isRedirect -> {
                        val location = response.header("Location")
                        if (location.isNullOrEmpty()) {
                            throw IOException("Redirect location not found")
                        }
                        currentUrl = resolveLocation(currentUrl, location)
                        if (currentUrl.isNullOrEmpty()) {
                            throw IOException("Failed to resolve redirect location")
                        }
                        continue
                    }

                    response.isSuccessful -> {
                        return UrlContentResponse(
                            // okhttp 4 leaves the body nullable; an empty document
                            // is the honest answer when there is none.
                            content = response.body?.string().orEmpty(),
                            headers = response.headers.names().associateWith { name ->
                                response.header(name).orEmpty()
                            },
                        )
                    }

                    else -> {
                        throw IOException("Request failed with status code ${response.code}")
                    }
                }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun applyEmbeddedBasicAuthHeader(rawUrl: String, requestBuilder: Request.Builder) {
        val parsed = runCatching { URL(rawUrl) }.getOrNull() ?: return
        parsed.userInfo?.let { userInfo ->
            val colon = userInfo.indexOf(':')
            val user = runCatching {
                Utils.decodeURIComponent(if (colon >= 0) userInfo.substring(0, colon) else userInfo)
            }.getOrDefault(if (colon >= 0) userInfo.substring(0, colon) else userInfo)
            val pass = runCatching {
                Utils.decodeURIComponent(if (colon >= 0) userInfo.substring(colon + 1) else "")
            }.getOrDefault(if (colon >= 0) userInfo.substring(colon + 1) else "")
            requestBuilder.header("Authorization", Credentials.basic(user, pass))
        }
    }

    // One shared client: every call site here used to build its own
    // OkHttpClient, which means a fresh connection pool, dispatcher threads and
    // idle keep-alive machinery per request — the exit-IP probes race three
    // endpoints at a time and speed tests fire in bulk. Deriving per-request
    // clients with newBuilder() keeps the pooling and just overrides what the
    // request asks for.
    private val baseClient = OkHttpClient()

    private fun buildOkHttpClient(
        timeout: Int,
        httpPort: Int,
        proxyUsername: String?,
        proxyPassword: String?,
        followRedirects: Boolean
    ): OkHttpClient {
        val builder = baseClient.newBuilder()
        .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)

        if (httpPort != 0) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LOOPBACK, httpPort)))
            if (!proxyUsername.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                builder.proxyAuthenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(proxyUsername, proxyPassword))
                        .build()
                    }
                }
            }
        }

        return builder.build()
    }

    private fun resolveLocation(baseUrl: String, raw: String): String? {
        return try {
            val locUri = URI(raw)
            val baseUri = URI(baseUrl)
            val resolved = if (locUri.isAbsolute) locUri else baseUri.resolve(locUri)
            resolved.toURL().toString()
        } catch (_: Exception) {
            try {
                URL(raw).toString()
            } catch (_: MalformedURLException) {
                try {
                    URL(URL(baseUrl), raw).toString()
                } catch (_: MalformedURLException) {
                    null
                }
            }
        }
    }

    fun downloadToFile(
        request: UrlContentRequest,
        targetFile: File
    ): Boolean {
        val url = request.url ?: return false
        val client = buildOkHttpClient(request.timeout, request.httpPort, request.proxyUsername, request.proxyPassword, followRedirects = true)
        val requestBuilder = Request.Builder()
        .url(url)
        .get()
        .header("Connection", "close")
        if (request.httpPort != 0 && !request.proxyUsername.isNullOrBlank() && !request.proxyPassword.isNullOrBlank()) {
            requestBuilder.header("Proxy-Authorization", Credentials.basic(request.proxyUsername, request.proxyPassword))
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "Failed to download file, code=${response.code}, url=$url")
                    return false
                }
                val body = response.body ?: return false
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to download file: $url", e)
            false
        }
    }
}
