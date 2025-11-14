package com.neko.hosttoip

import android.os.Bundle
import android.widget.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class HostToIP : ThemedActivity() {

    private lateinit var hostInput: EditText
    private lateinit var resolveButton: Button
    private lateinit var resultText: TextView
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uwu_host_to_ip)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.host_to_ip)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        hostInput = findViewById(R.id.host_input)
        resolveButton = findViewById(R.id.resolve_button)
        resultText = findViewById(R.id.result_text)

        resolveButton.setOnClickListener {
            val host = hostInput.text.toString().trim()
            if (host.isNotEmpty()) {
                resolveHostInfo(host)
            } else {
                Toast.makeText(this, getString(R.string.hip_error_empty), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveHostInfo(host: String) {
        coroutineScope.launch {
            resultText.text = getString(R.string.hip_resolving)
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val ipAddress = InetAddress.getByName(host).hostAddress
                    val info = getIpDetails(ipAddress)

                    getString(
                        R.string.hip_result_format,
                        host,
                        ipAddress,
                        info["country"],
                        info["city"],
                        info["isp"]
                    )
                } catch (e: Exception) {
                    getString(R.string.hip_error_generic, e.localizedMessage)
                }
            }
            resultText.text = result
        }
    }

    private fun getIpDetails(ip: String?): Map<String, String> {
        val unknownText = getString(R.string.hip_unknown)

        if (ip == null) {
             return mapOf(
                "country" to unknownText,
                "city" to unknownText,
                "isp" to unknownText
            )
        }

        try {
            val url = URL("http://ip-api.com/json/$ip")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.connect()

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            return if (json.getString("status") == "success") {
                mapOf(
                    "country" to json.optString("country", unknownText),
                    "city" to json.optString("city", unknownText),
                    "isp" to json.optString("isp", unknownText)
                )
            } else {
                mapOf(
                    "country" to unknownText,
                    "city" to unknownText,
                    "isp" to unknownText
                )
            }
        } catch (e: Exception) {
            return mapOf(
                "country" to unknownText,
                "city" to unknownText,
                "isp" to unknownText
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}
