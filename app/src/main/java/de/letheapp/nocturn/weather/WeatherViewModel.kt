package de.letheapp.nocturn.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Eintrag in der Orte-Liste des Wetter-Screens/der Seitenblätterung — Hauptort oder zusätzlicher Ort. */
data class WeatherLocationEntry(val id: String, val name: String)

/** Geladener Zustand einer einzelnen Orts-"Seite" im Wetter-Screen. */
data class WeatherPageState(
    val weather: DetailedWeather? = null,
    val forecast: List<DailyForecast> = emptyList(),
    val hourly: List<HourlyForecast> = emptyList(),
    val loading: Boolean = true,
    val forecastError: String? = null
)

/**
 * Lädt Wetterdaten für mehrere Orte gleichzeitig (Hauptort + zusätzliche, siehe
 * [AppPreferences.additionalLocations]) — im Wetter-Screen per Links/Rechts-Wischen
 * ([androidx.compose.foundation.pager.HorizontalPager]) durchblätterbar. Jede Seite wird erst
 * beim ersten Anzeigen geladen ([ensureLoaded]) und danach pro Ort separat im Speicher gehalten,
 * damit ein Seitenwechsel nicht jedes Mal einen neuen Netzwerk-Request auslöst.
 */
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val weatherEnabled: StateFlow<Boolean> = appPreferences.weatherEnabled

    fun setWeatherEnabled(enabled: Boolean) {
        appPreferences.setWeatherEnabled(enabled)
        if (enabled) {
            _pages.value.keys.forEach { load(it) }
        }
    }

    val locations: StateFlow<List<WeatherLocationEntry>> = combine(
        appPreferences.manualLocation, appPreferences.additionalLocations
    ) { manual, additional ->
        val main = WeatherLocationEntry(WeatherRepository.MAIN_LOCATION_ID, manual?.name ?: "Automatisch")
        listOf(main) + additional.map { WeatherLocationEntry(it.id, it.name) }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        listOf(WeatherLocationEntry(WeatherRepository.MAIN_LOCATION_ID, "Automatisch"))
    )

    private val _pages = MutableStateFlow<Map<String, WeatherPageState>>(emptyMap())
    val pages: StateFlow<Map<String, WeatherPageState>> = _pages

    fun pageFor(locationId: String): WeatherPageState = _pages.value[locationId] ?: WeatherPageState()

    /** Lädt die Seite nur, wenn sie noch nicht im Speicher ist — wird bei jedem Seitenwechsel aufgerufen. */
    fun ensureLoaded(locationId: String) {
        if (_pages.value.containsKey(locationId)) return
        load(locationId)
    }

    fun load(locationId: String) {
        viewModelScope.launch {
            setPage(locationId, pageFor(locationId).copy(loading = true))
            val weather = weatherRepository.currentWeather(locationId)
            val forecast = weatherRepository.dailyForecast(locationId).orEmpty()
            val forecastError = weatherRepository.lastForecastError
            val hourly = weatherRepository.hourlyForecastToday(locationId).orEmpty()
            setPage(locationId, WeatherPageState(weather, forecast, hourly, loading = false, forecastError = forecastError))
        }
    }

    private fun setPage(id: String, state: WeatherPageState) {
        _pages.value = _pages.value + (id to state)
    }

    /** Stündliche Vorhersage für einen einzelnen Tag der 8-Tage-Vorhersage, für [DayDetailDialog]. */
    suspend fun hourlyForDay(locationId: String, dayEpochMillis: Long): List<HourlyForecast> =
        weatherRepository.hourlyForecastForDay(locationId, dayEpochMillis).orEmpty()
}
