package de.letheapp.nocturn.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private const val MAX_DOTS_PER_DAY = 4
private val DAY_CELL_MIN_HEIGHT = 92.dp

/**
 * Übersichtliche Monatsansicht: ein Punkt pro Schicht/Termin am jeweiligen Tag (max. 4, Rest
 * als "+N") plus der erste Eintragsname klein darunter, Details per Tap auf den Tag. Bewusst
 * reduzierter als klassische Schichtplan-Apps (siehe CLAUDE.md Kernfunktion 6) — großzügigere
 * Zellen als in früheren Versionen, damit pro Tag mehr erkennbar ist, ohne überladen zu wirken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMonthScreen(
    onBack: () -> Unit,
    onAddAppointment: (LocalDate) -> Unit,
    onAddShift: (LocalDate) -> Unit,
    viewModel: CalendarMonthViewModel = hiltViewModel()
) {
    val month by viewModel.month.collectAsState()
    val entriesByDay by viewModel.entriesByDay.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var detailsDialogDay by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
                            .replaceFirstChar { it.uppercase() } + " ${month.year}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Vorheriger Monat")
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Nächster Monat")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Eintrag hinzufügen")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            val firstOfMonth = month.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
            val daysInMonth = month.lengthOfMonth()
            val today = LocalDate.now()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(leadingBlanks) {
                    Box(modifier = Modifier.heightIn(min = DAY_CELL_MIN_HEIGHT))
                }

                items(daysInMonth) { index ->
                    val date = firstOfMonth.plusDays(index.toLong())
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selectedDay,
                        entries = entriesByDay[date].orEmpty(),
                        onClick = {
                            selectedDay = date
                            if (entriesByDay[date].orEmpty().isNotEmpty()) detailsDialogDay = date
                        }
                    )
                }
            }
        }
    }

    detailsDialogDay?.let { date ->
        DayDetailsDialog(date = date, entries = entriesByDay[date].orEmpty(), onDismiss = { detailsDialogDay = null })
    }

    if (showAddDialog) {
        AddEntryTypeDialog(
            onPickAppointment = {
                showAddDialog = false
                onAddAppointment(selectedDay ?: LocalDate.now())
            },
            onPickShift = {
                showAddDialog = false
                onAddShift(selectedDay ?: LocalDate.now())
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun AddEntryTypeDialog(onPickAppointment: () -> Unit, onPickShift: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag hinzufügen") },
        text = { Text("Was möchtest du anlegen?") },
        confirmButton = {
            TextButton(onClick = onPickAppointment) { Text("Termin") }
        },
        dismissButton = {
            TextButton(onClick = onPickShift) { Text("Schicht") }
        }
    )
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, entries: List<CalendarDayEntry>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DAY_CELL_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
                .then(
                    if (isSelected && !isToday) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            entries.take(MAX_DOTS_PER_DAY).forEach { entry ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(entry.colorArgb), CircleShape)
                )
            }
            if (entries.size > MAX_DOTS_PER_DAY) {
                Text(
                    text = "+${entries.size - MAX_DOTS_PER_DAY}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (entries.isNotEmpty()) {
            Text(
                text = entries.first().label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun DayDetailsDialog(date: LocalDate, entries: List<CalendarDayEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.toString()) },
        text = {
            Column {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(entry.colorArgb), CircleShape)
                        )
                        val text = when (entry) {
                            is ShiftDayEntry -> if (entry.jobName.isNotBlank()) "${entry.shiftName} · ${entry.jobName}" else entry.shiftName
                            is AppointmentDayEntry -> if (entry.priority == "HIGH") "${entry.title} (wichtig)" else entry.title
                            is HolidayDayEntry -> if (entry.isSchoolHoliday) "${entry.name} (Ferien)" else entry.name
                        }
                        Text(text = text, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
