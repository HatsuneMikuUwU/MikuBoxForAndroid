package com.neko.hostnamefinder

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.nkbe.mikubox.ui.component.setMikuContent
import top.nkbe.mikubox.ui.page.tools.HostnameFinderScreen
import top.nkbe.mikubox.ui.page.tools.HostnameFinderUiState
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class HostnameFinder : ThemedActivity() {

    private val sources = listOf(
        "Anubis",
        "YouGetSignal",
        "HackerTarget",
        "Local DNS",
        "VirusTotal",
    )

    private val virusTotalApiKey = "NjhlMDUzYjc5NmI3OWE1YzBiNDczYWJhZDFjNTVkYWY2ZWRlNGU4M2VjMWNkZmMwNTUxYTVhODRkOGEyZjIzMw=="
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    private var uiState by mutableStateOf(
        HostnameFinderUiState(
            selectedSource = sources[0],
            sources = sources,
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMikuContent {
            HostnameFinderScreen(
                state = uiState,
                onHostChange = { host ->
                    uiState = uiState.copy(host = host, inputError = null)
                },
                onSelectSource = { src ->
                    uiState = uiState.copy(selectedSource = src)
                },
                onSearch = { performSearch() },
                onCopyResults = {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("hostnames", uiState.resultText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.hf_copied), Toast.LENGTH_SHORT).show()
                },
                onBack = { finish() },
            )
        }
    }

    private fun performSearch() {
        val host = uiState.host.trim()
        if (host.isEmpty()) {
            uiState = uiState.copy(inputError = getString(R.string.hf_error_input_host))
            return
        }

        uiState = uiState.copy(
            isLoading = true,
            inputError = null,
            resultTitle = getString(R.string.hf_result_loading),
            resultText = getString(R.string.hf_loading),
        )

        when (uiState.selectedSource) {
            "Anubis" -> fetchFromAnubis(host)
            "YouGetSignal" -> fetchFromYouGetSignal(host)
            "HackerTarget" -> fetchFromHackerTarget(host)
            "Local DNS" -> searchWithDns(host)
            "VirusTotal" -> fetchFromVirusTotal(host)
            else -> fetchFromAnubis(host)
        }
    }

    private fun getRequest(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json, text/html, */*")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun fetchFromYouGetSignal(domain: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val conn = URL("https://domains.yougetsignal.com/domains.php").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("User-Agent", userAgent)
                    conn.outputStream.bufferedWriter().use { it.write("remoteAddress=$domain") }
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
                val json = JSONObject(response)
                if (json.has("domainArray")) {
                    val pairs = json.getJSONArray("domainArray")
                    val domains = (0 until pairs.length()).map { i ->
                        pairs.getJSONArray(i).getString(0)
                    }
                    displayResults(domains.sorted(), R.string.hf_result_found_domains)
                } else {
                    displayResults(emptyList(), R.string.hf_result_found_domains)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun fetchFromAnubis(domain: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    getRequest("https://jldc.me/anubis/subdomains/$domain")
                }
                val jsonArray = JSONArray(result)
                val subdomains = (0 until jsonArray.length()).map { i ->
                    jsonArray.getString(i)
                }.distinct()
                displayResults(subdomains.sorted(), R.string.hf_result_found_subdomains)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun fetchFromHackerTarget(domain: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    getRequest("https://api.hackertarget.com/hostsearch/?q=$domain")
                }
                val hostnames = result.lines()
                    .filter { it.contains(",") }
                    .map { line -> line.split(",")[1] }
                    .distinct()
                displayResults(hostnames.sorted(), R.string.hf_result_found_hostnames)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun fetchFromVirusTotal(domain: String) {
        lifecycleScope.launch {
            try {
                val apiKey = String(Base64.decode(virusTotalApiKey, Base64.DEFAULT))
                val result = withContext(Dispatchers.IO) {
                    val conn = URL("https://www.virustotal.com/api/v3/domains/$domain/subdomains?limit=40")
                        .openConnection() as HttpURLConnection
                    conn.setRequestProperty("x-apikey", apiKey)
                    conn.setRequestProperty("User-Agent", userAgent)
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
                val data = JSONObject(result).getJSONArray("data")
                val subdomains = (0 until data.length()).map { i ->
                    data.getJSONObject(i).getString("id")
                }
                displayResults(subdomains.sorted(), R.string.hf_result_found_subdomains)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun searchWithDns(domain: String) {
        lifecycleScope.launch {
            try {
                val addresses = withContext(Dispatchers.IO) {
                    InetAddress.getAllByName(domain).mapNotNull { it.hostAddress }.distinct()
                }
                displayResults(addresses, R.string.hf_result_found_ips)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private fun displayResults(items: List<String>, titleResId: Int) {
        uiState = if (items.isEmpty()) {
            uiState.copy(
                isLoading = false,
                resultTitle = getString(titleResId, 0),
                resultText = "No results found.",
            )
        } else {
            uiState.copy(
                isLoading = false,
                resultTitle = getString(titleResId, items.size),
                resultText = items.joinToString("\n"),
            )
        }
    }

    private fun handleError(e: Exception) {
        e.printStackTrace()
        uiState = uiState.copy(
            isLoading = false,
            resultTitle = getString(R.string.hf_result_error),
            resultText = getString(R.string.hf_error_generic, e.localizedMessage ?: e.toString()),
        )
    }
}
