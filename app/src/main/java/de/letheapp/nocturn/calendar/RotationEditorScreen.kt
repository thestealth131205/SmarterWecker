package de.letheapp.nocturn.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
    var pickerDayIndex by remember { mutableIntStateOf(-1) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val attemptBack = {
        if (viewModel.hasUnsavedChanges) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = attemptBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Neue Rotation" else "Rotation bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
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

            Text(
                text = "Tag für Tag die Schicht(en) eintragen, bis sich das Muster wiederholen würde — pro Tag sind auch mehrere Schichten gleichzeitig möglich (z. B. Frühschicht + Kinder-Wochenende). Aktuelle Länge: ${viewModel.days.size} Tage. Der Starttag im Kalender wird später über die Kalender-Seitenleiste festgelegt.",
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
                    val dayShifts = viewModel.days[index].mapNotNull { id -> shifts.firstOrNull { it.id == id } }
                    DayCell(
                        dayNumber = index + 1,
                        dayShifts = dayShifts,
                        onClick = { pickerDayIndex = index }
                    )
                }
            }

            val usageCounts = viewModel.shiftUsageCounts
            if (usageCounts.isNotEmpty()) {
                Text(
                    text = "Verwendete Schichten in diesem Muster:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shifts.filter { usageCounts.containsKey(it.id) }.forEach { shift ->
                        val count = usageCounts[shift.id] ?: 0
                        AssistChip(
                            onClick = {},
                            label = { Text("${shift.name} ×$count") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(shift.colorArgb), RoundedCornerShape(3.dp))
                                )
                            }
                        )
                    }
                }
            }

            Text(
                text = "Antippen fügt sofort einen neuen Tag mit dieser Schicht an:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { viewModel.addDay() },
                        label = { Text("Frei") }
                    )
                    shifts.forEach { shift ->
                        AssistChip(
                            onClick = { viewModel.addDayWithShift(shift.id) },
                            label = { Text(shift.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(shift.colorArgb), RoundedCornerShape(3.dp))
                                )
                            }
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.removeLastDay() },
                    enabled = viewModel.days.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Letzten Tag entfernen")
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
        val index = pickerDayIndex
        ShiftPickerDialog(
            shifts = shifts,
            selectedShiftIds = viewModel.days[index].toSet(),
            onToggle = { shiftId -> viewModel.toggleShift(index, shiftId) },
            onClear = { viewModel.clearDay(index) },
            onDismiss = { pickerDayIndex = -1 }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Du hast das Muster geändert, ohne zu speichern. Diese Änderungen gehen sonst verloren.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Weiter bearbeiten") }
            }
        )
    }
}

@Composable
private fun DayCell(dayNumber: Int, dayShifts: List<ShiftEntity>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (dayShifts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayNumber.toString(), style = MaterialTheme.typography.labelSmall)
                    Text(text = "frei", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    dayNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                // Jede gleichzeitig geltende Schicht als eigener, gleich hoher Streifen untereinander.
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    dayShifts.forEach { shift ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(shift.colorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shift.name.take(3),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftPickerDialog(
    shifts: List<ShiftEntity>,
    selectedShiftIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schicht(en) für diesen Tag") },
        text = {
            Column {
                Text(
                    "Mehrere Schichten können gleichzeitig gelten (z. B. Frühschicht + Kinder-Wochenende).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextButton(onClick = onClear) { Text("Frei (alle abwählen)") }
                shifts.forEach { shift ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(shift.id) }
                    ) {
                        Checkbox(checked = shift.id in selectedShiftIds, onCheckedChange = { onToggle(shift.id) })
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        }
    )
}
