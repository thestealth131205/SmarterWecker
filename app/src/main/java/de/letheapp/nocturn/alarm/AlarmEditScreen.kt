package de.letheapp.nocturn.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.iobroker.IoBrokerActionPickerDialog
import de.letheapp.nocturn.iobroker.ioBrokerAction1
import de.letheapp.nocturn.iobroker.ioBrokerAction2
import de.letheapp.nocturn.iobroker.ioBrokerScriptAction
import de.letheapp.nocturn.iobroker.summaryLabel
import de.letheapp.nocturn.iobroker.withIoBrokerAction1
import de.letheapp.nocturn.iobroker.withIoBrokerAction2
import de.letheapp.nocturn.iobroker.withIoBrokerScriptAction
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private val WEEKDAY_FULL_NAMES = listOf(
    "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long,
    pendingSoundUri: String?,
    onPickSound: (currentUri: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    val rotationOptions by viewModel.rotationOptions.collectAsState()
    val allShifts by viewModel.allShifts.collectAsState()
    val ioBrokerConnected by viewModel.ioBrokerConnected.collectAsState()
    val ioBrokerDataPoints by viewModel.ioBrokerDataPoints.collectAsState()
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showRotationDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showIoBrokerDialog by remember { mutableStateOf<Int?>(null) }
    var showShiftNarrowDialog by remember { mutableStateOf(false) }
    var showWeekdayNarrowDialog by remember { mutableStateOf(false) }
    var pendingRotationId by remember { mutableStateOf<Long?>(null) }
    var pendingTimetableJobId by remember { mutableStateOf<Long?>(null) }
    var shiftNarrowOptions by remember { mutableStateOf<List<ShiftEntity>>(emptyList()) }
    var weekdayNarrowOptions by remember { mutableStateOf<List<Int>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(alarms) {
        if (alarm == null) {
            alarm = alarms.find { it.id == alarmId }
        }
    }
    LaunchedEffect(pendingSoundUri) {
        if (pendingSoundUri != null) {
            alarm = alarm?.copy(soundUri = pendingSoundUri)
        }
    }

    val current = alarm ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.save(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.delete(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Alarm löschen")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            val timeEditable = current.rotationId == null && current.timetableJobId == null
            Text(
                text = "%02d:%02d".format(current.hour, current.minute),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .clickable(enabled = timeEditable) { showTimePicker = true }
            )
            if (!timeEditable) {
                Text(
                    text = "Uhrzeit folgt der Schicht/dem Stundenplan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = current.label,
                onValueChange = { alarm = current.copy(label = it) },
                label = { Text("Bezeichnung") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )

            Text("Wiederholung", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            Row {
                listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                ).forEach { day ->
                    FilterChip(
                        selected = AlarmRepeat.isSet(current.repeatDays, day),
                        onClick = { alarm = current.copy(repeatDays = AlarmRepeat.toggle(current.repeatDays, day)) },
                        label = { Text(AlarmRepeat.shortLabel(day)) },
                        enabled = current.rotationId == null && current.timetableJobId == null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
            if (current.rotationId != null || current.timetableJobId != null) {
                Text(
                    text = "Wird ignoriert, solange eine Schicht/ein Stundenplan verknüpft ist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .clickable { onPickSound(current.soundUri) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Text(
                    text = "Weckton wählen",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable { showRotationDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CalendarViewMonth, contentDescription = null)
                val baseLabel = rotationOptions.find {
                    it.rotationId == current.rotationId && current.rotationId != null ||
                        it.timetableJobId == current.timetableJobId && current.timetableJobId != null
                }?.label ?: "Nicht mit Schicht/Stundenplan verknüpft"
                val narrowSuffix = when {
                    current.linkedShiftId != null ->
                        " · " + (allShifts.find { it.id == current.linkedShiftId }?.name ?: "Schicht")
                    current.linkedTimetableWeekday != null ->
                        " · " + WEEKDAY_FULL_NAMES.getOrElse(current.linkedTimetableWeekday) { "" }
                    else -> ""
                }
                Text(
                    text = baseLabel + narrowSuffix,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable {
                        viewModel.refreshIoBrokerDataPoints()
                        showIoBrokerDialog = 1
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null)
                Text(
                    text = current.ioBrokerAction1()?.summaryLabel()?.let { "ioBroker-Datenpunkt 1 bei Notfall-Alarm: $it" }
                        ?: "ioBroker-Datenpunkt 1 bei Notfall-Alarm (z. B. Lampe)",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable {
                        viewModel.refreshIoBrokerDataPoints()
                        showIoBrokerDialog = 2
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Blinds, contentDescription = null)
                Text(
                    text = current.ioBrokerAction2()?.summaryLabel()?.let { "ioBroker-Datenpunkt 2 bei Notfall-Alarm: $it" }
                        ?: "ioBroker-Datenpunkt 2 bei Notfall-Alarm (z. B. Rollo)",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable {
                        viewModel.refreshIoBrokerDataPoints()
                        showIoBrokerDialog = 3
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null)
                Text(
                    text = current.ioBrokerScriptAction()?.summaryLabel()?.let { "Skript bei Notfall-Alarm: $it" }
                        ?: "Skript bei Notfall-Alarm auslösen (z. B. Radio)",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }

    if (showTimePicker) {
        NocturnTimePickerDialog(
            initialHour = current.hour,
            initialMinute = current.minute,
            onConfirm = { hour, minute ->
                alarm = current.copy(hour = hour, minute = minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showRotationDialog) {
        RotationLinkDialog(
            options = rotationOptions,
            selectedRotationId = current.rotationId,
            selectedTimetableJobId = current.timetableJobId,
            onSelect = { option ->
                showRotationDialog = false
                when {
                    option == null -> {
                        alarm = current.copy(
                            rotationId = null, timetableJobId = null,
                            linkedShiftId = null, linkedTimetableWeekday = null
                        )
                    }
                    option.rotationId != null -> {
                        val rotationId = option.rotationId
                        coroutineScope.launch {
                            val shifts = viewModel.rotationShiftOptions(rotationId)
                            if (shifts.size <= 1) {
                                alarm = current.copy(
                                    rotationId = rotationId, timetableJobId = null,
                                    linkedShiftId = null, linkedTimetableWeekday = null
                                )
                            } else {
                                pendingRotationId = rotationId
                                shiftNarrowOptions = shifts
                                showShiftNarrowDialog = true
                            }
                        }
                    }
                    else -> {
                        val timetableJobId = option.timetableJobId!!
                        coroutineScope.launch {
                            val weekdays = viewModel.timetableWeekdayOptions(timetableJobId)
                            if (weekdays.size <= 1) {
                                alarm = current.copy(
                                    rotationId = null, timetableJobId = timetableJobId,
                                    linkedShiftId = null, linkedTimetableWeekday = null
                                )
                            } else {
                                pendingTimetableJobId = timetableJobId
                                weekdayNarrowOptions = weekdays
                                showWeekdayNarrowDialog = true
                            }
                        }
                    }
                }
            },
            onDismiss = { showRotationDialog = false }
        )
    }

    if (showShiftNarrowDialog) {
        ShiftNarrowDialog(
            shifts = shiftNarrowOptions,
            onSelect = { shiftId ->
                alarm = current.copy(
                    rotationId = pendingRotationId, timetableJobId = null,
                    linkedShiftId = shiftId, linkedTimetableWeekday = null
                )
                showShiftNarrowDialog = false
            },
            onDismiss = { showShiftNarrowDialog = false }
        )
    }

    if (showWeekdayNarrowDialog) {
        WeekdayNarrowDialog(
            weekdays = weekdayNarrowOptions,
            onSelect = { weekday ->
                alarm = current.copy(
                    rotationId = null, timetableJobId = pendingTimetableJobId,
                    linkedShiftId = null, linkedTimetableWeekday = weekday
                )
                showWeekdayNarrowDialog = false
            },
            onDismiss = { showWeekdayNarrowDialog = false }
        )
    }

    if (showIoBrokerDialog != null) {
        val slot = showIoBrokerDialog
        IoBrokerActionPickerDialog(
            title = when (slot) {
                1 -> "ioBroker-Datenpunkt 1 bei Notfall-Alarm (z. B. Lampe)"
                2 -> "ioBroker-Datenpunkt 2 bei Notfall-Alarm (z. B. Rollo)"
                else -> "Skript bei Notfall-Alarm auslösen (z. B. Radio)"
            },
            connected = ioBrokerConnected,
            dataPoints = ioBrokerDataPoints,
            current = when (slot) {
                1 -> current.ioBrokerAction1()
                2 -> current.ioBrokerAction2()
                else -> current.ioBrokerScriptAction()
            },
            onConfirm = { config ->
                alarm = when (slot) {
                    1 -> current.withIoBrokerAction1(config)
                    2 -> current.withIoBrokerAction2(config)
                    else -> current.withIoBrokerScriptAction(config)
                }
                showIoBrokerDialog = null
            },
            onDismiss = { showIoBrokerDialog = null }
        )
    }
}

/**
 * Zweiter Schritt nach Auswahl einer Rotation in [RotationLinkDialog]: engt den Alarm optional
 * auf eine einzelne Schicht der Rotation ein (z. B. nur "Frühschicht"), statt an jede Schicht
 * der Rotation zu koppeln (siehe [AlarmEntity.linkedShiftId]).
 */
@Composable
private fun ShiftNarrowDialog(
    shifts: List<ShiftEntity>,
    onSelect: (shiftId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auf eine Schicht einengen?") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) { Text("Jede Schicht dieser Rotation") }
                shifts.forEach { shift ->
                    TextButton(onClick = { onSelect(shift.id) }) {
                        Text(shift.name, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * Zweiter Schritt nach Auswahl eines Stundenplan-Jobs in [RotationLinkDialog]: engt den Alarm
 * optional auf einen einzelnen Wochentag ein, statt an jeden Unterrichtstag zu koppeln (siehe
 * [AlarmEntity.linkedTimetableWeekday]).
 */
@Composable
private fun WeekdayNarrowDialog(
    weekdays: List<Int>,
    onSelect: (weekday: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auf einen Wochentag einengen?") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) { Text("Jeder Unterrichtstag") }
                weekdays.forEach { weekday ->
                    TextButton(onClick = { onSelect(weekday) }) {
                        Text(
                            WEEKDAY_FULL_NAMES.getOrElse(weekday) { "Tag $weekday" },
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun RotationLinkDialog(
    options: List<RotationOption>,
    selectedRotationId: Long?,
    selectedTimetableJobId: Long?,
    onSelect: (RotationOption?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mit Schicht/Stundenplan verknüpfen") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) {
                    val noneSelected = selectedRotationId == null && selectedTimetableJobId == null
                    Text(if (noneSelected) "✓ Keine Verknüpfung" else "Keine Verknüpfung")
                }
                options.forEach { option ->
                    val selected = (option.rotationId != null && option.rotationId == selectedRotationId) ||
                        (option.timetableJobId != null && option.timetableJobId == selectedTimetableJobId)
                    TextButton(onClick = { onSelect(option) }) {
                        Text(if (selected) "✓ ${option.label}" else option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}
