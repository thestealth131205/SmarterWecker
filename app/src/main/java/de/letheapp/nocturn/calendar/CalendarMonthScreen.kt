package de.letheapp.nocturn.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
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
                SmallFloatingActionButton(onClick = { showAddDialog = true }) {
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
                                            durationDialogFor = rotation to date
                                            pendingRotation = null
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

