package de.letheapp.nocturn.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.BuildConfig
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aktuelles Wetter, wie es die OpenWeatherMap-"Current Weather Data"-API liefert (kostenloser
 * Tarif, keine separate One-Call-Subscription nötig) — genutzt sowohl im Wetter-Screen
 * (aktuelle Bedingungen) als auch fürs Uhr-Widget (nur [temperatureCelsius]/[emoji]).
 * [tempMinCelsius]/[tempMaxCelsius] sind dabei die von OpenWeatherMap in diesem Endpoint
 * gelieferten aktuellen Schwankungswerte, keine echte Tagesvorhersage (siehe [DailyForecast]
 * dafür).
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

/**
 * Ein Tag der 8-Tage-Vorhersage (OpenWeatherMap One Call 4.0, siehe [WeatherRepository.dailyForecast]).
 * [summary]/[humidityPercent]/[windSpeedKmh]/[pressureHpa]/[precipitationMm] sind optional, da sie
 * defensiv aus verschachtelten Feldern der Timeline-API gelesen werden und je nach Antwort fehlen
 * können — zeigt die Tages-Detailansicht im Wetter-Screen zusätzlich an, falls vorhanden.
 */
data class DailyForecast(
    val dateEpochMillis: Long,
    val minCelsius: Int,
    val maxCelsius: Int,
    val emoji: String,
    val description: String,
    val precipitationProbabilityPercent: Int,
    val summary: String? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Int? = null,
    val pressureHpa: Int? = null,
    val precipitationMm: Double? = null
)

/** Eine Stunde der stündlichen Vorhersage für den aktuellen Tag (OpenWeatherMap One Call 4.0, siehe [WeatherRepository.hourlyForecastToday]). */
data class HourlyForecast(
    val timeEpochMillis: Long,
    val temperatureCelsius: Int,
    val emoji: String,
    val description: String,
    val precipitationProbabilityPercent: Int
)

