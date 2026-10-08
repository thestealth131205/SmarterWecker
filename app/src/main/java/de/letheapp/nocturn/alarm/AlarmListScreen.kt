package de.letheapp.nocturn.alarm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
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
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onEditAlarm: (Long) -> Unit,
    onOpenCalendarTemplates: () -> Unit,
    onOpenCalendarMonth: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenNextcloudSync: () -> Unit,
    onOpenHolidaySettings: () -> Unit,
    onOpenHealthConnectStatus: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showWeekdayPicker by remember { mutableStateOf(false) }
    var pendingHour by remember { mutableIntStateOf(0) }
    var pendingMinute by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NocTurn") },
                actions = {
                    IconButton(onClick = onOpenCalendarMonth) {
                        Icon(Icons.Filled.CalendarViewMonth, contentDescription = "Kalender")
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Wecker") },
                            onClick = { showOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Status") },
                            leadingIcon = { Icon(Icons.Filled.MonitorHeart, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenHealthConnectStatus()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kalender") },
                            onClick = {
                                showOverflowMenu = false
                                onOpenCalendarMonth()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kalender-Vorlagen") },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenCalendarTemplates()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ferien & Feiertage") },
                            leadingIcon = { Icon(Icons.Filled.BeachAccess, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenHolidaySettings()
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
                            text = { Text("Nextcloud-Sync") },
                            leadingIcon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenNextcloudSync()
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
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                            onClick = { onEditAlarm(alarm.id) }
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
private fun AlarmCard(alarm: AlarmEntity, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp, 40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = "%02d:%02d".format(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (alarm.label.isNotBlank()) {
                        "${alarm.label} · ${AlarmRepeat.describe(alarm.repeatDays)}"
                    } else {
                        AlarmRepeat.describe(alarm.repeatDays)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
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
