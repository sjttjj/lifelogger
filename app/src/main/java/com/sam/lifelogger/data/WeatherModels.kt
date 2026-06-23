package com.sam.lifelogger.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Models for the /api/weather endpoint response.
 */
data class WeatherResponse(
    val location: WeatherLocation,
    val current: WeatherCurrent,
    val daily: List<WeatherDaily>,
    val source: String,
    val fetchedAt: String,
    val cacheExpiresAt: String,
    val stale: Boolean
)

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String
)

data class WeatherCurrent(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val condition: String,
    val weatherCode: Int,
    val precipitationMm: Double,
    val windKmh: Double,
    val observedAt: String
)

data class WeatherDaily(
    val date: String,
    val condition: String,
    val weatherCode: Int,
    val temperatureMinC: Double,
    val temperatureMaxC: Double,
    val precipitationProbabilityMax: Int
)

/** Parse a WeatherResponse from a raw JSON string. */
fun parseWeatherResponse(json: String): WeatherResponse {
    val root = JSONObject(json)

    val loc = root.getJSONObject("location")
    val location = WeatherLocation(
        name = loc.getString("name"),
        latitude = loc.getDouble("latitude"),
        longitude = loc.getDouble("longitude"),
        timezone = loc.getString("timezone")
    )

    val cur = root.getJSONObject("current")
    val current = WeatherCurrent(
        temperatureC = cur.getDouble("temperature_c"),
        apparentTemperatureC = cur.getDouble("apparent_temperature_c"),
        condition = cur.getString("condition"),
        weatherCode = cur.getInt("weather_code"),
        precipitationMm = cur.getDouble("precipitation_mm"),
        windKmh = cur.getDouble("wind_kmh"),
        observedAt = cur.getString("observed_at")
    )

    val dailyArr = root.getJSONArray("daily")
    val daily = (0 until dailyArr.length()).map { i ->
        val d = dailyArr.getJSONObject(i)
        WeatherDaily(
            date = d.getString("date"),
            condition = d.getString("condition"),
            weatherCode = d.getInt("weather_code"),
            temperatureMinC = d.getDouble("temperature_min_c"),
            temperatureMaxC = d.getDouble("temperature_max_c"),
            precipitationProbabilityMax = d.getInt("precipitation_probability_max")
        )
    }

    return WeatherResponse(
        location = location,
        current = current,
        daily = daily,
        source = root.getString("source"),
        fetchedAt = root.getString("fetched_at"),
        cacheExpiresAt = root.getString("cache_expires_at"),
        stale = root.optBoolean("stale", false)
    )
}
