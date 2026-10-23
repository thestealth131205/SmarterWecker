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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 *
 * Bei mehreren angelegten Orten ([WeatherViewModel.locations]) lässt sich per Links/Rechts-
 * Wischen zwischen ihnen blättern ([HorizontalPager]) — der Hauptort steht dabei immer an erster
 * Stelle. Das 3-Punkte-Menü oben rechts übernimmt die beiden Einträge, die zuvor im Wecker-
 * Einstellungen-Screen lagen: "Wetter" (Opt-in-Schalter) und "Ort" (öffnet [onOpenLocations]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onSelectTab: (NocturnTab) -> Unit,
    onOpenLocations: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val locations by viewModel.locations.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val weatherEnabled by viewModel.weatherEnabled.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { locations.size })

    LaunchedEffect(pagerState.currentPage, locations) {
        locations.getOrNull(pagerState.currentPage)?.let { viewModel.ensureLoaded(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wetter") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Wetter") },
                            leadingIcon = { Icon(Icons.Filled.WbSunny, contentDescription = null) },
                            trailingIcon = {
                                Switch(checked = weatherEnabled, onCheckedChange = viewModel::setWeatherEnabled)
                            },
                            onClick = { viewModel.setWeatherEnabled(!weatherEnabled) }
                        )
                        DropdownMenuItem(
                            text = { Text("Ort") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenLocations()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NocturnBottomBar(selected = NocturnTab.WETTER, onSelect = onSelectTab)
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (locations.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    locations.forEachIndexed { index, location ->
                        val isSelected = index == pagerState.currentPage
                        Text(
                            location.shortName,
                            style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val location = locations.getOrNull(page) ?: return@HorizontalPager
                WeatherPageContent(
                    page = pages[location.id] ?: WeatherPageState(),
                    locationName = location.shortName.takeIf { location.name != "Automatisch" },
                    locationId = location.id,
                    viewModel = viewModel
                )
            }
        }
    }
}

/** Der Teil des [WeatherLocationEntry.name] vor dem ersten Komma (z. B. "Werne" statt "Werne, North Rhine-Westphalia, DE"). */
private val WeatherLocationEntry.shortName: String
    get() = name.substringBefore(",").trim().ifBlank { name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherPageContent(
    page: WeatherPageState,
    locationName: String?,
    locationId: String,
    viewModel: WeatherViewModel
) {
    var selectedDay by remember { mutableStateOf<DailyForecast?>(null) }

    PullToRefreshBox(
        isRefreshing = page.loading,
        onRefresh = { viewModel.load(locationId) },
        modifier = Modifier.fillMaxSize()
    ) {
    when {
        page.weather != null -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CurrentWeatherCard(page.weather!!, locationName) }
            if (page.hourly.isNotEmpty()) {
                item {
                    Text(
                        "Heute stündlich",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item { HourlyForecastCard(page.hourly) }
            }
            item {
                Text(
                    "Vorhersage",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (page.forecast.isEmpty()) {
                item {
                    Column {
                        Text(
                            "8-Tage-Vorhersage aktuell nicht verfügbar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (page.forecastError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Fehlergrund: ${page.forecastError}",
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
                            page.forecast.forEachIndexed { index, day ->
                                ForecastRow(day, onClick = { selectedDay = day })
                                if (index != page.forecast.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
        page.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
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
        DayDetailDialog(
            day = day,
            locationId = locationId,
            viewModel = viewModel,
            onDismiss = { selectedDay = null }
        )
    }
}

@Composable
private fun CurrentWeatherCard(weather: DetailedWeather, locationName: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bevorzugt den selbst gewählten/gesuchten Ortsnamen statt des von OpenWeatherMap
            // zurückgegebenen "name"-Felds, das für dieselben Koordinaten auf einen anderen,
            // oft kleineren/unbekannteren Ort (nächstgelegener Eintrag in OWMs eigener
            // Ortsdatenbank) zeigen kann als den tatsächlich gesuchten/gemeinten Ort.
            val displayName = locationName?.takeIf { it.isNotBlank() } ?: weather.cityName
            if (displayName.isNotBlank()) {
                Text(displayName, style = MaterialTheme.typography.titleMedium)
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
private fun DayDetailDialog(
    day: DailyForecast,
    locationId: String,
    viewModel: WeatherViewModel,
    onDismiss: () -> Unit
) {
    var hourly by remember(day.dateEpochMillis) { mutableStateOf<List<HourlyForecast>>(emptyList()) }
    var hourlyLoading by remember(day.dateEpochMillis) { mutableStateOf(true) }

    LaunchedEffect(day.dateEpochMillis, locationId) {
        hourlyLoading = true
        hourly = viewModel.hourlyForDay(locationId, day.dateEpochMillis)
        hourlyLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
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

                if (hourlyLoading) {
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator()
                } else if (hourly.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Stündlich",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(hourly) { hour -> HourlyColumn(hour, isNow = false) }
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
