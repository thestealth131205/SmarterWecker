package de.letheapp.nocturn.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.letheapp.nocturn.monitoring.READ_STEPS_PERMISSION

/**
 * Berechtigungen-Onboarding (CLAUDE.md Kernfunktion 10): wird beim allerersten Start vor der
 * Alarmliste gezeigt, ist aber auch jederzeit später wieder erreichbar (z. B. über einen Banner
 * in der Alarmliste), da nicht erteilte Berechtigungen die jeweilige Zusatzfunktion dauerhaft
 * einschränken, nicht nur beim ersten Start relevant sind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    isFirstRun: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refreshKey++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Berechtigungen") },
                navigationIcon = {
                    if (!isFirstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = "NocTurn braucht diese Berechtigungen, um zuverlässig zu klingeln und " +
                    "zu erkennen, ob du wirklich aufgestanden bist.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PermissionRow(
                        icon = Icons.Filled.Alarm,
                        title = "Exakte Alarme",
                        explanation = "Damit der Wecker zuverlässig genau zur eingestellten Zeit klingelt.",
                        granted = remember(refreshKey) {
                            ContextCompat.getSystemService(context, android.app.AlarmManager::class.java)
                                ?.canScheduleExactAlarms() ?: true
                        },
                        onRequest = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.Notifications,
                        title = "Benachrichtigungen",
                        explanation = "Für die Alarm- und Notfall-Anzeige auf dem Sperrbildschirm.",
                        granted = remember(refreshKey) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        },
                        onRequest = { runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                    )
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.CameraAlt,
                        title = "Kamera",
                        explanation = "Für den Foto-Import von Stundenplan/Schichtplan per Texterkennung.",
                        granted = remember(refreshKey) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        },
                        onRequest = { runtimePermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
                    )
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.DirectionsRun,
                        title = "Aktivitätserkennung (Bewegung)",
                        explanation = "Für die Aufsteh-Erkennung über den Schrittzähler des Geräts, " +
                            "falls keine Smartwatch mit Health Connect verbunden ist. Die Bewegungserkennung " +
                            "über Beschleunigungs-/Gyroskop-Sensor braucht keine Berechtigung.",
                        granted = remember(refreshKey) {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACTIVITY_RECOGNITION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        },
                        onRequest = {
                            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                        }
                    )
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.Cake,
                        title = "Kontakte",
                        explanation = "Um Geburtstage deiner Kontakte automatisch im Kalender anzuzeigen " +
                            "(mit Namen und, falls vorhanden, Kontaktbild). Die Daten verlassen dein Gerät nicht.",
                        granted = remember(refreshKey) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        },
                        onRequest = { runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
                    )
                }
                item {
                    val healthConnectAvailable = remember {
                        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
                    }
                    var healthConnectGranted by remember { mutableStateOf(false) }
                    LaunchedEffect(refreshKey, healthConnectAvailable) {
                        if (healthConnectAvailable) {
                            healthConnectGranted = runCatching {
                                HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                            }.getOrDefault(emptySet()).contains(READ_STEPS_PERMISSION)
                        }
                    }
                    if (healthConnectAvailable) {
                        PermissionRow(
                            icon = Icons.Filled.Favorite,
                            title = "Health Connect",
                            explanation = "Für die Aufsteh-Erkennung über Schrittdaten, z. B. von einer Smartwatch.",
                            granted = healthConnectGranted,
                            onRequest = { healthConnectLauncher.launch(setOf(READ_STEPS_PERMISSION)) }
                        )
                    }
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.LocationOn,
                        title = "Standort (ungefähr)",
                        explanation = "Für die Wetteranzeige im Uhr-Homescreen-Widget. Es wird nur " +
                            "die letzte bekannte, ungefähre Position genutzt, keine aktive GPS-Ortung.",
                        granted = remember(refreshKey) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        },
                        onRequest = { runtimePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) }
                    )
                }
                item {
                    PermissionRow(
                        icon = Icons.Filled.BatteryAlert,
                        title = "Akku-Optimierung ignorieren",
                        explanation = "Damit die Aufsteh-Überwachung nach dem Klingeln nicht durch den " +
                            "Energiesparmodus unterbrochen wird.",
                        granted = remember(refreshKey) {
                            val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
                            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                        },
                        onRequest = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                }
            }

            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(if (isFirstRun) "Weiter" else "Fertig")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    explanation: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            if (granted) {
                Text(
                    "Erteilt",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                TextButton(onClick = onRequest) { Text("Erlauben") }
            }
        }
    }
}