/** Ein Treffer der OpenWeatherMap-Geocoding-API, zur Auswahl als manueller Wetter-Ort. */
data class GeoSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L
private const val FORECAST_CACHE_VALIDITY_MS = 60 * 60 * 1000L

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
 *
 * Die 8-Tage-Vorhersage ([dailyForecast]) braucht den OpenWeatherMap-"One Call API 4.0"-
 * Endpoint (`/data/4.0/onecall/timeline/1day`) — der aktuelle "Current Weather Data"-Endpoint
 * liefert nur Momentanwerte, keine echte Tagesvorhersage. OpenWeatherMap hat die alte "One
 * Call API 3.0" (`/data/3.0/onecall`) im Herbst 2026 durch 4.0 mit separaten, fokussierten
 * Endpoints pro Zeitauflösung ersetzt (hier genutzt: "1-day timeline" statt des kombinierten
 * `/onecall`-Response mit `exclude`-Parameter) — 3.0-Zugriffsschlüssel liefern seitdem bei 4.0
 * `401`, und umgekehrt liefert 3.0 `401`, sobald im OpenWeatherMap-Konto nur noch das neue "One
 * Call by Call"-Abo (= 4.0) aktiviert ist, nicht das alte 3.0-Abo. Das 4.0-Abo ist weiterhin im
 * kostenlosen Kontingent enthalten (1.000 Abrufe/Tag), verlangt aber ebenso einmalig eine
 * Abo-Aktivierung mit Zahlungsmethode im OpenWeatherMap-Konto (wird nicht belastet, solange das
 * Kontingent nicht überschritten wird) — ohne diese Aktivierung liefert der Endpoint `401`.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {

    companion object {
        /**
         * Platzhalter-ID des Hauptorts (manueller Ort bzw. automatische Standortermittlung, siehe
         * [AppPreferences.manualLocation]/[AppPreferences.liveLocationEnabled]) — alle weiteren
         * Orte werden über ihre echte [AppPreferences.SavedLocation.id] angesprochen.
         */
        const val MAIN_LOCATION_ID = "main"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cachePrefs = context.getSharedPreferences("weather_widget_cache", Context.MODE_PRIVATE)

    suspend fun currentWeather(locationId: String = MAIN_LOCATION_ID): DetailedWeather? = withContext(Dispatchers.IO) {
        if (!appPreferences.isWeatherEnabled()) return@withContext null

        val cachedAt = cachePrefs.getLong(KEY_CACHED_AT + locationId, 0L)
        val cached = readCache(locationId)
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_VALIDITY_MS) {
            return@withContext cached
        }
        fetchFromApi(locationId) ?: cached
    }

    /**
     * 8-Tage-Vorhersage (heute + 7 Tage) über OpenWeatherMap One Call 3.0, siehe Klassen-
     * Dokumentation zur nötigen einmaligen Abo-Aktivierung im OpenWeatherMap-Konto.
     */
    suspend fun dailyForecast(locationId: String = MAIN_LOCATION_ID): List<DailyForecast>? = withContext(Dispatchers.IO) {
        if (!appPreferences.isWeatherEnabled()) return@withContext null

        val cachedAt = cachePrefs.getLong(KEY_FORECAST_CACHED_AT + locationId, 0L)
        val cached = readForecastCache(locationId)
        if (cached != null && System.currentTimeMillis() - cachedAt < FORECAST_CACHE_VALIDITY_MS) {
            return@withContext cached
        }
        fetchForecastFromApi(locationId) ?: cached
    }

    /**
     * Grund, warum [dailyForecast] zuletzt `null`/leer geliefert hat (z. B. HTTP-Statuscode) —
     * wird nach jedem Abruf aktualisiert, damit der Wetter-Screen statt einer generischen
     * Meldung den tatsächlichen Fehler anzeigen kann (z. B. "401" bei fehlender/noch nicht
     * aktiver One-Call-3.0-Abo-Aktivierung).
     */
    @Volatile
    var lastForecastError: String? = null
        private set

    /**
     * Stündliche Vorhersage für den Rest des **aktuellen Tages**, über den OpenWeatherMap-
     * "One Call API 4.0"-Endpoint `/data/4.0/onecall/timeline/1h` (liefert bis zu 20 Stunden
     * ab jetzt, hier auf den aktuellen Kalendertag gefiltert). Wird im Wetter-Screen oberhalb
     * der 8-Tage-Vorhersage als eigener, vertikal scrollbarer Abschnitt angezeigt.
     */
    suspend fun hourlyForecastToday(locationId: String = MAIN_LOCATION_ID): List<HourlyForecast>? = withContext(Dispatchers.IO) {
        if (!appPreferences.isWeatherEnabled()) return@withContext null

        val cachedAt = cachePrefs.getLong(KEY_HOURLY_CACHED_AT + locationId, 0L)
        val cached = readHourlyCache(locationId)
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_VALIDITY_MS) {
            return@withContext cached
        }
        fetchHourlyFromApi(locationId) ?: cached
    }

    @Volatile
    var lastHourlyError: String? = null
        private set

    /**
     * Löscht den Wetter-Cache für einen Ort, z. B. nachdem der Nutzer dessen Koordinaten geändert
     * hat (Hauptort) oder ihn aus der Liste entfernt hat (zusätzlicher Ort). Ohne Parameter wird
     * nur der Hauptort-Cache gelöscht (bisheriges Verhalten, Standardfall nach Ortswahl).
     */
    fun clearCache(locationId: String = MAIN_LOCATION_ID) {
        cachePrefs.edit()
            .remove(KEY_CITY + locationId)
            .remove(KEY_TEMP + locationId)
            .remove(KEY_FEELS_LIKE + locationId)
            .remove(KEY_TEMP_MIN + locationId)
            .remove(KEY_TEMP_MAX + locationId)
            .remove(KEY_HUMIDITY + locationId)
            .remove(KEY_PRESSURE + locationId)
            .remove(KEY_WIND + locationId)
            .remove(KEY_DESCRIPTION + locationId)
            .remove(KEY_EMOJI + locationId)
            .remove(KEY_SUNRISE + locationId)
            .remove(KEY_SUNSET + locationId)
            .remove(KEY_CACHED_AT + locationId)
            .remove(KEY_FORECAST_DATA + locationId)
            .remove(KEY_FORECAST_CACHED_AT + locationId)
            .remove(KEY_HOURLY_DATA + locationId)
            .remove(KEY_HOURLY_CACHED_AT + locationId)
            .apply()
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

    private suspend fun fetchFromApi(locationId: String): DetailedWeather? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) return null
        val location = resolveLocation(locationId) ?: return null

        return runCatching {
            val url = "https://api.openweathermap.org/data/2.5/weather?" +
                "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric&lang=de"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                parseWeather(JSONObject(body))?.also { writeCache(locationId, it) }
            }
        }.getOrNull()
    }

    private suspend fun fetchForecastFromApi(locationId: String): List<DailyForecast>? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) {
            lastForecastError = "Kein OpenWeatherMap-API-Key hinterlegt."
            return null
        }
        val location = resolveLocation(locationId) ?: run {
            lastForecastError = "Kein Standort verfügbar (Berechtigung/Ortswahl prüfen)."
            return null
        }

        val result = runCatching {
            val url = "https://api.openweathermap.org/data/4.0/onecall/timeline/1day?" +
                "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric&lang=de"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodySnippet = response.body?.string().orEmpty().take(200)
                    error("HTTP ${response.code} ${response.message}".trim() + if (bodySnippet.isNotBlank()) " – $bodySnippet" else "")
                }
                val body = response.body?.string().orEmpty()
                val daily = JSONObject(body).optJSONArray("data")
                    ?: error("Antwort enthält kein \"data\"-Feld: ${body.take(200)}")
                (0 until daily.length()).take(8)
                    .map { index -> parseDailyForecast(daily.getJSONObject(index)) }
                    .also { writeForecastCache(locationId, it) }
            }
        }
        result.fold(
            onSuccess = { lastForecastError = null },
            onFailure = { throwable -> lastForecastError = throwable.message ?: throwable.toString() }
        )
        return result.getOrNull()
    }

    private suspend fun fetchHourlyFromApi(locationId: String): List<HourlyForecast>? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) {
            lastHourlyError = "Kein OpenWeatherMap-API-Key hinterlegt."
            return null
        }
        val location = resolveLocation(locationId) ?: run {
            lastHourlyError = "Kein Standort verfügbar (Berechtigung/Ortswahl prüfen)."
            return null
        }

        val result = runCatching {
            val url = "https://api.openweathermap.org/data/4.0/onecall/timeline/1h?" +
                "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric&lang=de"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodySnippet = response.body?.string().orEmpty().take(200)
                    error("HTTP ${response.code} ${response.message}".trim() + if (bodySnippet.isNotBlank()) " – $bodySnippet" else "")
                }
                val body = response.body?.string().orEmpty()
                val hourly = JSONObject(body).optJSONArray("data")
                    ?: error("Antwort enthält kein \"data\"-Feld: ${body.take(200)}")
                val today = java.time.LocalDate.now()
                (0 until hourly.length())
                    .map { index -> parseHourlyForecast(hourly.getJSONObject(index)) }
                    .filter { hour ->
                        Instant.ofEpochMilli(hour.timeEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == today
                    }
                    .also { writeHourlyCache(locationId, it) }
            }
        }
        result.fold(
            onSuccess = { lastHourlyError = null },
            onFailure = { throwable -> lastHourlyError = throwable.message ?: throwable.toString() }
        )
        return result.getOrNull()
    }

    private fun parseHourlyForecast(obj: JSONObject): HourlyForecast {
        val weather = obj.getJSONArray("weather").getJSONObject(0)
        return HourlyForecast(
            timeEpochMillis = obj.getLong("dt") * 1000L,
            temperatureCelsius = obj.getDouble("temp").toInt(),
            emoji = emojiForConditionId(weather.getInt("id")),
            description = weather.optString("description", "").replaceFirstChar { it.uppercase() },
            precipitationProbabilityPercent = (obj.optDouble("pop", 0.0) * 100).toInt()
        )
    }

    private fun parseDailyForecast(obj: JSONObject): DailyForecast {
        val temp = obj.getJSONObject("temp")
        val weather = obj.getJSONArray("weather").getJSONObject(0)
        val humidity = obj.optJSONObject("humidity")?.optDouble("afternoon", -1.0) ?: -1.0
        val windMaxSpeed = obj.optJSONObject("wind")?.optJSONObject("max")?.optDouble("speed", -1.0) ?: -1.0
        val pressure = obj.optJSONObject("pressure")?.optDouble("afternoon", -1.0) ?: -1.0
        val precipitationTotal = obj.optJSONObject("precipitation")?.optDouble("total", -1.0) ?: -1.0
        return DailyForecast(
            dateEpochMillis = obj.getLong("dt") * 1000L,
            minCelsius = temp.getDouble("min").toInt(),
            maxCelsius = temp.getDouble("max").toInt(),
            emoji = emojiForConditionId(weather.getInt("id")),
            description = weather.optString("description", "").replaceFirstChar { it.uppercase() },
            precipitationProbabilityPercent = (obj.optDouble("pop", 0.0) * 100).toInt(),
            summary = obj.optString("summary", "").takeIf { it.isNotBlank() },
            humidityPercent = humidity.takeIf { it >= 0 }?.toInt(),
            windSpeedKmh = windMaxSpeed.takeIf { it >= 0 }?.let { (it * 3.6).toInt() },
            pressureHpa = pressure.takeIf { it >= 0 }?.toInt(),
            precipitationMm = precipitationTotal.takeIf { it >= 0 }
        )
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

    /**
     * Löst einen [locationId] in tatsächliche Koordinaten auf. Für [MAIN_LOCATION_ID] gilt die
     * bisherige Logik (manueller Ort > Live-Standort > letzte bekannte Position); für einen
     * zusätzlichen, in [AppPreferences.additionalLocations] gespeicherten Ort werden dessen fest
     * hinterlegte Koordinaten verwendet (keine Geräte-Standortermittlung nötig).
     */
    private suspend fun resolveLocation(locationId: String): Location? {
        if (locationId != MAIN_LOCATION_ID) {
            val saved = appPreferences.additionalLocations.value.firstOrNull { it.id == locationId }
                ?: return null
            return Location("saved").apply {
                latitude = saved.latitude
                longitude = saved.longitude
            }
        }
        val manual = appPreferences.manualLocation.value
        if (manual != null) {
            return Location("manual").apply {
                latitude = manual.latitude
                longitude = manual.longitude
            }
        }
        if (appPreferences.isLiveLocationEnabled()) {
            freshLocation()?.let { return it }
        }
        return lastKnownLocation()
    }

    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        return locationManager.allProviders
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Fordert aktiv eine aktuelle, ungefähre Position an (statt der u. U. veralteten letzten
     * bekannten Position) — über Androids `LocationManager.getCurrentLocation` (API 30+, seit
     * minSdk 33 immer verfügbar), mit COARSE-tauglichen Providern (Netzwerk vor GPS) und einem
     * harten 8-Sekunden-Timeout, damit ein deaktivierter Provider die Wetter-Aktualisierung nicht
     * blockiert.
     */
    private suspend fun freshLocation(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null

        return withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        { it.run() },
                        { location -> if (continuation.isActive) continuation.resume(location) }
                    )
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun readCache(locationId: String): DetailedWeather? {
        val temp = cachePrefs.getInt(KEY_TEMP + locationId, Int.MIN_VALUE)
        val emoji = cachePrefs.getString(KEY_EMOJI + locationId, null)
        if (temp == Int.MIN_VALUE || emoji == null) return null
        return DetailedWeather(
            cityName = cachePrefs.getString(KEY_CITY + locationId, "").orEmpty(),
            temperatureCelsius = temp,
            feelsLikeCelsius = cachePrefs.getInt(KEY_FEELS_LIKE + locationId, temp),
            tempMinCelsius = cachePrefs.getInt(KEY_TEMP_MIN + locationId, temp),
            tempMaxCelsius = cachePrefs.getInt(KEY_TEMP_MAX + locationId, temp),
            humidityPercent = cachePrefs.getInt(KEY_HUMIDITY + locationId, 0),
            pressureHpa = cachePrefs.getInt(KEY_PRESSURE + locationId, 0),
            windSpeedKmh = cachePrefs.getInt(KEY_WIND + locationId, 0),
            description = cachePrefs.getString(KEY_DESCRIPTION + locationId, "").orEmpty(),
            emoji = emoji,
            sunriseEpochMillis = cachePrefs.getLong(KEY_SUNRISE + locationId, 0L),
            sunsetEpochMillis = cachePrefs.getLong(KEY_SUNSET + locationId, 0L)
        )
    }

    private fun writeCache(locationId: String, weather: DetailedWeather) {
        cachePrefs.edit()
            .putString(KEY_CITY + locationId, weather.cityName)
            .putInt(KEY_TEMP + locationId, weather.temperatureCelsius)
            .putInt(KEY_FEELS_LIKE + locationId, weather.feelsLikeCelsius)
            .putInt(KEY_TEMP_MIN + locationId, weather.tempMinCelsius)
            .putInt(KEY_TEMP_MAX + locationId, weather.tempMaxCelsius)
            .putInt(KEY_HUMIDITY + locationId, weather.humidityPercent)
            .putInt(KEY_PRESSURE + locationId, weather.pressureHpa)
            .putInt(KEY_WIND + locationId, weather.windSpeedKmh)
            .putString(KEY_DESCRIPTION + locationId, weather.description)
            .putString(KEY_EMOJI + locationId, weather.emoji)
            .putLong(KEY_SUNRISE + locationId, weather.sunriseEpochMillis)
            .putLong(KEY_SUNSET + locationId, weather.sunsetEpochMillis)
            .putLong(KEY_CACHED_AT + locationId, System.currentTimeMillis())
            .apply()
    }

    private fun readForecastCache(locationId: String): List<DailyForecast>? {
        val json = cachePrefs.getString(KEY_FORECAST_DATA + locationId, null) ?: return null
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                DailyForecast(
                    dateEpochMillis = obj.getLong("date"),
                    minCelsius = obj.getInt("min"),
                    maxCelsius = obj.getInt("max"),
                    emoji = obj.getString("emoji"),
                    description = obj.getString("description"),
                    precipitationProbabilityPercent = obj.getInt("pop"),
                    summary = obj.optString("summary", "").takeIf { it.isNotBlank() },
                    humidityPercent = obj.optInt("humidity", -1).takeIf { it >= 0 },
                    windSpeedKmh = obj.optInt("wind", -1).takeIf { it >= 0 },
                    pressureHpa = obj.optInt("pressure", -1).takeIf { it >= 0 },
                    precipitationMm = obj.optDouble("precipitation", -1.0).takeIf { it >= 0 }
                )
            }
        }.getOrNull()
    }

    private fun readHourlyCache(locationId: String): List<HourlyForecast>? {
        val json = cachePrefs.getString(KEY_HOURLY_DATA + locationId, null) ?: return null
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                HourlyForecast(
                    timeEpochMillis = obj.getLong("time"),
                    temperatureCelsius = obj.getInt("temp"),
                    emoji = obj.getString("emoji"),
                    description = obj.getString("description"),
                    precipitationProbabilityPercent = obj.getInt("pop")
                )
            }
        }.getOrNull()
    }

    private fun writeHourlyCache(locationId: String, hourly: List<HourlyForecast>) {
        val array = JSONArray()
        hourly.forEach { hour ->
            array.put(
                JSONObject()
                    .put("time", hour.timeEpochMillis)
                    .put("temp", hour.temperatureCelsius)
                    .put("emoji", hour.emoji)
                    .put("description", hour.description)
                    .put("pop", hour.precipitationProbabilityPercent)
            )
        }
        cachePrefs.edit()
            .putString(KEY_HOURLY_DATA + locationId, array.toString())
            .putLong(KEY_HOURLY_CACHED_AT + locationId, System.currentTimeMillis())
            .apply()
    }

    private fun writeForecastCache(locationId: String, forecast: List<DailyForecast>) {
        val array = JSONArray()
        forecast.forEach { day ->
            array.put(
                JSONObject()
                    .put("date", day.dateEpochMillis)
                    .put("min", day.minCelsius)
                    .put("max", day.maxCelsius)
                    .put("emoji", day.emoji)
                    .put("description", day.description)
                    .put("pop", day.precipitationProbabilityPercent)
                    .put("summary", day.summary ?: "")
                    .put("humidity", day.humidityPercent ?: -1)
                    .put("wind", day.windSpeedKmh ?: -1)
                    .put("pressure", day.pressureHpa ?: -1)
                    .put("precipitation", day.precipitationMm ?: -1.0)
            )
        }
        cachePrefs.edit()
            .putString(KEY_FORECAST_DATA + locationId, array.toString())
            .putLong(KEY_FORECAST_CACHED_AT + locationId, System.currentTimeMillis())
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
        const val KEY_FORECAST_DATA = "forecast_data"
        const val KEY_FORECAST_CACHED_AT = "forecast_cached_at"
        const val KEY_HOURLY_DATA = "hourly_data"
        const val KEY_HOURLY_CACHED_AT = "hourly_cached_at"
    }
}
