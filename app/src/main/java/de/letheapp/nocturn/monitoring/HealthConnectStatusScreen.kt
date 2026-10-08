package de.letheapp.nocturn.monitoring

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private enum class HealthConnectAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/**
 * Health-Connect-Verbindungsstatus, erreichbar über das 3-Punkte-Menü der Alarmliste
 * (Punkt "Status"). Analog zur gleichnamigen Sektion im Iosync-Projekt: Status-Karte mit
 * Ampel-Punkt plus "Status prüfen"/"Berechtigungen"-Buttons, hier reduziert auf den einen
 * Datentyp (Schritte), den [HealthConnectMovementMonitor] für die Aufsteh-Erkennung braucht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var availability by remember { mutableStateOf(HealthConnectAvailability.UNAVAILABLE) }
    var loading by remember { mutableStateOf(true) }
    var stepsGranted by remember { mutableStateOf(false) }
    var stepsLast24h by remember { mutableStateOf<Long?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            availability = when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
                else -> HealthConnectAvailability.UNAVAILABLE
            }

            if (availability == HealthConnectAvailability.AVAILABLE) {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = runCatching { client.permissionController.getGrantedPermissions() }
                    .getOrDefault(emptySet())
                stepsGranted = READ_STEPS_PERMISSION in granted
                stepsLast24h = if (stepsGranted) {
                    runCatching {
                        val now = Instant.now()
                        client.aggregate(
                            AggregateRequest(
                                metrics = setOf(StepsRecord.COUNT_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
                            )
                        )[StepsRecord.COUNT_TOTAL]
                    }.getOrNull()
                } else {
                    null
                }
            } else {
                stepsGranted = false
                stepsLast24h = null
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect Verbindung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Health Connect verbindet Google Fit, Samsung Health und andere Gesundheits-Apps. " +
                    "NocTurn nutzt die Schrittdaten daraus, um nach dem Klingeln zu erkennen, ob du " +
                    "wirklich aufgestanden und losgelaufen bist.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (dotColor, label) = when {
                            loading -> Color(0xFFFF9800) to "Wird geprüft…"
                            availability == HealthConnectAvailability.AVAILABLE -> Color(0xFF4CAF50) to "Verfügbar"
                            availability == HealthConnectAvailability.UPDATE_REQUIRED -> Color(0xFFFF9800) to "Update erforderlich"
                            else -> Color(0xFFF44336) to "Nicht verfügbar"
                        }
                        StatusDot(dotColor)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (!loading && availability != HealthConnectAvailability.AVAILABLE) {
                Text(
                    "Health Connect ist auf diesem Gerät nicht installiert oder muss aktualisiert werden. " +
                        "Ohne Health Connect läuft die Aufsteh-Erkennung nur über die Sensor-Fallback-Erkennung " +
                        "direkt am Smartphone (Schrittzähler/Beschleunigungssensor).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = {
                        val marketUri = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
                        } catch (_: ActivityNotFoundException) {
                            val webUri = Uri.parse(
                                "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Health Connect installieren") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { refresh() },
                    modifier = Modifier.weight(1f),
                    enabled = !loading
                ) { Text("Status prüfen") }

                Button(
                    onClick = { permissionLauncher.launch(setOf(READ_STEPS_PERMISSION)) },
                    modifier = Modifier.weight(1f),
                    enabled = availability == HealthConnectAvailability.AVAILABLE
                ) { Text("Berechtigungen") }
            }

            if (availability == HealthConnectAvailability.AVAILABLE) {
                Text("Verfügbare Datentypen", style = MaterialTheme.typography.labelMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Schritte", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when {
                                    !stepsGranted -> "Keine Berechtigung erteilt"
                                    stepsLast24h != null -> "$stepsLast24h Schritte in den letzten 24h"
                                    else -> "Berechtigt (keine Daten in 24h)"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        StatusDot(
                            when {
                                stepsGranted && stepsLast24h != null -> Color(0xFF4CAF50)
                                stepsGranted -> Color(0xFFFF9800)
                                else -> Color(0xFF9E9E9E)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(10.dp).background(color, CircleShape)
    )
}
