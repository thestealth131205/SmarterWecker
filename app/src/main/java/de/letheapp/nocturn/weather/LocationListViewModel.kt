package de.letheapp.nocturn.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.onboarding.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orte-Liste für den Wetter-Screen (3-Punkte-Menü → "Ort"): der Hauptort steht immer an erster
 * Stelle (Name kommt aus [AppPreferences.manualLocation] bzw. "Automatisch"), darunter die vom
 * Nutzer zusätzlich angelegten Orte ([AppPreferences.additionalLocations]), die sich im
 * Wetter-Screen per Wischen durchblättern lassen.
 */
@HiltViewModel
class LocationListViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    val mainLocationName: StateFlow<String> = appPreferences.manualLocation
        .map { it?.name ?: "Automatisch" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Automatisch")

    val additionalLocations: StateFlow<List<AppPreferences.SavedLocation>> = appPreferences.additionalLocations

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _suggestions = MutableStateFlow<List<GeoSuggestion>>(emptyList())
    val suggestions: StateFlow<List<GeoSuggestion>> = _suggestions

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    init {
        viewModelScope.launch {
            _searchQuery
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

    fun openAddDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
        _searchQuery.value = ""
        _suggestions.value = emptyList()
    }

    fun onSearchQueryChange(text: String) {
        _searchQuery.value = text
    }

    fun addLocation(suggestion: GeoSuggestion) {
        appPreferences.addAdditionalLocation(suggestion.displayName, suggestion.latitude, suggestion.longitude)
        dismissAddDialog()
    }

    fun removeLocation(id: String) {
        appPreferences.removeAdditionalLocation(id)
        weatherRepository.clearCache(id)
    }
}
