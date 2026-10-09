package de.letheapp.nocturn.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Wetter-Detailansicht, erreichbar über das Wettersymbol in der Titelleiste (nur sichtbar, wenn
 * Wetter in den Einstellungen aktiviert ist). Zeigt, was der kostenlose OpenWeatherMap
 * "Current Weather Data"-Endpoint hergibt (siehe [WeatherRepository]) — keine stündliche/mehr-
 * tägige Vorhersage, Wetterkarte, Luftqualität oder Pollenwerte, da die dafür nötigen
 * OpenWeatherMap-Endpoints (One Call, Air Pollution) ein kostenpflichtiges Abo erfordern.
 */
@Composable
fun WeatherDetailDialog(weather: DetailedWeather?, isLoading: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    weather != null -> WeatherDetailContent(weather)
                    isLoading -> {
                        CircularProgressIndicator()
                        Text(
                            "Wetter wird geladen …",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            "Wetter aktuell nicht verfügbar. Standort-Berechtigung und " +
                                "Internetverbindung prüfen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherDetailContent(weather: DetailedWeather) {
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

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(epochMillis: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
