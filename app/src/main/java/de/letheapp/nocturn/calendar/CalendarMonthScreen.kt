package de.letheapp.nocturn.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.ui.NocturnBottomBar
import de.letheapp.nocturn.ui.NocturnTab
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_HEADERS = listOf("M", "D", "M", "D", "F", "S", "S")
private const val MAX_ENTRIES_PER_DAY = 3
private val DAY_CELL_MIN_HEIGHT = 48.dp
private const val SWIPE_THRESHOLD_PX = 120f
private const val LETHE_PACKAGE_NAME = "com.Lethe.app"

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
    onAddTask: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenExport: () -> Unit,
    onOpenCalendarTemplates: () -> Unit,
    onOpenHolidaySettings: () -> Unit,
    onOpenNextcloudSync: () -> Unit,
    onOpenRotationEditor: (jobId: Long) -> Unit,
    onOpenJobTimetable: (jobId: Long, jobName: String) -> Unit,
    onSelectTab: (NocturnTab) -> Unit,
    viewModel: CalendarMonthViewModel = hiltViewModel()
) {
    val month by viewModel.month.collectAsState()
    val entriesByDay by viewModel.entriesByDay.collectAsState()
    val rotations by viewModel.rotations.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val shareFileUri by viewModel.shareFileUri.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRotationPicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNoJobTip by remember { mutableStateOf(false) }
    var pendingRotation by remember { mutableStateOf<RotationOption?>(null) }
    var pendingDeleteRotation by remember { mutableStateOf<RotationOption?>(null) }
    var durationDialogFor by remember { mutableStateOf<RotationApplyRequest?>(null) }
    var phaseQueryFor by remember { mutableStateOf<RotationTodayShiftQueryState?>(null) }
    var fridayQueryFor by remember { mutableStateOf<RotationFridayQueryState?>(null) }
    var nextOccurrenceQueryFor by remember { mutableStateOf<RotationNextOccurrenceQueryState?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val scope = rememberCoroutineScope()
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
        // Lethe (letheapp.de) ist der Inhaber-Messenger dieses Ökosystems und soll beim
        // Kalender-Teilen bevorzugt angeboten werden statt nur alphabetisch irgendwo in der
        // Android-Liste zu stehen – daher als pinned "Direct Share"-Vorschlag vor den
        // restigen Apps, sofern installiert.
        val letheIntent = Intent(intent).setPackage(LETHE_PACKAGE_NAME)
        val letheInstalled = context.packageManager
            .queryIntentActivities(letheIntent, 0)
            .isNotEmpty()
        val chooser = Intent.createChooser(intent, "Kalender teilen").apply {
            if (letheInstalled) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(letheIntent))
            }
        }
        context.startActivity(chooser)
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
                    "Rotation aus Kalender entfernen",
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
                            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            selected = false,
                            onClick = {
                                pendingDeleteRotation = rotation
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
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
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
                                text = { Text("Schichten") },
                                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    when {
                                        jobs.isEmpty() -> showNoJobTip = true
                                        jobs.size == 1 -> onOpenJobTimetable(jobs.first().id, jobs.first().name)
                                        else -> onOpenCalendarTemplates()
                                    }
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
                SmallFloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Eintrag hinzufügen")
                }
            },
            bottomBar = {
                NocturnBottomBar(selected = NocturnTab.KALENDER, onSelect = onSelectTab)
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

                val gridStart = monthGridStart(month)
                val today = LocalDate.now()
                val gridWeeks = monthGridWeeks(month)

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (week in 0 until gridWeeks) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            for (dayOfWeekIndex in 0 until 7) {
                                val date = gridStart.plusDays((week * 7 + dayOfWeekIndex).toLong())
                                DayCell(
                                    date = date,
                                    isCurrentMonth = YearMonth.from(date) == month,
                                    isWeekend = dayOfWeekIndex >= 5,
                                    isToday = date == today,
                                    isSelected = date == selectedDay,
                                    entries = entriesByDay[date].orEmpty(),
                                    onClick = {
                                        val rotation = pendingRotation
                                        if (rotation != null) {
                                            pendingRotation = null
                                            scope.launch {
                                                val startEpochDay = date.toEpochDay()
                                                val candidates = viewModel.rotationPhaseCandidates(rotation.id, startEpochDay)
                                                if (candidates.size <= 1) {
                                                    durationDialogFor = RotationApplyRequest(rotation, date, candidates.firstOrNull() ?: 0)
                                                } else {
                                                    val shiftOptions = viewModel.getRotationShiftOptions(rotation.id)
                                                    phaseQueryFor = RotationTodayShiftQueryState(rotation, date, shiftOptions)
                                                }
                                            }
                                        } else {
                                            selectedDay = date
                                            if (entriesByDay[date].orEmpty().isNotEmpty()) onOpenDay(date)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        if (showNoJobTip) {
            AlertDialog(
                onDismissRequest = { showNoJobTip = false },
                title = { Text("Noch kein Job angelegt") },
                text = { Text("Lege zuerst einen Job an und dann deine Schichten.") },
                confirmButton = {
                    TextButton(onClick = { showNoJobTip = false; onOpenCalendarTemplates() }) {
                        Text("Job anlegen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoJobTip = false }) { Text("Abbrechen") }
                }
            )
        }

        if (showAddDialog) {
            AddEntryTypeDialog(
                onPickAppointment = {
                    showAddDialog = false
                    onAddAppointment(selectedDay ?: LocalDate.now())
                },
                onPickTask = {
                    showAddDialog = false
                    onAddTask(selectedDay ?: LocalDate.now())
                },
                onPickShift = {
                    showAddDialog = false
                    onAddShift(selectedDay ?: LocalDate.now())
                },
                onPickRotation = {
                    showAddDialog = false
                    showRotationPicker = true
                },
                onDismiss = { showAddDialog = false }
            )
        }

        if (showRotationPicker) {
            RotationEntryPickerDialog(
                jobs = jobs,
                onPickExistingJob = { job ->
                    showRotationPicker = false
                    if (job.mode == JobMode.STUDENT.name) {
                        onOpenJobTimetable(job.id, job.name)
                    } else {
                        onOpenRotationEditor(job.id)
                    }
                },
                onCreateJob = { name, mode ->
                    viewModel.createJob(name, mode) { jobId ->
                        showRotationPicker = false
                        if (mode == JobMode.STUDENT) {
                            onOpenJobTimetable(jobId, name)
                        } else {
                            onOpenRotationEditor(jobId)
                        }
                    }
                },
                onDismiss = { showRotationPicker = false }
            )
        }

        phaseQueryFor?.let { queryState ->
            RotationTodayShiftQueryDialog(
                state = queryState,
                onConfirm = { todayShiftId ->
                    phaseQueryFor = null
                    scope.launch {
                        val startEpochDay = queryState.startDate.toEpochDay()
                        val remaining = viewModel.candidatesMatchingTodayShift(queryState.rotation.id, startEpochDay, todayShiftId)
                        when {
                            remaining.size <= 1 -> {
                                durationDialogFor = RotationApplyRequest(queryState.rotation, queryState.startDate, remaining.firstOrNull() ?: 0)
                            }
                            viewModel.needsNextOccurrenceDateQuery(queryState.rotation.id, startEpochDay, todayShiftId) -> {
                                val label = queryState.shiftOptions.firstOrNull { it?.id == todayShiftId }?.name ?: "Frei"
                                nextOccurrenceQueryFor = RotationNextOccurrenceQueryState(queryState.rotation, queryState.startDate, todayShiftId, label)
                            }
                            else -> {
                                fridayQueryFor = RotationFridayQueryState(queryState.rotation, queryState.startDate, todayShiftId, queryState.shiftOptions)
                            }
                        }
                    }
                },
                onDismiss = { phaseQueryFor = null }
            )
        }

        fridayQueryFor?.let { queryState ->
            RotationFridayQueryDialog(
                state = queryState,
                onConfirm = { nextFridayShiftId ->
                    fridayQueryFor = null
                    scope.launch {
                        val phaseStartIndex = viewModel.resolveRotationPhase(
                            queryState.rotation.id,
                            queryState.startDate.toEpochDay(),
                            queryState.todayShiftId,
                            nextFridayShiftId
                        )
                        durationDialogFor = RotationApplyRequest(queryState.rotation, queryState.startDate, phaseStartIndex)
                    }
                },
                onDismiss = { fridayQueryFor = null }
            )
        }

        nextOccurrenceQueryFor?.let { queryState ->
            RotationNextOccurrenceDateDialog(
                state = queryState,
                onConfirm = { nextOccurrenceDate ->
                    nextOccurrenceQueryFor = null
                    scope.launch {
                        val phaseStartIndex = viewModel.resolveRotationPhaseByNextOccurrence(
                            queryState.rotation.id,
                            queryState.startDate.toEpochDay(),
                            queryState.todayShiftId,
                            nextOccurrenceDate.toEpochDay()
                        )
                        durationDialogFor = RotationApplyRequest(queryState.rotation, queryState.startDate, phaseStartIndex)
                    }
                },
                onDismiss = { nextOccurrenceQueryFor = null }
            )
        }

        durationDialogFor?.let { request ->
            RotationDurationDialog(
                rotationName = request.rotation.name,
                startDate = request.startDate,
                onPickMonths = { months ->
                    val totalDays = (request.startDate.plusMonths(months.toLong()).toEpochDay() - request.startDate.toEpochDay()).toInt()
                    viewModel.applyRotation(request.rotation.id, request.startDate.toEpochDay(), totalDays, request.phaseStartIndex)
                    durationDialogFor = null
                },
                onDismiss = { durationDialogFor = null }
            )
        }

        pendingDeleteRotation?.let { rotation ->
            AlertDialog(
                onDismissRequest = { pendingDeleteRotation = null },
                title = { Text("„${rotation.name}“ aus dem Kalender entfernen?") },
                text = {
                    Text(
                        "Alle bisher eingetragenen Tage dieser Rotation werden aus dem Kalender gelöscht, egal für welchen Zeitraum sie zugewiesen wurden. Die Rotation selbst bleibt als Vorlage erhalten und kann danach erneut zugewiesen werden."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeRotationEntries(rotation.id)
                        pendingDeleteRotation = null
                    }) {
                        Text("Entfernen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteRotation = null }) { Text("Abbrechen") }
                }
            )
        }
    }
}

/** Starttag + aufgelöster Tag-Index im Rotationsmuster, bereit für [RotationDurationDialog]. */
private data class RotationApplyRequest(val rotation: RotationOption, val startDate: LocalDate, val phaseStartIndex: Int)

/** Zustand des ersten Rückfrage-Schritts, wenn derselbe Wochentag mehrfach im Rotationsmuster vorkommt. */
private data class RotationTodayShiftQueryState(val rotation: RotationOption, val startDate: LocalDate, val shiftOptions: List<ShiftEntity?>)

/** Zustand des zweiten Rückfrage-Schritts (Schicht am nächsten Freitag), falls die Schicht am
 *  Starttag allein nicht reicht und die Freitags-Frage zuverlässig weiterhilft. */
private data class RotationFridayQueryState(val rotation: RotationOption, val startDate: LocalDate, val todayShiftId: Long?, val shiftOptions: List<ShiftEntity?>)

/** Zustand der Datums-Rückfrage für eine im Muster unregelmäßig/selten vorkommende Schicht,
 *  siehe [de.letheapp.nocturn.data.repository.CalendarRepository.needsNextOccurrenceDateQuery]. */
private data class RotationNextOccurrenceQueryState(val rotation: RotationOption, val startDate: LocalDate, val todayShiftId: Long?, val shiftLabel: String)

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

/** Nächster Freitag nach [date] — fällt [date] selbst auf einen Freitag, ist der übernächste gemeint. */
private fun nextFriday(date: LocalDate): LocalDate {
    val weekday = date.dayOfWeek.value - 1 // 0 = Montag .. 6 = Sonntag
    val daysUntilFriday = (4 - weekday + 7) % 7
    return date.plusDays((if (daysUntilFriday == 0) 7 else daysUntilFriday).toLong())
}

/**
 * Erster Rückfrage-Schritt, wenn derselbe Wochentag mehrfach im Rotationsmuster vorkommt
 * (Zykluslänge über eine Woche): welche Schicht gilt am Starttag selbst. Je nach Ergebnis
 * entscheidet die Aufruferin, ob das schon reicht, eine Freitags-Rückfrage ([RotationFridayQueryDialog])
 * oder — bei einer unregelmäßig/selten vorkommenden Schicht — eine Datums-Rückfrage
 * ([RotationNextOccurrenceDateDialog]) nötig ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationTodayShiftQueryDialog(
    state: RotationTodayShiftQueryState,
    onConfirm: (todayShiftId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(false) }
    var shiftId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wo stehst du in „${state.rotation.name}“?") },
        text = {
            Column {
                Text(
                    "Diese Rotation wiederholt sich über mehr als eine Woche — damit sie ab ${state.startDate} richtig weiterläuft, sag uns kurz, welche Schicht an diesem Tag gilt.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text("Welche Schicht hast du am ${state.startDate}?", style = MaterialTheme.typography.labelLarge)
                ShiftChipRow(
                    options = state.shiftOptions,
                    selectedShiftId = shiftId,
                    hasSelection = selected,
                    onSelect = { selected = true; shiftId = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(shiftId) }, enabled = selected) { Text("Weiter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/**
 * Zweiter Rückfrage-Schritt: welche Schicht gilt am nächsten Freitag — wird nur angezeigt, wenn
 * die Schicht am Starttag allein nicht reicht, die Freitags-Antwort die verbleibenden
 * Kandidaten aber zuverlässig unterscheiden kann (siehe
 * [de.letheapp.nocturn.data.repository.CalendarRepository.needsNextOccurrenceDateQuery]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationFridayQueryDialog(
    state: RotationFridayQueryState,
    onConfirm: (nextFridayShiftId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(false) }
    var shiftId by remember { mutableStateOf<Long?>(null) }
    val fridayDate = remember(state.startDate) { nextFriday(state.startDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Noch eine Schicht zur Sicherheit") },
        text = {
            Column {
                Text(
                    "Mehrere Stellen im Muster passen noch zu deiner Antwort. Welche Schicht hast du am $fridayDate?",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ShiftChipRow(
                    options = state.shiftOptions,
                    selectedShiftId = shiftId,
                    hasSelection = selected,
                    onSelect = { selected = true; shiftId = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(shiftId) }, enabled = selected) { Text("Weiter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/**
 * Rückfrage für eine im Muster unregelmäßig/selten vorkommende Schicht: der Wochentag allein
 * (auch der des nächsten Freitags) reicht nicht, um zwischen den verbleibenden Kandidaten zu
 * unterscheiden — z. B. weil dieselbe seltene Schicht an mehreren, nicht wochentaktisch
 * zusammenhängenden Stellen im Muster vorkommt. Statt eines Wochentags wird deshalb das
 * konkrete Kalenderdatum der nächsten Wiederholung dieser Schicht erfragt (siehe
 * [de.letheapp.nocturn.data.repository.CalendarRepository.resolveRotationPhaseByNextOccurrence]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationNextOccurrenceDateDialog(
    state: RotationNextOccurrenceQueryState,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val utc = remember { ZoneId.of("UTC") }
    val initialDate = remember(state.startDate) { state.startDate.plusDays(1) }
    val initialMillis = remember(initialDate) { initialDate.atStartOfDay(utc).toInstant().toEpochMilli() }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis ?: initialMillis
                onConfirm(Instant.ofEpochMilli(millis).atZone(utc).toLocalDate())
            }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "„${state.shiftLabel}“ kommt in „${state.rotation.name}“ nur unregelmäßig vor — der Wochentag allein reicht hier nicht aus. Wann hast du diese Schicht das nächste Mal?",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftChipRow(
    options: List<ShiftEntity?>,
    selectedShiftId: Long?,
    hasSelection: Boolean,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { shift ->
            FilterChip(
                selected = hasSelection && selectedShiftId == shift?.id,
                onClick = { onSelect(shift?.id) },
                label = { Text(shift?.name ?: "Frei") }
            )
        }
    }
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
internal fun AddEntryTypeDialog(
    onPickAppointment: () -> Unit,
    onPickTask: () -> Unit,
    onPickShift: () -> Unit,
    onPickRotation: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag hinzufügen") },
        text = {
            Column {
                Text(
                    "Was möchtest du anlegen?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextButton(onClick = onPickAppointment, modifier = Modifier.fillMaxWidth()) {
                    Text("Termin", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                TextButton(onClick = onPickTask, modifier = Modifier.fillMaxWidth()) {
                    Text("Aufgabe", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                TextButton(onClick = onPickShift, modifier = Modifier.fillMaxWidth()) {
                    Text("Schicht", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
                TextButton(onClick = onPickRotation, modifier = Modifier.fillMaxWidth()) {
                    Text("Rotation / Stundenplan", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/**
 * Zwischenschritt des "+"-Dialogs für "Rotation / Stundenplan": Rotationen und Wochenstundenpläne
 * gehören zu einem Job (siehe [JobDetailScreen]), daher wird hier zuerst ein bestehender Job
 * gewählt oder direkt ein neuer angelegt, bevor zum passenden Editor weiternavigiert wird
 * (Schichtarbeit-Job → neue Rotation in [RotationEditorScreen], Schüler/Student-Job →
 * Stundenplan-Tab in [JobDetailScreen]).
 */
@Composable
internal fun RotationEntryPickerDialog(
    jobs: List<JobEntity>,
    onPickExistingJob: (JobEntity) -> Unit,
    onCreateJob: (name: String, mode: JobMode) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateForm by remember { mutableStateOf(jobs.isEmpty()) }
    var newJobName by remember { mutableStateOf("") }
    var newJobMode by remember { mutableStateOf(JobMode.SHIFT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showCreateForm) "Neuer Job" else "Rotation / Stundenplan anlegen") },
        text = {
            if (showCreateForm) {
                Column {
                    Text(
                        "Rotationen und Stundenpläne gehören zu einem Job. Lege dafür zuerst einen an:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newJobName,
                        onValueChange = { newJobName = it },
                        label = { Text("Name (z. B. Hauptjob, Schule)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = newJobMode == JobMode.SHIFT,
                            onClick = { newJobMode = JobMode.SHIFT },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Schichtarbeit") }
                        SegmentedButton(
                            selected = newJobMode == JobMode.STUDENT,
                            onClick = { newJobMode = JobMode.STUDENT },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Schüler/Student") }
                    }
                }
            } else {
                Column {
                    Text(
                        "Für welchen Job soll eine Rotation oder ein Stundenplan angelegt werden?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    jobs.forEach { job ->
                        TextButton(onClick = { onPickExistingJob(job) }, modifier = Modifier.fillMaxWidth()) {
                            val modeLabel = if (job.mode == JobMode.STUDENT.name) "Schüler/Student" else "Schichtarbeit"
                            Text("${job.name} ($modeLabel)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                    TextButton(onClick = { showCreateForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Neuen Job anlegen", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateForm) {
                TextButton(onClick = { onCreateJob(newJobName.trim(), newJobMode) }, enabled = newJobName.isNotBlank()) {
                    Text("Anlegen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** Montag der ersten in der Monatsansicht sichtbaren Woche (inkl. Vormonats-Resttage). */
internal fun monthGridStart(month: YearMonth): LocalDate {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    return firstOfMonth.minusDays(leadingBlanks.toLong())
}

/**
 * Anzahl der darzustellenden Wochenreihen: so viele, bis der Monat komplett sichtbar ist,
 * plus eine zusätzliche Reihe, die garantiert nur aus Folgemonats-Tagen besteht (Platz für
 * den "+"-Button ohne wichtige Inhalte zu verdecken).
 */
internal fun monthGridWeeks(month: YearMonth): Int {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val totalCells = leadingBlanks + month.lengthOfMonth()
    val weeksToShowMonth = (totalCells + 6) / 7
    return weeksToShowMonth + 1
}

@Composable
private fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isWeekend: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    entries: List<CalendarDayEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPublicHoliday = entries.any { it is HolidayDayEntry && !it.isSchoolHoliday }
    val contentAlpha = if (isCurrentMonth) 1f else 0.35f
    val weekendTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (isCurrentMonth) 0.55f else 0.2f)

    Column(
        modifier = modifier
            .heightIn(min = DAY_CELL_MIN_HEIGHT)
            .background(
                color = when {
                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                    isWeekend -> weekendTint
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(6.dp)
            )
            .then(
                if (isSelected && !isToday) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = (if (hasPublicHoliday && isCurrentMonth) Color(PUBLIC_HOLIDAY_COLOR_ARGB) else MaterialTheme.colorScheme.onSurface)
                .copy(alpha = if (hasPublicHoliday && isCurrentMonth) 1f else contentAlpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Column(modifier = Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.take(MAX_ENTRIES_PER_DAY).forEach { entry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(entry.colorArgb).copy(alpha = contentAlpha), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                    )
                }
            }
            if (entries.size > MAX_ENTRIES_PER_DAY) {
                Text(
                    text = "+${entries.size - MAX_ENTRIES_PER_DAY}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
    }
}

