package com.neko.hostnamefinder

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class HostnameFinder : ThemedActivity() {

    private lateinit var edtHost: EditText
    private lateinit var btnSearch: Button
    private lateinit var txtResults: TextView
    private lateinit var txtResultTitle: TextView
    private lateinit var btnCopy: Button
    private lateinit var spinnerSource: Spinner

    private val sources = listOf("YouGetSignal", "Local DNS", "VirusTotal", "crt.sh")
    private val virusTotalApiKey = "NjhlMDUzYjc5NmI3OWE1YzBiNDczYWJhZDFjNTVkYWY2ZWRlNGU4M2VjMWNkZmMwNTUxYTVhODRkOGEyZjIzMw=="

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uwu_hostname_finder)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.hostname_finder)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        edtHost = findViewById(R.id.edtHost)
        btnSearch = findViewById(R.id.btnSearch)
        txtResults = findViewById(R.id.txtResults)
        txtResultTitle = findViewById(R.id.txtResultTitle)
        btnCopy = findViewById(R.id.btnCopy)
        spinnerSource = findViewById(R.id.spinnerSource)

        spinnerSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sources)

        btnSearch.setOnClickListener { performSearch() }

        edtHost.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("hostnames", txtResults.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.hf_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch() {
        val host = edtHost.text.toString().trim()
        if (host.isEmpty()) {
            edtHost.error = getString(R.string.hf_error_input_host)
            return
        }

        txtResults.text = getString(R.string.hf_loading)
        txtResultTitle.text = getString(R.string.hf_result_loading)

        when (spinnerSource.selectedItem.toString()) {
            "YouGetSignal" -> fetchFromYouGetSignal(host)
            "Local DNS" -> searchWithDns(host)
            "VirusTotal" -> fetchFromVirusTotal(host)
            "crt.sh" -> fetchFromCrtSh(host)
        }
    }

    private fun searchWithDns(domain: String) {
        lifecycleScope.launch {
            try {
                val addresses = withContext(Dispatchers.IO) {
                    InetAddress.getAllByName(domain).map { it.hostAddress }.distinct()
                }
                txtResults.text = addresses.joinToString("\n")
                txtResultTitle.text = getString(R.string.hf_result_found_ips, addresses.size)
            } catch (e: Exception) {
                txtResults.text = getString(R.string.hf_error_generic, e.localizedMessage)
                txtResultTitle.text = getString(R.string.hf_result_error)
            }
        }
    }

    private fun fetchFromCrtSh(domain: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = "https://crt.sh/?q=%25.$domain&output=json"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
                val entries = Regex("\"common_name\":\"(.*?)\"").findAll(result).map { it.groupValues[1] }.toSet()
                txtResults.text = entries.sorted().joinToString("\n")
                txtResultTitle.text = getString(R.string.hf_result_found_hostnames, entries.size)
            } catch (e: Exception) {
                txtResults.text = getString(R.string.hf_error_crt, e.localizedMessage)
                txtResultTitle.text = getString(R.string.hf_result_error)
            }
        }
    }

    private fun fetchFromVirusTotal(domain: String) {
        lifecycleScope.launch {
            try {
                val apiKey = String(Base64.decode(virusTotalApiKey, Base64.DEFAULT))

                val result = withContext(Dispatchers.IO) {
                    val url = "https://www.virustotal.com/api/v3/domains/$domain/subdomains?limit=40"
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.setRequestProperty("x-apikey", apiKey)
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val data = JSONObject(result).getJSONArray("data")
                val subdomains = (0 until data.length()).map { i ->
                    data.getJSONObject(i).getString("id")
                }

                txtResults.text = subdomains.sorted().joinToString("\n")
                txtResultTitle.text = getString(R.string.hf_result_found_subdomains, subdomains.size)
            } catch (e: Exception) {
                txtResults.text = getString(R.string.hf_error_vt, e.localizedMessage)
                txtResultTitle.text = getString(R.string.hf_result_error)
            }
        }
    }

    private fun fetchFromYouGetSignal(domain: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val url = URL("https://domains.yougetsignal.com/domains.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.outputStream.bufferedWriter().use { it.write("remoteAddress=$domain") }
                    conn.inputStream.bufferedReader().use { it.readText() }
                }

                val json = JSONObject(response)
                val pairs = json.getJSONArray("domainArray")
                val domains = (0 until pairs.length()).map { i ->
                    pairs.getJSONArray(i).getString(0)
                }

                txtResults.text = domains.sorted().joinToString("\n")
                txtResultTitle.text = getString(R.string.hf_result_found_domains, domains.size)
            } catch (e: Exception) {
                txtResults.text = getString(R.string.hf_error_ygs, e.localizedMessage)
                txtResultTitle.text = getString(R.string.hf_result_error)
            }
        }
    }
}
