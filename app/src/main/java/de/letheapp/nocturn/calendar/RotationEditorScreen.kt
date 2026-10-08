package de.letheapp.nocturn.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.ShiftEntity
import java.time.LocalDate

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * Rotations-Editor: Tag-für-Tag-Raster (7 Wochentags-Spalten), in das der Nutzer so
 * lange Schichten einträgt, bis sich das Muster wiederholen würde. Die Zykluslänge
 * ergibt sich aus der Anzahl der eingetragenen Tage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationEditorScreen(
    onBack: () -> Unit,
    viewModel: RotationEditorViewModel = hiltViewModel()
) {
    val shifts by viewModel.shifts.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var pickerDayIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Neue Rotation" else "Rotation bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Rotation speichern")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!viewModel.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Lädt…")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Name der Rotation (z. B. 84-Tage-Zyklus)") },
                modifier = Modifier.fillMaxWidth()
            )

            val startDate = remember(viewModel.startDateEpochDay) { LocalDate.ofEpochDay(viewModel.startDateEpochDay) }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("Start der Rotation: $startDate")
            }

            Text(
                text = "Tag für Tag die Schicht eintragen, bis sich das Muster wiederholen würde. Aktuelle Länge: ${viewModel.days.size} Tage.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(WEEKDAY_LABELS) { label ->
                    Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium)
                }

                items(viewModel.days.size) { index ->
                    val shiftId = viewModel.days[index]
                    val shift = shifts.firstOrNull { it.id == shiftId }
                    DayCell(
                        dayNumber = index + 1,
                        shift = shift,
                        onClick = { pickerDayIndex = index }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.addDay() }, modifier = Modifier.weight(1f)) {
                    Text("+ Tag")
                }
                OutlinedButton(onClick = { viewModel.removeLastDay() }, modifier = Modifier.weight(1f)) {
                    Text("Letzten Tag entfernen")
                }
            }

            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                enabled = viewModel.name.isNotBlank() && viewModel.days.isNotEmpty()
            ) {
                Text("Rotation speichern")
            }
        }
    }

    if (pickerDayIndex >= 0) {
        ShiftPickerDialog(
            shifts = shifts,
            onSelect = { shiftId -> viewModel.setDay(pickerDayIndex, shiftId); pickerDayIndex = -1 },
            onDismiss = { pickerDayIndex = -1 }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.startDateEpochDay * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.startDateEpochDay = LocalDate.ofEpochDay(0)
                            .plusDays(millis / (24 * 60 * 60 * 1000))
                            .toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DayCell(dayNumber: Int, shift: ShiftEntity?, onClick: () -> Unit) {
    val backgroundColor = shift?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayNumber.toString(), style = MaterialTheme.typography.labelSmall)
                Text(
                    text = shift?.name?.take(3) ?: "frei",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ShiftPickerDialog(shifts: List<ShiftEntity>, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schicht für diesen Tag") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) { Text("Frei") }
                shifts.forEach { shift ->
                    TextButton(onClick = { onSelect(shift.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp)
                                    .background(Color(shift.colorArgb), RoundedCornerShape(4.dp))
                            )
                            Text(shift.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
