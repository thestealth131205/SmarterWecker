package de.letheapp.nocturn.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.BuildConfig
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aktuelles Wetter, wie es die OpenWeatherMap-"Current Weather Data"-API liefert (kostenloser
 * Tarif, keine separate One-Call-Subscription nötig) — genutzt sowohl fürs Topbar-Wettersymbol
 * (Detailansicht) als auch fürs Uhr-Widget (nur [temperatureCelsius]/[emoji]).
 * [tempMinCelsius]/[tempMaxCelsius] sind dabei die von OpenWeatherMap in diesem Endpoint
 * gelieferten aktuellen Schwankungswerte, keine echte Tagesvorhersage (die bräuchte den
 * kostenpflichtigen One-Call-Endpoint).
 */
data class DetailedWeather(
    val cityName: String,
    val temperatureCelsius: Int,
    val feelsLikeCelsius: Int,
    val tempMinCelsius: Int,
    val tempMaxCelsius: Int,
    val humidityPercent: Int,
    val pressureHpa: Int,
    val windSpeedKmh: Int,
    val description: String,
    val emoji: String,
    val sunriseEpochMillis: Long,
    val sunsetEpochMillis: Long
)

/** Ein Treffer der OpenWeatherMap-Geocoding-API, zur Auswahl als manueller Wetter-Ort. */
data class GeoSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L

