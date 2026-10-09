package de.letheapp.nocturn.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Aktuelles Wetter fürs Uhr-Widget: Temperatur gerundet + ein Emoji statt eines Icon-Assets. */
data class WidgetWeather(val temperatureCelsius: Int, val emoji: String)

private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L

/**
 * Minimaler OpenWeatherMap-Client fürs Uhr-Widget (CLAUDE.md Kernfunktion 7), mit OkHttp +
 * Androids eingebautem `org.json` statt Retrofit/Moshi, passend zur FOSS-Minimalismus-Leitlinie.
 * Der Standort kommt bewusst über Androids Plattform-`LocationManager` (letzte bekannte Position,
 * keine aktive GPS-Anfrage) statt über die proprietäre Google-Play-Services-Standort-Lib, da im
 * Projekt bislang keine GMS-Abhängigkeit existiert. Ergebnisse werden 30 Minuten lang in
 * SharedPreferences gecacht, damit der minütliche Uhr-Widget-Tick nicht bei jedem Update einen
 * Netzwerk-Request auslöst.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cachePrefs = context.getSharedPreferences("weather_widget_cache", Context.MODE_PRIVATE)

    suspend fun currentWeather(): WidgetWeather? = withContext(Dispatchers.IO) {
        val cachedAt = cachePrefs.getLong(KEY_CACHED_AT, 0L)
        val cached = readCache()
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_VALIDITY_MS) {
            return@withContext cached
        }
        fetchFromApi() ?: cached
    }

    private fun fetchFromApi(): WidgetWeather? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) return null
        val location = lastKnownLocation() ?: return null

        return runCatching {
            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric&lang=de"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val obj = JSONObject(body)
                val temp = obj.getJSONObject("main").getDouble("temp").toInt()
                val weatherId = obj.getJSONArray("weather").getJSONObject(0).getInt("id")
                WidgetWeather(temp, emojiForConditionId(weatherId)).also(::writeCache)
            }
        }.getOrNull()
    }

    private fun lastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        return locationManager.allProviders
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun readCache(): WidgetWeather? {
        val temp = cachePrefs.getInt(KEY_TEMP, Int.MIN_VALUE)
        val emoji = cachePrefs.getString(KEY_EMOJI, null)
        if (temp == Int.MIN_VALUE || emoji == null) return null
        return WidgetWeather(temp, emoji)
    }

    private fun writeCache(weather: WidgetWeather) {
        cachePrefs.edit()
            .putInt(KEY_TEMP, weather.temperatureCelsius)
            .putString(KEY_EMOJI, weather.emoji)
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    /** https://openweathermap.org/weather-conditions */
    private fun emojiForConditionId(id: Int): String = when {
        id in 200..232 -> "⛈"
        id in 300..321 -> "🌦"
        id in 500..531 -> "🌧"
        id in 600..622 -> "❄"
        id in 701..781 -> "🌫"
        id == 800 -> "☀"
        id == 801 -> "🌤"
        id in 802..804 -> "☁"
        else -> "☁"
    }

    private companion object {
        const val KEY_TEMP = "temp"
        const val KEY_EMOJI = "emoji"
        const val KEY_CACHED_AT = "cached_at"
    }
}
