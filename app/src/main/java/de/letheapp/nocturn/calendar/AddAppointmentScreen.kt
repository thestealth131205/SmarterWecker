package de.letheapp.nocturn.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Bestätigungsscreen, der erscheint, wenn NocTurn aus einer Fremd-App (z. B. Kinoticket,
 * Buchungsbestätigung) heraus über den "Zum Kalender hinzufügen"-Dialog von Android als
 * Kalender-App ausgewählt wurde (siehe [AddAppointmentActivity]). Datum bleibt wie von der
 * Fremd-App vorgegeben, Uhrzeiten/Titel/Ort/Notiz sind vor dem Speichern noch korrigierbar.
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
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddAppointmentViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf(initialTitle) }
    var location by remember { mutableStateOf(initialLocation) }
    var description by remember { mutableStateOf(initialDescription) }
    var allDay by remember { mutableStateOf(initialAllDay) }
    var beginMillis by remember { mutableLongStateOf(initialBeginMillis) }
    var endMillis by remember { mutableLongStateOf(initialEndMillis) }
    var editingBegin by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Termin speichern") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
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
                        onSaved = onSaved
                    )
                }) { Text("Speichern") }
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
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