/**
 * Minimaler OpenWeatherMap-Client (CLAUDE.md Kernfunktion 7), mit OkHttp + Androids
 * eingebautem `org.json` statt Retrofit/Moshi, passend zur FOSS-Minimalismus-Leitlinie.
 * Der Standort kommt bewusst über Androids Plattform-`LocationManager` (letzte bekannte
 * Position, keine aktive GPS-Anfrage) statt über die proprietäre Google-Play-Services-
 * Standort-Lib, da im Projekt bislang keine GMS-Abhängigkeit existiert. Ergebnisse werden 30
 * Minuten lang in SharedPreferences gecacht, damit der minütliche Uhr-Widget-Tick nicht bei
 * jedem Update einen Netzwerk-Request auslöst. Die Wetteranzeige selbst ist über
 * [AppPreferences.isWeatherEnabled] ein bewusstes Opt-in (Einstellungen-Screen) — ist sie aus,
 * liefert [currentWeather] immer `null`, ohne überhaupt einen Request abzusetzen.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cachePrefs = context.getSharedPreferences("weather_widget_cache", Context.MODE_PRIVATE)

    suspend fun currentWeather(): DetailedWeather? = withContext(Dispatchers.IO) {
        if (!appPreferences.isWeatherEnabled()) return@withContext null

        val cachedAt = cachePrefs.getLong(KEY_CACHED_AT, 0L)
        val cached = readCache()
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_VALIDITY_MS) {
            return@withContext cached
        }
        fetchFromApi() ?: cached
    }

    /** Löscht den Wetter-Cache, z. B. nachdem der Nutzer einen anderen Ort gewählt hat. */
    fun clearCache() {
        cachePrefs.edit().clear().apply()
    }

    /**
     * Sucht Orte über die OpenWeatherMap-Geocoding-API (derselbe API-Key, kein separates
     * Abo nötig) — liefert Live-Vorschläge für die Ortssuche im Einstellungen-Screen.
     */
    suspend fun searchLocations(query: String): List<GeoSuggestion> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()

        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.openweathermap.org/geo/1.0/direct?q=$encoded&limit=5&appid=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                (0 until array.length()).map { index ->
                    val obj = array.getJSONObject(index)
                    val name = obj.getString("name")
                    val state = obj.optString("state", "")
                    val country = obj.optString("country", "")
                    val parts = listOfNotNull(name, state.takeIf { it.isNotBlank() }, country.takeIf { it.isNotBlank() })
                    GeoSuggestion(
                        displayName = parts.joinToString(", "),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchFromApi(): DetailedWeather? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) return null
        val location = resolvedLocation() ?: return null

        return runCatching {
            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric&lang=de"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                parseWeather(JSONObject(body))?.also(::writeCache)
            }
        }.getOrNull()
    }

    private fun parseWeather(obj: JSONObject): DetailedWeather {
        val main = obj.getJSONObject("main")
        val weather = obj.getJSONArray("weather").getJSONObject(0)
        val weatherId = weather.getInt("id")
        val windSpeedMs = obj.optJSONObject("wind")?.optDouble("speed", 0.0) ?: 0.0
        val sys = obj.optJSONObject("sys")

        return DetailedWeather(
            cityName = obj.optString("name", ""),
            temperatureCelsius = main.getDouble("temp").toInt(),
            feelsLikeCelsius = main.getDouble("feels_like").toInt(),
            tempMinCelsius = main.getDouble("temp_min").toInt(),
            tempMaxCelsius = main.getDouble("temp_max").toInt(),
            humidityPercent = main.getInt("humidity"),
            pressureHpa = main.getInt("pressure"),
            windSpeedKmh = (windSpeedMs * 3.6).toInt(),
            description = weather.optString("description", "").replaceFirstChar { it.uppercase() },
            emoji = emojiForConditionId(weatherId),
            sunriseEpochMillis = (sys?.optLong("sunrise", 0L) ?: 0L) * 1000L,
            sunsetEpochMillis = (sys?.optLong("sunset", 0L) ?: 0L) * 1000L
        )
    }

    /** Manuell gewählter Ort (Einstellungen-Screen) hat Vorrang vor der GPS-Ermittlung. */
    private fun resolvedLocation(): Location? {
        val manual = appPreferences.manualLocation.value
        if (manual != null) {
            return Location("manual").apply {
                latitude = manual.latitude
                longitude = manual.longitude
            }
        }
        return lastKnownLocation()
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

    private fun readCache(): DetailedWeather? {
        val temp = cachePrefs.getInt(KEY_TEMP, Int.MIN_VALUE)
        val emoji = cachePrefs.getString(KEY_EMOJI, null)
        if (temp == Int.MIN_VALUE || emoji == null) return null
        return DetailedWeather(
            cityName = cachePrefs.getString(KEY_CITY, "").orEmpty(),
            temperatureCelsius = temp,
            feelsLikeCelsius = cachePrefs.getInt(KEY_FEELS_LIKE, temp),
            tempMinCelsius = cachePrefs.getInt(KEY_TEMP_MIN, temp),
            tempMaxCelsius = cachePrefs.getInt(KEY_TEMP_MAX, temp),
            humidityPercent = cachePrefs.getInt(KEY_HUMIDITY, 0),
            pressureHpa = cachePrefs.getInt(KEY_PRESSURE, 0),
            windSpeedKmh = cachePrefs.getInt(KEY_WIND, 0),
            description = cachePrefs.getString(KEY_DESCRIPTION, "").orEmpty(),
            emoji = emoji,
            sunriseEpochMillis = cachePrefs.getLong(KEY_SUNRISE, 0L),
            sunsetEpochMillis = cachePrefs.getLong(KEY_SUNSET, 0L)
        )
    }

    private fun writeCache(weather: DetailedWeather) {
        cachePrefs.edit()
            .putString(KEY_CITY, weather.cityName)
            .putInt(KEY_TEMP, weather.temperatureCelsius)
            .putInt(KEY_FEELS_LIKE, weather.feelsLikeCelsius)
            .putInt(KEY_TEMP_MIN, weather.tempMinCelsius)
            .putInt(KEY_TEMP_MAX, weather.tempMaxCelsius)
            .putInt(KEY_HUMIDITY, weather.humidityPercent)
            .putInt(KEY_PRESSURE, weather.pressureHpa)
            .putInt(KEY_WIND, weather.windSpeedKmh)
            .putString(KEY_DESCRIPTION, weather.description)
            .putString(KEY_EMOJI, weather.emoji)
            .putLong(KEY_SUNRISE, weather.sunriseEpochMillis)
            .putLong(KEY_SUNSET, weather.sunsetEpochMillis)
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
        const val KEY_CITY = "city"
        const val KEY_TEMP = "temp"
        const val KEY_FEELS_LIKE = "feels_like"
        const val KEY_TEMP_MIN = "temp_min"
        const val KEY_TEMP_MAX = "temp_max"
        const val KEY_HUMIDITY = "humidity"
        const val KEY_PRESSURE = "pressure"
        const val KEY_WIND = "wind"
        const val KEY_DESCRIPTION = "description"
        const val KEY_EMOJI = "emoji"
        const val KEY_SUNRISE = "sunrise"
        const val KEY_SUNSET = "sunset"
        const val KEY_CACHED_AT = "cached_at"
    }
}
