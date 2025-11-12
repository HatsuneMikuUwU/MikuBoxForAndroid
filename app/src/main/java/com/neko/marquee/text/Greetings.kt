package com.neko.marquee.text

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.*
import kotlin.math.roundToInt

class Greetings @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var showWeather = false
    private var lastWeatherTime = 0L
    private var lastWeatherText: String? = null
    private var useManualCity = false

    private val weatherInterval = 30 * 60 * 1000L
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L
    ).setMinUpdateIntervalMillis(60_000L).build()

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(
                    Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED
                )
            ) updateGreeting(lastWeatherText ?: "")
        }
    }

    init {
        showWeather = DataStore.showWeatherInfo
        useManualCity = DataStore.manualWeatherEnabled
        updateGreeting()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        showWeather = DataStore.showWeatherInfo
        useManualCity = DataStore.manualWeatherEnabled

        if (showWeather) fetchWeather()
        scheduleWeatherRefresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeReceiver)
        handler.removeCallbacksAndMessages(null)
        coroutineScope.cancel()
    }

    override fun isFocused(): Boolean = true

    private fun scheduleWeatherRefresh() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (showWeather) fetchWeather()
                handler.postDelayed(this, weatherInterval)
            }
        }, weatherInterval)
    }

    private fun updateGreeting(weatherText: String = "") {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        @StringRes val greetRes = when (hour) {
            in 5..10 -> R.string.uwu_greeting_morning
            in 11..14 -> R.string.uwu_greeting_afternoon
            in 15..18 -> R.string.uwu_greeting_evening
            in 19..23 -> R.string.uwu_greeting_night
            else -> R.string.uwu_greeting_late_night
        }

        val greeting = context.getString(greetRes)
        text = if (weatherText.isNotEmpty() && showWeather) "$greeting $weatherText" else greeting
    }

    private fun fetchWeather() {
        if (useManualCity) {
            val city = DataStore.manualWeatherCity.ifEmpty { "Tokyo" }
            fetchWeatherByCity(city)
        } else {
            fetchWeatherByGPS()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchWeatherByGPS() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fetchWeatherByCity("Tokyo")
            return
        }

        fusedClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedClient.removeLocationUpdates(this)
                val loc = result.lastLocation
                if (loc != null) fetchWeatherByCoords(loc.latitude, loc.longitude)
                else fetchWeatherByCity("Tokyo")
            }
        }, Looper.getMainLooper())
    }

    private fun fetchWeatherByCoords(lat: Double, lon: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                if ((now - lastWeatherTime) < weatherInterval && lastWeatherText != null) {
                    withContext(Dispatchers.Main) { updateGreeting(lastWeatherText!!) }
                    return@launch
                }

                val response = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                ).readText()
                val current = JSONObject(response).getJSONObject("current_weather")
                val temp = current.getDouble("temperature").roundToInt()
                val code = current.getInt("weathercode")

                val condition = getLocalizedCondition(code)
                val emoji = getWeatherEmoji(code)
                val prefix = context.getString(R.string.weather_today)
                val weatherText = "$prefix $condition $emoji , $temp°C"

                lastWeatherText = weatherText
                lastWeatherTime = now
                withContext(Dispatchers.Main) { updateGreeting(weatherText) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchWeatherByCity(city: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val geoResponse = URL("https://geocoding-api.open-meteo.com/v1/search?name=$city").readText()
                val geoJson = JSONObject(geoResponse)
                val results = geoJson.optJSONArray("results") ?: return@launch
                if (results.length() == 0) return@launch

                val first = results.getJSONObject(0)
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")

                fetchWeatherByCoords(lat, lon)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getWeatherEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2, 3 -> "🌤️"
        45, 48 -> "🌫️"
        in 51..55 -> "🌦️"
        in 61..65 -> "🌧️"
        in 71..75 -> "❄️"
        in 80..82 -> "🌧️"
        in 95..99 -> "⛈️"
        else -> "☁️"
    }

    private fun getLocalizedCondition(code: Int): String {
        val resId = when (code) {
            0 -> R.string.weather_clear
            1, 2, 3 -> R.string.weather_partly_cloudy
            45, 48 -> R.string.weather_fog
            51, 53, 55 -> R.string.weather_drizzle
            61, 63, 65 -> R.string.weather_rain
            71, 73, 75 -> R.string.weather_snow
            80, 81, 82 -> R.string.weather_showers
            95, 96, 99 -> R.string.weather_thunderstorm
            else -> R.string.weather_cloudy
        }
        return context.getString(resId)
    }
}