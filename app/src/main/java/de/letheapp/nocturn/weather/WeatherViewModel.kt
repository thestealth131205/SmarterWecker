package de.letheapp.nocturn.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _weather = MutableStateFlow<DetailedWeather?>(null)
    val weather: StateFlow<DetailedWeather?> = _weather

    private val _forecast = MutableStateFlow<List<DailyForecast>>(emptyList())
    val forecast: StateFlow<List<DailyForecast>> = _forecast

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _weather.value = weatherRepository.currentWeather()
            _forecast.value = weatherRepository.dailyForecast().orEmpty()
            _loading.value = false
        }
    }
}
