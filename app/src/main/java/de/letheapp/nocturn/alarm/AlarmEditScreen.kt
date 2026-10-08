package de.letheapp.nocturn.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import java.time.DayOfWeek

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
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showRotationDialog by remember { mutableStateOf(false) }

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
            Text(text = "%02d:%02d".format(current.hour, current.minute))
            if (current.rotationId != null || current.timetableJobId != null) {
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
                Text(
                    text = rotationOptions.find {
                        it.rotationId == current.rotationId && current.rotationId != null ||
                            it.timetableJobId == current.timetableJobId && current.timetableJobId != null
                    }?.label ?: "Nicht mit Schicht/Stundenplan verknüpft",
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }

    if (showRotationDialog) {
        RotationLinkDialog(
            options = rotationOptions,
            selectedRotationId = current.rotationId,
            selectedTimetableJobId = current.timetableJobId,
            onSelect = { option ->
                alarm = current.copy(rotationId = option?.rotationId, timetableJobId = option?.timetableJobId)
                showRotationDialog = false
            },
            onDismiss = { showRotationDialog = false }
        )
    }
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
