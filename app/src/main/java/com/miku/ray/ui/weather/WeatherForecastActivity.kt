package com.miku.ray.ui.weather

import com.miku.ray.remixicon.R as RemixR
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.R
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.ui.base.BaseActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DetailItem(
    @androidx.annotation.DrawableRes val iconRes: Int,
    val label: String,
    val value: String
)

data class HourlyForecastItem(
    val timeLabel: String,
    val dayLabel: String,
    val tempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int,
    val isNow: Boolean
)

data class DailyForecastItem(
    val weekdayLabel: String,
    val maxTempCelsius: Int,
    val minTempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int
)

class WeatherForecastActivity : BaseActivity() {
    private var job: Job? = null

    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvMaxMin: TextView
    private lateinit var tvError: TextView
    private lateinit var tvSummary: TextView
    private lateinit var cardCurrent: android.view.View
    private lateinit var cardDetails: android.view.View
    private lateinit var recyclerDetails: RecyclerView
    private lateinit var cardSummary: android.view.View
    private lateinit var cardHourly: android.view.View
    private lateinit var cardDaily: android.view.View
    private lateinit var recyclerHourly: RecyclerView
    private lateinit var recyclerDaily: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_forecast)

        findViewById<androidx.core.widget.NestedScrollView>(R.id.weather_forecast_content).applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.weather_forecast_title), subtitle = getString(R.string.subtitle_weather_forecast))

        ivIcon = findViewById(R.id.ivForecastCurrentIcon)
        tvCondition = findViewById(R.id.tvForecastCurrentCondition)
        tvTemp = findViewById(R.id.tvForecastCurrentTemp)
        tvFeelsLike = findViewById(R.id.tvForecastFeelsLike)
        tvMaxMin = findViewById(R.id.tvForecastMaxMin)
        tvError = findViewById(R.id.tvForecastError)
        tvSummary = findViewById(R.id.tvForecastSummary)
        cardCurrent = findViewById(R.id.cardForecastCurrent)
        cardDetails = findViewById(R.id.cardForecastDetails)
        recyclerDetails = findViewById(R.id.recyclerForecastDetails)
        cardSummary = findViewById(R.id.cardForecastSummary)
        cardHourly = findViewById(R.id.cardForecastHourly)
        cardDaily = findViewById(R.id.cardForecastDaily)
        recyclerHourly = findViewById(R.id.recyclerForecastHourly)
        recyclerDaily = findViewById(R.id.recyclerForecastDaily)

        recyclerDetails.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recyclerDetails.isNestedScrollingEnabled = false
        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerDaily.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.isNestedScrollingEnabled = false
        recyclerDaily.isNestedScrollingEnabled = false

        val cached = WeatherHelper.getCachedWeatherEntry()
        if (cached != null) {
            render(cached)
        }
        loadForecast(force = false, showLoadingIndicator = cached == null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_weather_forecast, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_refresh_weather -> {
            loadForecast(force = true, showLoadingIndicator = true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadForecast(force: Boolean, showLoadingIndicator: Boolean) {
        job?.cancel()
        if (showLoadingIndicator) {
            showLoading()
        }
        job = lifecycleScope.launch {
            val fresh = WeatherHelper.fetchForecast(this@WeatherForecastActivity, force = force)
            if (showLoadingIndicator) {
                hideLoading()
            }
            if (fresh != null) {
                render(fresh)
            } else if (!cardCurrent.isVisible) {
                tvError.isVisible = true
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun render(entry: WeatherHelper.WeatherCacheEntry) {
        tvError.isVisible = false
        cardCurrent.isVisible = true

        ivIcon.setImageResource(WeatherHelper.iconResForCode(entry.weatherCode, entry.isDay))
        tvCondition.text = getString(WeatherHelper.conditionLabelRes(entry.weatherCode))
        tvTemp.text = "${Math.round(entry.temperatureCelsius)}\u00b0"
        tvFeelsLike.text = getString(
            R.string.weather_feels_like,
            "${Math.round(entry.apparentTemperatureCelsius)}\u00b0"
        )

        val todayMax = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val todayMin = entry.dailyTemperatureMinCelsius.getOrNull(0)
        tvMaxMin.isVisible = todayMax != null && todayMin != null
        if (todayMax != null && todayMin != null) {
            tvMaxMin.text = getString(
                R.string.weather_max_min,
                "${Math.round(todayMax)}\u00b0",
                "${Math.round(todayMin)}\u00b0"
            )
        }

        val detailItems = buildDetailItems(entry)
        cardDetails.isVisible = detailItems.isNotEmpty()
        recyclerDetails.adapter = WeatherDetailAdapter(this, detailItems)

        cardSummary.isVisible = true
        tvSummary.text = buildDaySummary(entry)

        val hourlyItems = buildHourlyItems(entry)
        cardHourly.isVisible = hourlyItems.isNotEmpty()
        recyclerHourly.adapter = WeatherHourlyAdapter(this, hourlyItems)

        val dailyItems = buildDailyItems(entry)
        cardDaily.isVisible = dailyItems.isNotEmpty()
        recyclerDaily.adapter = WeatherDailyAdapter(this, dailyItems)
    }

    private fun buildDaySummary(entry: WeatherHelper.WeatherCacheEntry): String {
        val conditionLabel = getString(WeatherHelper.conditionLabelRes(entry.weatherCode)).lowercase(Locale.getDefault())
        val hi = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val lo = entry.dailyTemperatureMinCelsius.getOrNull(0)
        val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(0) ?: 0
        val wind = entry.windSpeedKmh

        val parts = mutableListOf<String>()
        parts.add(getString(R.string.weather_summary_condition, conditionLabel))
        if (hi != null && lo != null) {
            parts.add(
                getString(
                    R.string.weather_summary_high_low,
                    "${Math.round(hi)}\u00b0",
                    "${Math.round(lo)}\u00b0"
                )
            )
        }
        when {
            precip >= 70 -> parts.add(getString(R.string.weather_summary_rain_likely))
            precip >= 40 -> parts.add(getString(R.string.weather_summary_rain_possible))
            precip >= 20 -> parts.add(getString(R.string.weather_summary_rain_slight))
        }
        if (wind >= 30) {
            parts.add(getString(R.string.weather_summary_windy))
        }
        return parts.joinToString(" ")
    }

    private fun buildDetailItems(entry: WeatherHelper.WeatherCacheEntry): List<DetailItem> {
        val items = mutableListOf<DetailItem>()

        items += DetailItem(
            RemixR.drawable.rmx_weather_water_percent_line,
            getString(R.string.weather_detail_humidity),
            "${entry.relativeHumidity}%"
        )
        items += DetailItem(
            RemixR.drawable.rmx_weather_water_percent_line,
            getString(R.string.weather_detail_dew_point),
            "${Math.round(entry.dewPointCelsius)}\u00b0"
        )
        items += DetailItem(
            RemixR.drawable.rmx_weather_windy_line,
            getString(R.string.weather_detail_wind),
            getString(
                R.string.weather_wind_format,
                Math.round(entry.windSpeedKmh),
                compassDirection(entry.windDirectionDeg)
            )
        )
        items += DetailItem(
            RemixR.drawable.rmx_weather_windy_line,
            getString(R.string.weather_detail_wind_gusts),
            getString(R.string.weather_speed_kmh_format, Math.round(entry.windGustsKmh))
        )
        items += DetailItem(
            RemixR.drawable.rmx_speed_line,
            getString(R.string.weather_detail_pressure),
            getString(R.string.weather_pressure_format, Math.round(entry.pressureMsl))
        )
        items += DetailItem(
            RemixR.drawable.rmx_system_eye_line,
            getString(R.string.weather_detail_visibility),
            formatVisibility(entry.visibilityMeters)
        )
        items += DetailItem(
            RemixR.drawable.rmx_cloud_line,
            getString(R.string.weather_detail_cloud_cover),
            "${entry.cloudCoverPercent}%"
        )

        entry.dailyUvIndexMax.getOrNull(0)?.let { uv ->
            items += DetailItem(
                RemixR.drawable.rmx_weather_sun_line,
                getString(R.string.weather_detail_uv_index),
                String.format(Locale.getDefault(), "%.1f", uv)
            )
        }
        formatTimeOfDay(entry.dailySunriseIso.getOrNull(0))?.let { sunrise ->
            items += DetailItem(RemixR.drawable.rmx_weather_shining_2_line, getString(R.string.weather_detail_sunrise), sunrise)
        }
        formatTimeOfDay(entry.dailySunsetIso.getOrNull(0))?.let { sunset ->
            items += DetailItem(RemixR.drawable.rmx_weather_shining_line, getString(R.string.weather_detail_sunset), sunset)
        }
        entry.dailyPrecipitationSumMm.getOrNull(0)?.let { mm ->
            items += DetailItem(
                RemixR.drawable.rmx_weather_rainy_line,
                getString(R.string.weather_detail_precipitation),
                getString(R.string.weather_precipitation_format, mm)
            )
        }
        entry.airQualityIndex?.let { aqi ->
            items += DetailItem(
                RemixR.drawable.rmx_weather_haze_line,
                getString(R.string.weather_detail_air_quality),
                "$aqi \u00b7 ${getString(WeatherHelper.airQualityLabelRes(aqi))}"
            )
        }

        return items
    }

    private fun compassDirection(degrees: Int): String {
        val directions = listOf(
            R.string.weather_compass_n, R.string.weather_compass_ne,
            R.string.weather_compass_e, R.string.weather_compass_se,
            R.string.weather_compass_s, R.string.weather_compass_sw,
            R.string.weather_compass_w, R.string.weather_compass_nw
        )
        val index = (((degrees % 360) + 360) % 360 / 45.0).roundToInt() % 8
        return getString(directions[index])
    }

    private fun formatVisibility(meters: Double): String =
    if (meters >= 1000) {
        getString(R.string.weather_visibility_km_format, meters / 1000.0)
    } else {
        getString(R.string.weather_visibility_m_format, Math.round(meters))
    }

    private fun formatTimeOfDay(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso)
            parsed?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildHourlyItems(entry: WeatherHelper.WeatherCacheEntry): List<HourlyForecastItem> {
        val times = entry.hourlyTimeIso
        if (times.isNullOrEmpty()) return emptyList()

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
        val startIndex = times.indexOfFirst { it >= nowIso }.let { if (it < 0) 0 else it }
        val endIndex = minOf(times.size, startIndex + 24)

        val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val timeFmt = SimpleDateFormat("HH.mm", Locale.getDefault())
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return (startIndex until endIndex).mapNotNull { i ->
            val iso = times.getOrNull(i) ?: return@mapNotNull null
            val temp = entry.hourlyTemperatureCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.hourlyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.hourlyPrecipitationProbability.getOrNull(i) ?: 0
            val isDay = (entry.hourlyIsDay.getOrNull(i) ?: 1) == 1
            val date = try {
                isoParser.parse(iso)
            } catch (e: Exception) {
                null
            }

            HourlyForecastItem(
                timeLabel = if (i == startIndex) {
                    getString(R.string.weather_now)
                } else {
                    date?.let { timeFmt.format(it) } ?: ""
                },
                dayLabel = date?.let { dayFmt.format(it) } ?: "",
                tempCelsius = Math.round(temp).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, isDay),
                isNow = i == startIndex
            )
        }
    }

    private fun buildDailyItems(entry: WeatherHelper.WeatherCacheEntry): List<DailyForecastItem> {
        val dates = entry.dailyDateIso
        if (dates.isNullOrEmpty()) return emptyList()

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return dates.indices.mapNotNull { i ->
            val dateStr = dates.getOrNull(i) ?: return@mapNotNull null
            val max = entry.dailyTemperatureMaxCelsius.getOrNull(i) ?: return@mapNotNull null
            val min = entry.dailyTemperatureMinCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.dailyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(i) ?: 0

            val weekdayLabel = if (i == 0) {
                getString(R.string.weather_today)
            } else {
                val date = try {
                    dateParser.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                date?.let { dayFmt.format(it) } ?: ""
            }

            DailyForecastItem(
                weekdayLabel = weekdayLabel,
                maxTempCelsius = Math.round(max).toInt(),
                minTempCelsius = Math.round(min).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, true)
            )
        }
    }
}
