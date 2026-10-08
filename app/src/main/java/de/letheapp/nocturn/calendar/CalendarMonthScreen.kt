package de.letheapp.nocturn.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private const val MAX_DOTS_PER_DAY = 3

/**
 * Übersichtliche Monatsansicht: ein Punkt pro Schicht am jeweiligen Tag (max. 3, Rest als
 * "+N"), Details per Tap auf den Tag. Bewusst reduzierter als klassische Schichtplan-Apps
 * (siehe CLAUDE.md Kernfunktion 6) — keine Text-Kürzel/volle Beschriftung in der Kachel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMonthScreen(
    onBack: () -> Unit,
    viewModel: CalendarMonthViewModel = hiltViewModel()
) {
    val month by viewModel.month.collectAsState()
    val entriesByDay by viewModel.entriesByDay.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

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
                    Box(modifier = Modifier.aspectRatio(1f))
                }

                items(daysInMonth) { index ->
                    val date = firstOfMonth.plusDays(index.toLong())
                    DayCell(
                        date = date,
                        isToday = date == today,
                        shifts = entriesByDay[date].orEmpty(),
                        onClick = { if (entriesByDay[date].orEmpty().isNotEmpty()) selectedDay = date }
                    )
                }
            }
        }
    }

    selectedDay?.let { date ->
        DayDetailsDialog(date = date, shifts = entriesByDay[date].orEmpty(), onDismiss = { selectedDay = null })
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, shifts: List<DayShiftInfo>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
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
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            shifts.take(MAX_DOTS_PER_DAY).forEach { shift ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(shift.colorArgb), CircleShape)
                )
            }
            if (shifts.size > MAX_DOTS_PER_DAY) {
                Text(
                    text = "+${shifts.size - MAX_DOTS_PER_DAY}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun DayDetailsDialog(date: LocalDate, shifts: List<DayShiftInfo>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.toString()) },
        text = {
            Column {
                shifts.forEach { shift ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(shift.colorArgb), CircleShape)
                        )
                        Text(
                            text = if (shift.jobName.isNotBlank()) "${shift.shiftName} · ${shift.jobName}" else shift.shiftName,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
