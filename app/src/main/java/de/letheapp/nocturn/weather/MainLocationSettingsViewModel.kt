package de.letheapp.nocturn.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Einstellungen für den Hauptort (automatische Standortermittlung vs. manuell gewählter Ort,
 * Live-Standort-Schalter) — bisher Teil des Wecker-Einstellungen-Screens, jetzt eigener Screen,
 * erreichbar über die Orte-Liste im Wetter-Screen (3-Punkte-Menü → "Ort" → erster Eintrag).
 */
@HiltViewModel
class MainLocationSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    val manualLocation: StateFlow<AppPreferences.ManualLocation?> = appPreferences.manualLocation
    val liveLocationEnabled: StateFlow<Boolean> = appPreferences.liveLocationEnabled

    private val _locationQuery = MutableStateFlow("")
    val locationQuery: StateFlow<String> = _locationQuery

    private val _suggestions = MutableStateFlow<List<GeoSuggestion>>(emptyList())
    val suggestions: StateFlow<List<GeoSuggestion>> = _suggestions

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    init {
        viewModelScope.launch {
            _locationQuery
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { text ->
                    if (text.length < 2) {
                        _suggestions.value = emptyList()
                        _searching.value = false
                        return@collectLatest
                    }
                    _searching.value = true
                    _suggestions.value = weatherRepository.searchLocations(text)
                    _searching.value = false
                }
        }
    }

    fun setLiveLocationEnabled(enabled: Boolean) {
        appPreferences.setLiveLocationEnabled(enabled)
        weatherRepository.clearCache()
    }

    fun onLocationQueryChange(text: String) {
        _locationQuery.value = text
    }

    fun selectLocation(suggestion: GeoSuggestion) {
        appPreferences.setManualLocation(
            AppPreferences.ManualLocation(suggestion.displayName, suggestion.latitude, suggestion.longitude)
        )
        weatherRepository.clearCache()
        _locationQuery.value = ""
        _suggestions.value = emptyList()
    }

    fun useAutomaticLocation() {
        appPreferences.clearManualLocation()
        weatherRepository.clearCache()
        _locationQuery.value = ""
        _suggestions.value = emptyList()
    }
}
