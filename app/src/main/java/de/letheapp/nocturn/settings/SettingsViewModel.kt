package de.letheapp.nocturn.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.onboarding.AppPreferences
import de.letheapp.nocturn.weather.GeoSuggestion
import de.letheapp.nocturn.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    val weatherEnabled: StateFlow<Boolean> = appPreferences.weatherEnabled
    val manualLocation: StateFlow<AppPreferences.ManualLocation?> = appPreferences.manualLocation
    val liveLocationEnabled: StateFlow<Boolean> = appPreferences.liveLocationEnabled
    val dynamicColorEnabled: StateFlow<Boolean> = appPreferences.dynamicColorEnabled

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

    fun setWeatherEnabled(enabled: Boolean) {
        appPreferences.setWeatherEnabled(enabled)
    }

    fun setLiveLocationEnabled(enabled: Boolean) {
        appPreferences.setLiveLocationEnabled(enabled)
        weatherRepository.clearCache()
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        appPreferences.setDynamicColorEnabled(enabled)
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
