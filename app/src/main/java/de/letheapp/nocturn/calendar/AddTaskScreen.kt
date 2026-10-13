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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Aufgaben-Formular (CLAUDE.md Kalender-Kernfunktion): anders als ein Termin kein fester
 * Zeitraum, sondern ein abhakbarer To-Do-Eintrag mit Fälligkeitstag und optionaler Frist-Uhrzeit,
 * angelehnt an das vom Nutzer gezeigte Google-Tasks-Referenzbild.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    initialEpochDay: Long,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddTaskViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(initialEpochDay)) }
    var dueTimeMillis by remember { mutableStateOf<Long?>(null) }
    var editingDate by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aufgabe speichern") }) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancelled) { Text("Abbrechen") }
                TextButton(onClick = {
                    viewModel.save(
                        title = title,
                        notes = notes,
                        dueEpochDay = date.toEpochDay(),
                        dueEpochMillis = dueTimeMillis,
                        allDay = allDay,
                        onSaved = onSaved
                    )
                }) { Text("Speichern") }
            }
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
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Details") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { editingDate = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fällig am", modifier = Modifier.weight(1f))
                Text(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), style = MaterialTheme.typography.bodyMedium)
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
                    modifier = Modifier.fillMaxWidth().clickable { editingTime = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Frist", modifier = Modifier.weight(1f))
                    val current = dueTimeMillis
                    Text(
                        if (current != null) formatTime(current) else "Uhrzeit wählen",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (editingDate) {
        NocturnDatePickerDialog(
            initialDate = date,
            onConfirm = { newDate ->
                date = newDate
                editingDate = false
            },
            onDismiss = { editingDate = false }
        )
    }

    if (editingTime) {
        val base = dueTimeMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
            ?: date.atTime(9, 0).atZone(ZoneId.systemDefault())
        NocturnTimePickerDialog(
            initialHour = base.hour,
            initialMinute = base.minute,
            onConfirm = { hour, minute ->
                dueTimeMillis = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                editingTime = false
            },
            onDismiss = { editingTime = false }
        )
    }
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
