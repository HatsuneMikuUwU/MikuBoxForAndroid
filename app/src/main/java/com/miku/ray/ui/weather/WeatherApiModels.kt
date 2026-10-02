package com.miku.ray.ui.weather

import com.google.gson.annotations.SerializedName

data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OpenMeteoDaily? = null
)

data class OpenMeteoCurrent(
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: Double = 0.0,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int = 0,
    @SerializedName("dew_point_2m") val dewPoint: Double = 0.0,
    @SerializedName("weather_code") val weatherCode: Int = 0,
    @SerializedName("wind_speed_10m") val windSpeed: Double = 0.0,
    @SerializedName("wind_direction_10m") val windDirection: Int = 0,
    @SerializedName("pressure_msl") val pressureMsl: Double = 0.0,
    @SerializedName("visibility") val visibility: Double = 0.0,
    @SerializedName("cloud_cover") val cloudCover: Int = 0,
    @SerializedName("wind_gusts_10m") val windGusts: Double = 0.0,
    @SerializedName("is_day") val isDay: Int = 1
)

data class OpenMeteoHourly(
    val time: List<String>? = null,
    @SerializedName("temperature_2m") val temperature: List<Double>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    @SerializedName("is_day") val isDay: List<Int>? = null
)

data class OpenMeteoDaily(
    val time: List<String>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("temperature_2m_max") val temperatureMax: List<Double>? = null,
    @SerializedName("temperature_2m_min") val temperatureMin: List<Double>? = null,
    @SerializedName("apparent_temperature_max") val apparentTemperatureMax: List<Double>? = null,
    @SerializedName("apparent_temperature_min") val apparentTemperatureMin: List<Double>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>? = null,
    @SerializedName("precipitation_sum") val precipitationSum: List<Double>? = null,
    @SerializedName("uv_index_max") val uvIndexMax: List<Double>? = null,
    @SerializedName("sunrise") val sunrise: List<String>? = null,
    @SerializedName("sunset") val sunset: List<String>? = null,
    @SerializedName("daylight_duration") val daylightDuration: List<Double>? = null,
    @SerializedName("sunshine_duration") val sunshineDuration: List<Double>? = null
)

data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult>? = null
)

data class OpenMeteoGeocodingResult(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val admin1: String? = null
)

data class OpenMeteoAirQualityResponse(
    val current: OpenMeteoAirQualityCurrent? = null
)

data class OpenMeteoAirQualityCurrent(
    @SerializedName("us_aqi") val usAqi: Int? = null
)
