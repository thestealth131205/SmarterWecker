package de.letheapp.nocturn.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.ui.NocturnBottomBar
import de.letheapp.nocturn.ui.NocturnTab
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Eigener Wetter-Screen (statt des früheren Dialogs): aktuelle Bedingungen
 * ([WeatherRepository.currentWeather]) plus 8-Tage-Vorhersage
 * ([WeatherRepository.dailyForecast], OpenWeatherMap One Call 3.0). Erreichbar über das
 * Wettersymbol in der Alarmliste und über die Wetter-Tap-Zone des Uhr-Widgets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onBack: () -> Unit,
    onSelectTab: (NocturnTab) -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val weather by viewModel.weather.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val hourly by viewModel.hourly.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val forecastError by viewModel.forecastError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wetter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NocturnBottomBar(selected = NocturnTab.WETTER, onSelect = onSelectTab)
        }
    ) { innerPadding ->
        when {
            weather != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { CurrentWeatherCard(weather!!) }
                if (hourly.isNotEmpty()) {
                    item {
                        Text(
                            "Heute stündlich",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    item { HourlyForecastCard(hourly) }
                }
                item {
                    Text(
                        "Vorhersage",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                if (forecast.isEmpty()) {
                    item {
                        Column {
                            Text(
                                "8-Tage-Vorhersage aktuell nicht verfügbar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (forecastError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Fehlergrund: $forecastError",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Card {
                            Column {
                                forecast.forEachIndexed { index, day ->
                                    ForecastRow(day)
                                    if (index != forecast.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            else -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Wetter aktuell nicht verfügbar. Standort-Berechtigung und " +
                        "Internetverbindung prüfen.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CurrentWeatherCard(weather: DetailedWeather) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (weather.cityName.isNotBlank()) {
                Text(weather.cityName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(weather.emoji, style = MaterialTheme.typography.displayMedium)
            Text("${weather.temperatureCelsius}°", style = MaterialTheme.typography.displayLarge)
            Text(weather.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Gefühlt ${weather.feelsLikeCelsius}° · Max ${weather.tempMaxCelsius}° · Min ${weather.tempMinCelsius}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WeatherStat("Luftfeuchtigkeit", "${weather.humidityPercent} %")
                WeatherStat("Wind", "${weather.windSpeedKmh} km/h")
                WeatherStat("Luftdruck", "${weather.pressureHpa} hPa")
            }

            if (weather.sunriseEpochMillis > 0 && weather.sunsetEpochMillis > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WeatherStat("Sonnenaufgang", formatTime(weather.sunriseEpochMillis))
                    WeatherStat("Sonnenuntergang", formatTime(weather.sunsetEpochMillis))
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(hourly: List<HourlyForecast>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
        ) {
            itemsIndexed(hourly) { index, hour ->
                HourlyRow(hour)
                if (index != hourly.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HourlyRow(hour: HourlyForecast) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatHourLabel(hour.timeEpochMillis),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(56.dp)
        )
        Text(hour.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(36.dp))
        Text(
            hour.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (hour.precipitationProbabilityPercent > 0) {
            Text(
                "💧${hour.precipitationProbabilityPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text("${hour.temperatureCelsius}°", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ForecastRow(day: DailyForecast) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatDayLabel(day.dateEpochMillis),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(72.dp)
        )
        Text(day.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(36.dp))
        Text(
            day.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (day.precipitationProbabilityPercent > 0) {
            Text(
                "💧${day.precipitationProbabilityPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text("${day.maxCelsius}°", style = MaterialTheme.typography.bodyLarge)
        Text(
            "  ${day.minCelsius}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(epochMillis: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun formatHourLabel(epochMillis: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun formatDayLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    if (date == today) return "Heute"
    return date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        .replaceFirstChar { it.uppercase() } + ". " + date.dayOfMonth + "."
}
