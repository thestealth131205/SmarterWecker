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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.letheapp.nocturn.data.db.AppointmentPriority
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
    initialReminderMinutesBefore: Int? = null,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddAppointmentViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf(initialTitle) }
    var location by remember { mutableStateOf(initialLocation) }
    var description by remember { mutableStateOf(initialDescription) }
    var allDay by remember { mutableStateOf(initialAllDay) }
    var priority by remember { mutableStateOf(initialPriority) }
    var reminderMinutesBefore by remember { mutableStateOf(initialReminderMinutesBefore) }
    var showAsBusy by remember { mutableStateOf(true) }
    var beginMillis by remember { mutableLongStateOf(initialBeginMillis) }
    var endMillis by remember { mutableLongStateOf(initialEndMillis) }
    var editingBegin by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<PendingAttachment>() }
    val context = LocalContext.current

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
            TopAppBar(title = { Text("Termin speichern") })
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancelled) { Text("Abbrechen") }
                TextButton(onClick = {
                    viewModel.save(
                        title = title,
                        location = location,
                        description = description,
                        startEpochMillis = beginMillis,
                        endEpochMillis = endMillis,
                        allDay = allDay,
                        priority = priority,
                        reminderMinutesBefore = reminderMinutesBefore,
                        showAsBusy = showAsBusy,
                        attachments = attachments.toList(),
                        onSaved = onSaved
                    )
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

            Text("Erinnerung", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Pair<Int?, String>>(
                    null to "Keine",
                    30 to "30 Min. vorher",
                    24 * 60 to "1 Tag vorher",
                    2 * 24 * 60 to "2 Tage vorher"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = reminderMinutesBefore == value,
                        onClick = { reminderMinutesBefore = value },
                        label = { Text(label) }
                    )
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
