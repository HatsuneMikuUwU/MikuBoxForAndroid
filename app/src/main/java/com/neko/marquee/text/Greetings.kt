package com.neko.marquee.text

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.AttributeSet
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.*
import kotlin.math.roundToInt

class Greetings @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    
    private var weatherJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val prefs by lazy { context.getSharedPreferences("neko_weather_cache", Context.MODE_PRIVATE) }

    private var showWeather = false
    private var useManualCity = false
    private var showCondition = true
    
    private val weatherInterval = 60 * 60 * 1000L

    private var cachedTemp: Int = -999
    private var cachedCode: Int = -1
    private var cachedCity: String = ""
    private var lastWeatherTime: Long = 0L

    private val KEY_TEMP = "w_temp"
    private val KEY_CODE = "w_code"
    private val KEY_CITY = "w_city"
    private val KEY_TIME = "w_time"
    private val KEY_IS_MANUAL = "w_is_manual"
    private val KEY_MANUAL_NAME = "w_manual_name"

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L
    ).setMinUpdateIntervalMillis(60_000L).build()

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(
                    Intent.ACTION_TIME_TICK, 
                    Intent.ACTION_TIME_CHANGED, 
                    Intent.ACTION_TIMEZONE_CHANGED
                )
            ) {
                updateDisplay()
            }
        }
    }

    init {
        ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSingleLine = true
        isSelected = true
        isFocusable = true
        isFocusableInTouchMode = true
        freezesText = true

        cachedTemp = prefs.getInt(KEY_TEMP, -999)
        cachedCode = prefs.getInt(KEY_CODE, -1)
        cachedCity = prefs.getString(KEY_CITY, "") ?: ""
        lastWeatherTime = prefs.getLong(KEY_TIME, 0L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSelected = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)

        refreshSettings()
        updateDisplay()
        startWeatherLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (e: Exception) {
        }
        weatherJob?.cancel()
        scope.coroutineContext.cancelChildren()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) isSelected = true
    }

    private fun refreshSettings() {
        showWeather = DataStore.showWeatherInfo
        useManualCity = DataStore.manualWeatherEnabled
        showCondition = DataStore.showWeatherCondition 
    }

    private fun startWeatherLoop() {
        weatherJob?.cancel()
        weatherJob = scope.launch {
            while (isActive) {
                if (showWeather) {
                    refreshSettings()
                    fetchWeather()
                }
                delay(weatherInterval)
            }
        }
    }

    private fun updateDisplay() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        @StringRes val greetRes = when (hour) {
            in 5..10 -> R.string.uwu_greeting_morning
            in 11..14 -> R.string.uwu_greeting_afternoon
            in 15..18 -> R.string.uwu_greeting_evening
            in 19..23 -> R.string.uwu_greeting_night
            else -> R.string.uwu_greeting_late_night
        }
        
        val greeting = context.getString(greetRes)
        val sb = StringBuilder(greeting)

        if (showWeather && cachedCode != -1 && cachedTemp != -999) {
            val emoji = getWeatherEmoji(cachedCode)
            val locationSuffix = if (cachedCity.isNotEmpty()) " - $cachedCity" else ""
            
            sb.append("  ")

            if (showCondition) {
                val condition = getLocalizedCondition(cachedCode)
                val prefix = context.getString(R.string.weather_today)
                sb.append("$prefix $condition $emoji")
            } else {
                sb.append(emoji)
            }
            sb.append(" $cachedTemp°C$locationSuffix")
        }

        text = sb.toString()
        
        isSelected = false
        isSelected = true 
    }

    private suspend fun fetchWeather() {
        val now = System.currentTimeMillis()
        val storedIsManual = prefs.getBoolean(KEY_IS_MANUAL, false)
        val storedManualCity = prefs.getString(KEY_MANUAL_NAME, "") ?: ""
        val currentManualCity = DataStore.manualWeatherCity

        val isTimeValid = (now - lastWeatherTime) < weatherInterval
        val isModeValid = (storedIsManual == useManualCity)
        val isCityValid = if (useManualCity) (storedManualCity == currentManualCity) else true

        if (isTimeValid && isModeValid && isCityValid && cachedCode != -1) {
            withContext(Dispatchers.Main) { updateDisplay() }
            return
        }

        if (useManualCity) {
            val city = currentManualCity.ifEmpty { "Tokyo" }
            fetchWeatherByCity(city)
        } else {
            fetchWeatherByGPS()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchWeatherByGPS() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fetchWeatherByCity("Tokyo")
            return
        }
        
        withContext(Dispatchers.Main) {
             fusedClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    scope.launch(Dispatchers.IO) {
                        if (loc != null) fetchWeatherByCoords(loc.latitude, loc.longitude, null)
                        else fetchWeatherByCity("Tokyo")
                    }
                }
            }, android.os.Looper.getMainLooper())
        }
    }

    private suspend fun fetchWeatherByCity(city: String) {
        withContext(Dispatchers.IO) {
            try {
                val encodedCity = URLEncoder.encode(city, "UTF-8")
                val geoResponse = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity").readText()
                val geoJson = JSONObject(geoResponse)
                val results = geoJson.optJSONArray("results")
                
                if (results == null || results.length() == 0) return@withContext

                val first = results.getJSONObject(0)
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")
                val officialName = first.optString("name", city)

                fetchWeatherByCoords(lat, lon, officialName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchWeatherByCoords(lat: Double, lon: Double, cityName: String?) {
        try {
            var resolvedCity = cityName
            
            if (resolvedCity == null) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        resolvedCity = addresses[0].locality 
                            ?: addresses[0].subAdminArea 
                            ?: addresses[0].adminArea
                    }
                } catch (e: Exception) {         
                    e.printStackTrace() 
                }
            }

            val response = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            ).readText()
            
            val current = JSONObject(response).getJSONObject("current_weather")
            
            cachedTemp = current.getDouble("temperature").roundToInt()
            cachedCode = current.getInt("weathercode")
            cachedCity = resolvedCity ?: ""
            lastWeatherTime = System.currentTimeMillis()

            prefs.edit()
                .putInt(KEY_TEMP, cachedTemp)
                .putInt(KEY_CODE, cachedCode)
                .putString(KEY_CITY, cachedCity)
                .putLong(KEY_TIME, lastWeatherTime)
                .putBoolean(KEY_IS_MANUAL, useManualCity)
                .putString(KEY_MANUAL_NAME, if(useManualCity) DataStore.manualWeatherCity else "")
                .apply()

            withContext(Dispatchers.Main) { updateDisplay() }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getWeatherEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..55 -> "🌧️"
        56, 57 -> "❄️"
        in 61..65 -> "🌧️"
        66, 67 -> "❄️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    private fun getLocalizedCondition(code: Int): String {
        val resId = when (code) {
            0, 1 -> R.string.weather_clear
            2 -> R.string.weather_partly_cloudy
            3 -> R.string.weather_cloudy 
            45, 48 -> R.string.weather_fog
            51, 53, 55, 56, 57 -> R.string.weather_drizzle
            61, 63, 65, 66, 67 -> R.string.weather_rain
            71, 73, 75, 77, 85, 86 -> R.string.weather_snow
            80, 81, 82 -> R.string.weather_showers
            95, 96, 99 -> R.string.weather_thunderstorm
            else -> R.string.weather_cloudy
        }
        return context.getString(resId)
    }
}
