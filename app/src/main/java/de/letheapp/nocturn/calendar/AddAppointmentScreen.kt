package de.letheapp.nocturn.calendar

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import de.letheapp.nocturn.contacts.ContactEmailSuggestion
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.db.ReminderChannel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Termin-Formular: sowohl für den Bestätigungsscreen, der erscheint, wenn NocTurn aus einer
 * Fremd-App (z. B. Kinoticket, Buchungsbestätigung) heraus über den "Zum Kalender
 * hinzufügen"-Dialog von Android als Kalender-App ausgewählt wurde (siehe
 * [AddAppointmentActivity]), als auch für den "+"-Button in [CalendarMonthScreen]. Datum,
 * Uhrzeiten, Titel/Ort/Notiz und Wichtigkeit sind vor dem Speichern korrigierbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppointmentScreen(
    initialTitle: String,
    initialLocation: String,
    initialDescription: String,
    initialBeginMillis: Long,
    initialEndMillis: Long,
    initialAllDay: Boolean,
    initialPriority: AppointmentPriority = AppointmentPriority.NORMAL,
    /** Ungleich null: Bearbeiten-Modus statt Neuanlage, siehe [AddAppointmentViewModel.loadForEdit]. */
    appointmentId: Long? = null,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddAppointmentViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf(initialTitle) }
    var location by remember { mutableStateOf(initialLocation) }
    var description by remember { mutableStateOf(initialDescription) }
    var allDay by remember { mutableStateOf(initialAllDay) }
    var priority by remember { mutableStateOf(initialPriority) }
    val reminders = remember { mutableStateListOf<PendingReminder>() }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAsBusy by remember { mutableStateOf(true) }
    var beginMillis by remember { mutableLongStateOf(initialBeginMillis) }
    var endMillis by remember { mutableLongStateOf(initialEndMillis) }
    var editingBegin by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<PendingAttachment>() }
    val context = LocalContext.current

    LaunchedEffect(appointmentId) {
        val id = appointmentId ?: return@LaunchedEffect
        val data = viewModel.loadForEdit(id) ?: return@LaunchedEffect
        title = data.appointment.title
        location = data.appointment.location
        description = data.appointment.description
        allDay = data.appointment.allDay
        priority = AppointmentPriority.valueOf(data.appointment.priority)
        showAsBusy = data.appointment.showAsBusy
        beginMillis = data.appointment.startEpochMillis
        endMillis = data.appointment.endEpochMillis
        reminders.clear()
        reminders.addAll(data.reminders)
        attachments.clear()
        attachments.addAll(data.attachments)
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val fileName = queryAttachmentFileName(uri, context) ?: uri.lastPathSegment.orEmpty()
            val mimeType = context.contentResolver.getType(uri)
            attachments.add(PendingAttachment(uri = uri.toString(), fileName = fileName, mimeType = mimeType))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (appointmentId != null) "Termin bearbeiten" else "Termin speichern") },
                actions = {
                    if (appointmentId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Termin löschen")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancelled) { Text("Abbrechen") }
                TextButton(onClick = {
                    if (appointmentId != null) {
                        viewModel.update(
                            appointmentId = appointmentId,
                            title = title,
                            location = location,
                            description = description,
                            startEpochMillis = beginMillis,
                            endEpochMillis = endMillis,
                            allDay = allDay,
                            priority = priority,
                            reminders = reminders.toList(),
                            showAsBusy = showAsBusy,
                            attachments = attachments.toList(),
                            onSaved = onSaved
                        )
                    } else {
                        viewModel.save(
                            title = title,
                            location = location,
                            description = description,
                            startEpochMillis = beginMillis,
                            endEpochMillis = endMillis,
                            allDay = allDay,
                            priority = priority,
                            reminders = reminders.toList(),
                            showAsBusy = showAsBusy,
                            attachments = attachments.toList(),
                            onSaved = onSaved
                        )
                    }
                }) { Text("Speichern") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { editingDate = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Datum", modifier = Modifier.weight(1f))
                Text(formatDate(beginMillis), style = MaterialTheme.typography.bodyMedium)
            }

            Text("Wichtigkeit", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppointmentPriority.LOW to "Niedrig",
                    AppointmentPriority.NORMAL to "Normal",
                    AppointmentPriority.HIGH to "Hoch"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { priority = value },
                        label = { Text(label) }
                    )
                }
            }

            Text("Anzeigen als", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    true to "Beschäftigt",
                    false to "Frei"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = showAsBusy == value,
                        onClick = { showAsBusy = value },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (reminders.isEmpty()) "Keine Erinnerung" else "Erinnerungen",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAddReminderDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Hinzufügen")
                }
            }
            if (reminders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders) { reminder ->
                        AssistChip(
                            onClick = { reminders.remove(reminder) },
                            label = { Text(describeReminder(reminder)) },
                            leadingIcon = {
                                Icon(
                                    if (reminder.channel == ReminderChannel.EMAIL) Icons.Filled.Email else Icons.Filled.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Entfernen", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ganztägig", modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            if (!allDay) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { editingBegin = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Beginn", modifier = Modifier.weight(1f))
                    Text(formatDateTime(beginMillis), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { editingEnd = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ende", modifier = Modifier.weight(1f))
                    Text(formatDateTime(endMillis), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (attachments.isEmpty()) "Keine Anhänge" else "Anhänge",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    attachmentPicker.launch(arrayOf("*/*"))
                }) { Text("Hinzufügen") }
            }
            attachments.forEach { attachment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Text(
                        attachment.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { attachments.remove(attachment) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Anhang entfernen")
                    }
                }
            }
        }
    }

    if (editingBegin) {
        val time = Instant.ofEpochMilli(beginMillis).atZone(ZoneId.systemDefault())
        NocturnTimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onConfirm = { hour, minute ->
                beginMillis = time.withHour(hour).withMinute(minute).toInstant().toEpochMilli()
                if (endMillis < beginMillis) endMillis = beginMillis
                editingBegin = false
            },
            onDismiss = { editingBegin = false }
        )
    }

    if (editingEnd) {
        val time = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
        NocturnTimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onConfirm = { hour, minute ->
                endMillis = time.withHour(hour).withMinute(minute).toInstant().toEpochMilli()
                editingEnd = false
            },
            onDismiss = { editingEnd = false }
        )
    }

    if (editingDate) {
        val beginZoned = Instant.ofEpochMilli(beginMillis).atZone(ZoneId.systemDefault())
        val endZoned = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
        NocturnDatePickerDialog(
            initialDate = beginZoned.toLocalDate(),
            onConfirm = { newDate ->
                val deltaDays = ChronoUnit.DAYS.between(beginZoned.toLocalDate(), newDate)
                beginMillis = beginZoned.plusDays(deltaDays).toInstant().toEpochMilli()
                endMillis = endZoned.plusDays(deltaDays).toInstant().toEpochMilli()
                editingDate = false
            },
            onDismiss = { editingDate = false }
        )
    }

    if (showAddReminderDialog) {
        AddReminderDialog(
            onSearchEmail = { query -> viewModel.searchContactEmails(query) },
            onConfirm = { reminder ->
                reminders.add(reminder)
                showAddReminderDialog = false
            },
            onDismiss = { showAddReminderDialog = false }
        )
    }

    if (showDeleteConfirm && appointmentId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Termin löschen?") },
            text = { Text("„$title“ wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(appointmentId, onDeleted = onCancelled)
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

private enum class ReminderUnit(val label: String, val minutesPerUnit: Int) {
    MINUTES("Minuten", 1),
    HOURS("Stunden", 60),
    DAYS("Tage", 24 * 60)
}

private fun describeReminder(reminder: PendingReminder): String {
    val timeLabel = when {
        reminder.minutesBefore % (24 * 60) == 0 && reminder.minutesBefore >= 24 * 60 -> {
            val days = reminder.minutesBefore / (24 * 60)
            if (days == 1) "1 Tag vorher" else "$days Tage vorher"
        }
        reminder.minutesBefore % 60 == 0 && reminder.minutesBefore >= 60 -> {
            val hours = reminder.minutesBefore / 60
            if (hours == 1) "1 Stunde vorher" else "$hours Stunden vorher"
        }
        else -> "${reminder.minutesBefore} Min. vorher"
    }
    return if (reminder.channel == ReminderChannel.EMAIL) {
        "$timeLabel · ${reminder.emailAddress.orEmpty()}"
    } else {
        timeLabel
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    onSearchEmail: suspend (String) -> List<ContactEmailSuggestion>,
    onConfirm: (PendingReminder) -> Unit,
    onDismiss: () -> Unit
) {
    var channel by remember { mutableStateOf(ReminderChannel.NOTIFICATION) }
    var selectedPreset by remember { mutableStateOf<Int?>(30) }
    var customAmount by remember { mutableStateOf("30") }
    var customUnit by remember { mutableStateOf(ReminderUnit.MINUTES) }
    var customUnitMenuExpanded by remember { mutableStateOf(false) }
    var emailAddress by remember { mutableStateOf("") }
    var emailMenuExpanded by remember { mutableStateOf(false) }
    var emailSuggestions by remember { mutableStateOf<List<ContactEmailSuggestion>>(emptyList()) }

    LaunchedEffect(emailAddress) {
        emailSuggestions = if (emailAddress.isBlank()) emptyList() else onSearchEmail(emailAddress)
    }

    val minutesBefore = selectedPreset ?: (customAmount.toIntOrNull()?.times(customUnit.minutesPerUnit) ?: 0)
    val canConfirm = minutesBefore > 0 && (channel == ReminderChannel.NOTIFICATION || emailAddress.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erinnerung hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Art", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = channel == ReminderChannel.NOTIFICATION,
                        onClick = { channel = ReminderChannel.NOTIFICATION },
                        label = { Text("Benachrichtigung") }
                    )
                    FilterChip(
                        selected = channel == ReminderChannel.EMAIL,
                        onClick = { channel = ReminderChannel.EMAIL },
                        label = { Text("E-Mail") }
                    )
                }

                Text("Zeitpunkt", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Pair<Int?, String>>(
                        30 to "30 Min.",
                        60 to "1 Std.",
                        24 * 60 to "1 Tag",
                        2 * 24 * 60 to "2 Tage",
                        null to "Benutzerdefiniert"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = selectedPreset == value,
                            onClick = { selectedPreset = value },
                            label = { Text(label) }
                        )
                    }
                }

                if (selectedPreset == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customAmount,
                            onValueChange = { customAmount = it.filter(Char::isDigit) },
                            label = { Text("Anzahl") },
                            modifier = Modifier.weight(1f)
                        )
                        ExposedDropdownMenuBox(
                            expanded = customUnitMenuExpanded,
                            onExpandedChange = { customUnitMenuExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = customUnit.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Einheit") },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = customUnitMenuExpanded,
                                onDismissRequest = { customUnitMenuExpanded = false }
                            ) {
                                ReminderUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit.label) },
                                        onClick = {
                                            customUnit = unit
                                            customUnitMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (channel == ReminderChannel.EMAIL) {
                    ExposedDropdownMenuBox(
                        expanded = emailMenuExpanded && emailSuggestions.isNotEmpty(),
                        onExpandedChange = { emailMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = {
                                emailAddress = it
                                emailMenuExpanded = true
                            },
                            label = { Text("E-Mail-Adresse") },
                            singleLine = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = emailMenuExpanded && emailSuggestions.isNotEmpty(),
                            onDismissRequest = { emailMenuExpanded = false }
                        ) {
                            emailSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text("${suggestion.displayName} · ${suggestion.address}") },
                                    onClick = {
                                        emailAddress = suggestion.address
                                        emailMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        PendingReminder(
                            minutesBefore = minutesBefore,
                            channel = channel,
                            emailAddress = emailAddress.trim().takeIf { channel == ReminderChannel.EMAIL && it.isNotBlank() }
                        )
                    )
                }
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun queryAttachmentFileName(uri: Uri, context: android.content.Context): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()
