package de.letheapp.nocturn.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private const val MAX_DOTS_PER_DAY = 4
private val DAY_CELL_MIN_HEIGHT = 92.dp
private const val SWIPE_THRESHOLD_PX = 120f

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
    onOpenDay: (LocalDate) -> Unit,
    onOpenExport: () -> Unit,
    onOpenCalendarTemplates: () -> Unit,
    onOpenHolidaySettings: () -> Unit,
    onOpenNextcloudSync: () -> Unit,
    viewModel: CalendarMonthViewModel = hiltViewModel()
) {
    val month by viewModel.month.collectAsState()
    val entriesByDay by viewModel.entriesByDay.collectAsState()
    val rotations by viewModel.rotations.collectAsState()
    val shareFileUri by viewModel.shareFileUri.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingRotation by remember { mutableStateOf<RotationOption?>(null) }
    var durationDialogFor by remember { mutableStateOf<Pair<RotationOption, LocalDate>?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    LaunchedEffect(shareFileUri) {
        val uri = shareFileUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Kalender teilen"))
        viewModel.consumeShareFile()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("Zurück zur Übersicht") },
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                    selected = false,
                    onClick = {
                        drawerScope.launch { drawerState.close() }
                        onBack()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Rotation anwenden",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (rotations.isEmpty()) {
                    Text(
                        "Noch keine Rotationen angelegt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    rotations.forEach { rotation ->
                        NavigationDrawerItem(
                            label = {
                                Text(if (rotation.jobName.isNotBlank()) "${rotation.name} · ${rotation.jobName}" else rotation.name)
                            },
                            icon = { Icon(Icons.Filled.EventRepeat, contentDescription = null) },
                            selected = pendingRotation?.id == rotation.id,
                            onClick = {
                                pendingRotation = rotation
                                drawerScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Ferien & Feiertage",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "Diese Farben markieren Feiertage und Ferien im Kalender:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp)
                )
                HolidayLegendRow(color = Color(PUBLIC_HOLIDAY_COLOR_ARGB), label = "Gesetzliche Feiertage")
                HolidayLegendRow(color = Color(SCHOOL_HOLIDAY_COLOR_ARGB), label = "Schulferien")
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
                                .replaceFirstChar { it.uppercase() } + " ${month.year}"
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::previousMonth) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Vorheriger Monat")
                        }
                        IconButton(onClick = viewModel::nextMonth) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Nächster Monat")
                        }
                        IconButton(onClick = viewModel::shareMonth) {
                            Icon(Icons.Filled.Share, contentDescription = "Kalender teilen")
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
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
                                text = { Text("Nextcloud-Sync") },
                                leadingIcon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenNextcloudSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Kalender exportieren") },
                                leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenExport()
                                }
                            )
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
            var dragAccumulated by remember { mutableStateOf(0f) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragAccumulated <= -SWIPE_THRESHOLD_PX -> viewModel.nextMonth()
                                    dragAccumulated >= SWIPE_THRESHOLD_PX -> viewModel.previousMonth()
                                }
                                dragAccumulated = 0f
                            },
                            onDragCancel = { dragAccumulated = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulated += dragAmount
                        }
                    }
            ) {
                pendingRotation?.let { rotation ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            "Tippe auf den Starttag für „${rotation.name}“ im Kalender.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

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
                                val rotation = pendingRotation
                                if (rotation != null) {
                                    durationDialogFor = rotation to date
                                    pendingRotation = null
                                } else {
                                    selectedDay = date
                                    if (entriesByDay[date].orEmpty().isNotEmpty()) onOpenDay(date)
                                }
                            }
                        )
                    }
                }
            }
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

        durationDialogFor?.let { (rotation, startDate) ->
            RotationDurationDialog(
                rotationName = rotation.name,
                startDate = startDate,
                onPickMonths = { months ->
                    val totalDays = (startDate.plusMonths(months.toLong()).toEpochDay() - startDate.toEpochDay()).toInt()
                    viewModel.applyRotation(rotation.id, startDate.toEpochDay(), totalDays)
                    durationDialogFor = null
                },
                onDismiss = { durationDialogFor = null }
            )
        }
    }
}

private val ROTATION_DURATION_OPTIONS = listOf(
    "1 Monat" to 1,
    "3 Monate" to 3,
    "6 Monate" to 6,
    "1 Jahr" to 12,
    "2 Jahre" to 24
)

@Composable
private fun RotationDurationDialog(
    rotationName: String,
    startDate: LocalDate,
    onPickMonths: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("„$rotationName“ ab $startDate anwenden") },
        text = {
            Column {
                Text(
                    "Wie lange soll die Rotation ab diesem Tag in den Kalender eingetragen werden?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ROTATION_DURATION_OPTIONS.forEach { (label, months) ->
                    TextButton(
                        onClick = { onPickMonths(months) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun HolidayLegendRow(color: Color, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AddEntryTypeDialog(onPickAppointment: () -> Unit, onPickShift: () -> Unit, onDismiss: () -> Unit) {
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
    val hasPublicHoliday = entries.any { it is HolidayDayEntry && !it.isSchoolHoliday }
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
                    when {
                        isToday -> MaterialTheme.colorScheme.primary
                        hasPublicHoliday -> Color(PUBLIC_HOLIDAY_COLOR_ARGB)
                        else -> Color.Transparent
                    },
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
                color = if (isToday || hasPublicHoliday) Color.White else MaterialTheme.colorScheme.onSurface
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

