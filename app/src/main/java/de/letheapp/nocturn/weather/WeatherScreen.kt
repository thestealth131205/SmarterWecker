package de.letheapp.nocturn.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
 * ([WeatherRepository.dailyForecast], OpenWeatherMap One Call 3.0). Arbeitet als eigenständiger
 * Screen auf Ebene der unteren Tab-Leiste (wie die Kalender-Monatsansicht) — kein Zurück-Pfeil,
 * da er direkt über die Tab-Leiste bzw. das Wettersymbol/die Widget-Tap-Zone erreicht wird.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onSelectTab: (NocturnTab) -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val weather by viewModel.weather.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val hourly by viewModel.hourly.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val forecastError by viewModel.forecastError.collectAsState()
    var selectedDay by remember { mutableStateOf<DailyForecast?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wetter") },
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
                                    ForecastRow(day, onClick = { selectedDay = day })
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

    selectedDay?.let { day ->
        DayDetailDialog(day = day, onDismiss = { selectedDay = null })
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(hourly) { index, hour ->
                HourlyColumn(hour, isNow = index == 0)
            }
        }
    }
}

@Composable
private fun HourlyColumn(hour: HourlyForecast, isNow: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Text("${hour.temperatureCelsius}°", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(hour.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        if (hour.precipitationProbabilityPercent > 0) {
            Text(
                "${hour.precipitationProbabilityPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (isNow) "Jetzt" else formatHourLabel(hour.timeEpochMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ForecastRow(day: DailyForecast, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
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
private fun DayDetailDialog(day: DailyForecast, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(formatFullDayLabel(day.dateEpochMillis), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(day.emoji, style = MaterialTheme.typography.displayMedium)
                Text(day.description, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Max ${day.maxCelsius}°", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Min ${day.minCelsius}°",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (day.summary != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        day.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WeatherStat("Regen", "${day.precipitationProbabilityPercent} %")
                    if (day.humidityPercent != null) {
                        WeatherStat("Luftfeuchtigkeit", "${day.humidityPercent} %")
                    }
                    if (day.windSpeedKmh != null) {
                        WeatherStat("Wind", "${day.windSpeedKmh} km/h")
                    }
                }

                if (day.pressureHpa != null || (day.precipitationMm != null && day.precipitationMm > 0)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (day.pressureHpa != null) {
                            WeatherStat("Luftdruck", "${day.pressureHpa} hPa")
                        }
                        if (day.precipitationMm != null && day.precipitationMm > 0) {
                            WeatherStat("Niederschlag", "%.1f mm".format(day.precipitationMm))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
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

private fun formatFullDayLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
        .replaceFirstChar { it.uppercase() }
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    val prefix = if (date == today) "Heute, " else ""
    return "$prefix$weekday, ${date.dayOfMonth}. $month"
}
