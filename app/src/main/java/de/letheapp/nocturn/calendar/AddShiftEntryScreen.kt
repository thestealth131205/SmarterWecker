package de.letheapp.nocturn.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Formular für die FAB-Option "Schicht" in [CalendarMonthScreen]: Job und Schicht wählen,
 * Datum ist mit dem zuvor angetippten Kalendertag vorbelegt, aber änderbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftEntryScreen(
    initialEpochDay: Long,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddShiftEntryViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsState()
    val selectedJobId by viewModel.selectedJobId.collectAsState()
    val shifts by viewModel.shiftsForSelectedJob.collectAsState()
    var selectedShiftId by remember { mutableStateOf<Long?>(null) }
    var epochDay by remember { mutableStateOf(initialEpochDay) }
    var editingDate by remember { mutableStateOf(false) }
    var jobMenuExpanded by remember { mutableStateOf(false) }
    var shiftMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schicht eintragen") },
                navigationIcon = {
                    IconButton(onClick = onCancelled) {
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
            Row(
                modifier = Modifier.fillMaxWidth().clickable { editingDate = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Datum", modifier = Modifier.weight(1f))
                Text(
                    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ExposedDropdownMenuBox(expanded = jobMenuExpanded, onExpandedChange = { jobMenuExpanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = jobs.firstOrNull { it.id == selectedJobId }?.name ?: "",
                    onValueChange = {},
                    label = { Text("Job") },
                    placeholder = { Text("Job wählen") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = jobMenuExpanded, onDismissRequest = { jobMenuExpanded = false }) {
                    if (jobs.isEmpty()) {
                        DropdownMenuItem(text = { Text("Noch keine Jobs angelegt") }, onClick = {}, enabled = false)
                    }
                    jobs.forEach { job ->
                        DropdownMenuItem(
                            text = { Text(job.name) },
                            onClick = {
                                viewModel.selectJob(job.id)
                                selectedShiftId = null
                                jobMenuExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = shiftMenuExpanded,
                onExpandedChange = { if (selectedJobId != null) shiftMenuExpanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = shifts.firstOrNull { it.id == selectedShiftId }?.name ?: "",
                    onValueChange = {},
                    label = { Text("Schicht") },
                    placeholder = { Text("Schicht wählen") },
                    enabled = selectedJobId != null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shiftMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = shiftMenuExpanded, onDismissRequest = { shiftMenuExpanded = false }) {
                    if (shifts.isEmpty()) {
                        DropdownMenuItem(text = { Text("Keine Schichten für diesen Job") }, onClick = {}, enabled = false)
                    }
                    shifts.forEach { shift ->
                        DropdownMenuItem(
                            text = { Text(shift.name) },
                            onClick = {
                                selectedShiftId = shift.id
                                shiftMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancelled) { Text("Abbrechen") }
                TextButton(
                    onClick = {
                        val jobId = selectedJobId
                        val shiftId = selectedShiftId
                        if (jobId != null && shiftId != null) {
                            viewModel.save(jobId, shiftId, epochDay, onSaved)
                        }
                    },
                    enabled = selectedJobId != null && selectedShiftId != null
                ) { Text("Speichern") }
            }
        }
    }

    if (editingDate) {
        NocturnDatePickerDialog(
            initialDate = LocalDate.ofEpochDay(epochDay),
            onConfirm = { date ->
                epochDay = date.toEpochDay()
                editingDate = false
            },
            onDismiss = { editingDate = false }
        )
    }
}
