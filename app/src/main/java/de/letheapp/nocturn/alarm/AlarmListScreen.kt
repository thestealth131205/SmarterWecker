package de.letheapp.nocturn.alarm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.iobroker.ioBrokerAction1
import de.letheapp.nocturn.iobroker.ioBrokerAction2
import de.letheapp.nocturn.iobroker.ioBrokerScriptAction
import de.letheapp.nocturn.ui.NocturnBottomBar
import de.letheapp.nocturn.ui.NocturnTab
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onEditAlarm: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHealthConnectStatus: () -> Unit,
    onOpenIoBrokerSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenStopwatch: () -> Unit,
    onSelectTab: (NocturnTab) -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showWeekdayPicker by remember { mutableStateOf(false) }
    var pendingHour by remember { mutableIntStateOf(0) }
    var pendingMinute by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showVoiceHelpDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val voiceCommandsEnabled by viewModel.voiceCommandsEnabled.collectAsState()
    val voiceFeedback by viewModel.voiceFeedback.collectAsState()
    val speechRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            viewModel.handleVoiceCommand(spokenText)
        }
    }

    LaunchedEffect(voiceFeedback) {
        voiceFeedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearVoiceFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NocTurn") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Wetter/Kalender sind bereits über die untere Tab-Leiste erreichbar — hier
                    // stattdessen Stoppuhr/Timer, die (anders als Wecker/Wetter/Kalender) keine
                    // eigenen Tabs bekommen, aber genauso schnell zugänglich sein sollen.
                    if (voiceCommandsEnabled) {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Timer, Stoppuhr oder Wecker per Sprache stellen …")
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                speechRecognitionLauncher.launch(intent)
                            } else {
                                Toast.makeText(context, "Keine Spracherkennung auf diesem Gerät verfügbar.", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Sprachbefehl")
                        }
                    }
                    IconButton(onClick = onOpenStopwatch) {
                        Icon(Icons.Filled.AvTimer, contentDescription = "Stoppuhr")
                    }
                    IconButton(onClick = onOpenTimer) {
                        Icon(Icons.Filled.Timer, contentDescription = "Timer")
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Status") },
                            leadingIcon = { Icon(Icons.Filled.MonitorHeart, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenHealthConnectStatus()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Berechtigungen") },
                            leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenPermissions()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Smart-Home (ioBroker)") },
                            leadingIcon = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenIoBrokerSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Einstellungen") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Spracherkennung")
                                    Switch(
                                        checked = voiceCommandsEnabled,
                                        onCheckedChange = { viewModel.setVoiceCommandsEnabled(it) }
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showVoiceHelpDialog = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTimePicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Alarm hinzufügen")
            }
        },
        bottomBar = {
            NocturnBottomBar(selected = NocturnTab.WECKER, onSelect = onSelectTab)
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ExactAlarmPermissionBanner()

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Noch keine Alarme")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        val isAutoShiftWakeup = alarm.autoShiftWakeupEpochDay != null
                        val editClick: (() -> Unit)? = if (isAutoShiftWakeup) null else ({ onEditAlarm(alarm.id) })
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                            // Automatisch verwaltete "Nächste Schicht"-Alarme haben keinen eigenen
                            // Bearbeiten-Screen — Uhrzeit/Datum kommen aus der verknüpften Schicht.
                            onClick = editClick
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val now = java.time.LocalTime.now()
        NocturnTimePickerDialog(
            initialHour = now.hour,
            initialMinute = now.minute,
            onConfirm = { hour, minute ->
                pendingHour = hour
                pendingMinute = minute
                showTimePicker = false
                showWeekdayPicker = true
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showWeekdayPicker) {
        WeekdayPickerDialog(
            onConfirm = { repeatDays ->
                viewModel.createAlarm(pendingHour, pendingMinute, repeatDays)
                showWeekdayPicker = false
            },
            onDismiss = { showWeekdayPicker = false }
        )
    }

    if (showVoiceHelpDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceHelpDialog = false },
            title = { Text("Sprachbefehle") },
            text = {
                Column {
                    Text(
                        "Tippe oben auf das Mikrofon-Symbol und sage einen der folgenden Befehle. " +
                            "Die Spracherkennung läuft komplett über Android selbst, ohne eigenen Server.",
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text("„Stelle einen Timer auf 10 Minuten“", style = MaterialTheme.typography.bodyMedium)
                    Text("„Starte/Stoppe die Stoppuhr“ bzw. „Setze die Stoppuhr zurück“", style = MaterialTheme.typography.bodyMedium)
                    Text("„Wecke mich um 6 Uhr“", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceHelpDialog = false }) { Text("Verstanden") }
            }
        )
    }

}

@Composable
private fun WeekdayPickerDialog(onConfirm: (repeatDays: Int) -> Unit, onDismiss: () -> Unit) {
    var repeatDays by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wiederholung") },
        text = {
            Column {
                Text(
                    text = AlarmRepeat.describe(repeatDays),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row {
                    listOf(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                    ).forEach { day ->
                        FilterChip(
                            selected = AlarmRepeat.isSet(repeatDays, day),
                            onClick = { repeatDays = AlarmRepeat.toggle(repeatDays, day) },
                            label = { Text(AlarmRepeat.shortLabel(day)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(repeatDays) }) { Text("Alarm anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun AlarmCard(alarm: AlarmEntity, onToggle: (Boolean) -> Unit, onClick: (() -> Unit)?) {
    val accentColor = if (alarm.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val contentAlpha = if (alarm.enabled) 1f else 0.6f
    val autoShiftWakeupEpochDay = alarm.autoShiftWakeupEpochDay
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (alarm.enabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp, 40.dp)
                    .background(accentColor, RoundedCornerShape(4.dp))
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%02d:%02d".format(alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IoBrokerActionStatusIcons(alarm = alarm, contentAlpha = contentAlpha)
                }
                Text(
                    text = when {
                        autoShiftWakeupEpochDay != null -> {
                            val date = java.time.LocalDate.ofEpochDay(autoShiftWakeupEpochDay)
                            val dateLabel = date.format(
                                java.time.format.DateTimeFormatter.ofPattern("EEE, d. MMM", java.util.Locale.GERMAN)
                            )
                            "Automatisch · ${alarm.label} · $dateLabel"
                        }
                        alarm.label.isNotBlank() -> "${alarm.label} · ${AlarmRepeat.describe(alarm.repeatDays)}"
                        else -> AlarmRepeat.describe(alarm.repeatDays)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * Drei kleine Symbole neben der Uhrzeit, die zeigen, ob für diesen Alarm die beiden frei
 * wählbaren ioBroker-Datenpunkte (z. B. Lampe/Rollo) und der Skript-Datenpunkt (z. B. Radio)
 * konfiguriert sind: Akzentfarbe wenn gesetzt, grau wenn nicht.
 */
@Composable
private fun IoBrokerActionStatusIcons(alarm: AlarmEntity, contentAlpha: Float) {
    val setColor = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
    val unsetColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (alarm.ioBrokerAction1() != null) setColor else unsetColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Filled.Blinds,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (alarm.ioBrokerAction2() != null) setColor else unsetColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Filled.SettingsInputAntenna,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (alarm.ioBrokerScriptAction() != null) setColor else unsetColor
        )
    }
}

@Composable
private fun ExactAlarmPermissionBanner() {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(android.app.AlarmManager::class.java) }
    var granted by remember { mutableStateOf(alarmManager?.canScheduleExactAlarms() ?: true) }

    if (!granted) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Für pünktliches Klingeln wird die Berechtigung für exakte Alarme benötigt.",
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                    granted = alarmManager?.canScheduleExactAlarms() ?: true
                }) {
                    Text("Erlauben")
                }
            }
        }
    }
}
