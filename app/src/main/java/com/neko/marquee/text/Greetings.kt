package com.neko.marquee.text

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.*
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
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
    
    private val prefs by lazy { context.getSharedPreferences("neko_weather_cache", Context.MODE_PRIVATE) }

    private var showWeather = false
    private var useManualCity = false
    
    private var lastWeatherTime = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var cachedTemp = 0
    private var cachedCode = -1
    private var cachedLocName = ""

    private val weatherInterval = 30 * 60 * 1000L
    private val minDistanceChange = 2000f

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(
                    Intent.ACTION_TIME_TICK, 
                    Intent.ACTION_TIME_CHANGED, 
                    Intent.ACTION_TIMEZONE_CHANGED
                )
            ) {
                refreshGreetingText()
            }
        }
    }

    init {
        showWeather = DataStore.showWeatherInfo
        useManualCity = DataStore.manualWeatherEnabled
        
        loadCache()
        refreshGreetingText()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)

        checkSettingsAndFetch()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (e: Exception) {
        }
        handler.removeCallbacksAndMessages(null)
        coroutineScope.cancel()
    }

    override fun isFocused(): Boolean = true

    private fun getAppLocale(): Locale {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0] 
            ?: Locale.getDefault()
    }

    private fun loadCache() {
        lastWeatherTime = prefs.getLong("cached_time", 0L)
        lastLat = prefs.getFloat("cached_lat", 0f).toDouble()
        lastLon = prefs.getFloat("cached_lon", 0f).toDouble()
        cachedTemp = prefs.getInt("cached_temp", 0)
        cachedCode = prefs.getInt("cached_code", -1)
        cachedLocName = prefs.getString("cached_loc_name", "") ?: ""
    }

    private fun saveCache(time: Long, lat: Double, lon: Double, temp: Int, code: Int, locName: String) {
        lastWeatherTime = time
        lastLat = lat
        lastLon = lon
        cachedTemp = temp
        cachedCode = code
        cachedLocName = locName
        
        prefs.edit().apply {
            putLong("cached_time", time)
            putFloat("cached_lat", lat.toFloat())
            putFloat("cached_lon", lon.toFloat())
            putInt("cached_temp", temp)
            putInt("cached_code", code)
            putString("cached_loc_name", locName)
            apply()
        }
    }

    private fun refreshGreetingText() {
        val weatherString = if (cachedCode != -1 && showWeather) {
            val condition = getLocalizedCondition(cachedCode)
            val emoji = getWeatherEmoji(cachedCode)
            val prefix = context.getString(R.string.weather_today)
            val locSuffix = if (cachedLocName.isNotEmpty()) " ($cachedLocName)" else ""
            
            "$prefix $condition $emoji , $cachedTemp°C$locSuffix"
        } else {
            ""
        }
        
        updateTextView(weatherString)
    }

    private fun updateTextView(weatherText: String) {
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

    private fun checkSettingsAndFetch() {
        showWeather = DataStore.showWeatherInfo
        useManualCity = DataStore.manualWeatherEnabled
        
        if (showWeather) fetchWeather()
        
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ checkSettingsAndFetch() }, weatherInterval)
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
            fetchWeatherByCity(DataStore.manualWeatherCity.ifEmpty { "Tokyo" })
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    fetchWeatherByCoords(location.latitude, location.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            fetchWeatherByCoords(lastLoc.latitude, lastLoc.longitude)
                        } else {
                            fetchWeatherByCity("Tokyo")
                        }
                    }
                }
            }
            .addOnFailureListener {
                fetchWeatherByCity("Tokyo")
            }
    }

    private fun fetchWeatherByCoords(lat: Double, lon: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val dist = FloatArray(1)
                Location.distanceBetween(lastLat, lastLon, lat, lon, dist)
                val distanceChanged = dist[0] > minDistanceChange

                if ((now - lastWeatherTime) < weatherInterval && !distanceChanged && cachedCode != -1) {
                    withContext(Dispatchers.Main) { refreshGreetingText() }
                    return@launch
                }

                var locationName = ""
                try {
                    val geocoder = Geocoder(context, getAppLocale())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                locationName = addresses[0].subLocality ?: addresses[0].locality ?: ""
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            locationName = addresses[0].subLocality ?: addresses[0].locality ?: ""
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val response = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"
                ).readText()
                
                val current = JSONObject(response).getJSONObject("current_weather")
                val temp = current.getDouble("temperature").roundToInt()
                val code = current.getInt("weathercode")

                withContext(Dispatchers.Main) { 
                    saveCache(now, lat, lon, temp, code, locationName)
                    refreshGreetingText() 
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (cachedCode != -1) {
                    withContext(Dispatchers.Main) { refreshGreetingText() }
                }
            }
        }
    }

    private fun fetchWeatherByCity(city: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val geoResponse = URL("https://geocoding-api.open-meteo.com/v1/search?name=$city&count=1").readText()
                val geoJson = JSONObject(geoResponse)
                val results = geoJson.optJSONArray("results") ?: return@launch
                
                if (results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    fetchWeatherByCoords(lat, lon)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        in 71..75 -> "❄️"
        77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "❄️"
        95 -> "⛈️"
        96, 99 -> "⛈️"
        else -> "qm"
    }

    private fun getLocalizedCondition(code: Int): String {
        val resId = when (code) {
            0 -> R.string.weather_clear
            1 -> R.string.weather_clear
            2 -> R.string.weather_partly_cloudy
            3 -> R.string.weather_cloudy 
            45, 48 -> R.string.weather_fog
            51, 53, 55 -> R.string.weather_drizzle
            56, 57 -> R.string.weather_drizzle
            61, 63, 65 -> R.string.weather_rain
            66, 67 -> R.string.weather_rain
            71, 73, 75, 77 -> R.string.weather_snow
            80, 81, 82 -> R.string.weather_showers
            85, 86 -> R.string.weather_snow
            95, 96, 99 -> R.string.weather_thunderstorm
            else -> R.string.weather_cloudy
        }
        return context.getString(resId)
    }
}
