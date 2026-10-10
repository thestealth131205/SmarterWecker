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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftEntity

private val WEEKDAY_TAB_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private const val MAX_LESSONS_PER_DAY = 12

/**
 * Vorlagen-Detailansicht eines Jobs. Je nach [JobEntity.mode][de.letheapp.nocturn.data.db.JobEntity]
 * entweder die Schichtarbeit-Tabs "Schichten"/"Rotationen" (Schritte 9+10) oder der
 * Wochenstundenplan für den Schüler/Student-Modus (bis zu 12 Unterrichtsstunden pro Tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobName: String,
    onOpenRotationEditor: (rotationId: Long?) -> Unit,
    onOpenPhotoImport: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val job by viewModel.job.collectAsState()
    val isStudentMode = job?.mode == JobMode.STUDENT.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPhotoImport) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Foto-Import")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedButton(
                    selected = !isStudentMode,
                    onClick = { viewModel.setMode(JobMode.SHIFT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Schichtarbeit") }
                SegmentedButton(
                    selected = isStudentMode,
                    onClick = { viewModel.setMode(JobMode.STUDENT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Schüler/Student") }
            }

            if (isStudentMode) {
                TimetableTab(viewModel)
            } else {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Schichten") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Rotationen") })
                }

                when (selectedTab) {
                    0 -> ShiftsTab(viewModel)
                    else -> RotationsTab(viewModel, onOpenRotationEditor)
                }
            }
        }
    }
}

@Composable
private fun TimetableTab(viewModel: JobDetailViewModel) {
    val lessons by viewModel.lessons.collectAsState()
    var selectedWeekday by remember { mutableIntStateOf(0) }
    var editingLesson by remember { mutableStateOf<LessonEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val lessonsForDay = lessons.filter { it.weekday == selectedWeekday }
        .sortedBy { it.startHour * 60 + it.startMinute }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedWeekday) {
            WEEKDAY_TAB_LABELS.forEachIndexed { index, label ->
                Tab(selected = selectedWeekday == index, onClick = { selectedWeekday = index }, text = { Text(label) })
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (lessonsForDay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Unterrichtsstunden an diesem Tag")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lessonsForDay, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            onClick = { editingLesson = lesson; showDialog = true },
                            onDelete = { viewModel.deleteLesson(lesson) }
                        )
                    }
                }
            }

            if (lessonsForDay.size < MAX_LESSONS_PER_DAY) {
                FloatingActionButton(
                    onClick = { editingLesson = null; showDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Unterrichtsstunde hinzufügen")
                }
            }
        }
    }

    if (showDialog) {
        LessonEditDialog(
            jobId = viewModel.jobId,
            weekday = selectedWeekday,
            existing = editingLesson,
            onConfirm = { lesson -> viewModel.saveLesson(lesson); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun LessonCard(lesson: LessonEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val endTotal = lesson.startHour * 60 + lesson.startMinute + lesson.durationMinutes
    val endHour = (endTotal / 60) % 24
    val endMinute = endTotal % 60

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(lesson.colorArgb), CircleShape)
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(lesson.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%02d:%02d–%02d:%02d".format(lesson.startHour, lesson.startMinute, endHour, endMinute),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Unterrichtsstunde löschen")
            }
        }
    }
}

@Composable
private fun ShiftsTab(viewModel: JobDetailViewModel) {
    val shifts by viewModel.shifts.collectAsState()
    var editingShift by remember { mutableStateOf<ShiftEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (shifts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Noch keine Schichten angelegt. Tippe auf „+“, um deine erste Schicht anzulegen.",
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shifts, key = { it.id }) { shift ->
                    ShiftCard(
                        shift = shift,
                        onClick = { editingShift = shift; showDialog = true },
                        onDelete = { viewModel.deleteShift(shift) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { editingShift = null; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Schicht hinzufügen")
        }
    }

    if (showDialog) {
        ShiftEditDialog(
            jobId = viewModel.jobId,
            existing = editingShift,
            onConfirm = { shift -> viewModel.saveShift(shift); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ShiftCard(shift: ShiftEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val endTotal = shift.startHour * 60 + shift.startMinute + shift.durationMinutes
    val endHour = (endTotal / 60) % 24
    val endMinute = endTotal % 60

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(shift.colorArgb), CircleShape)
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(shift.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%02d:%02d–%02d:%02d".format(shift.startHour, shift.startMinute, endHour, endMinute),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Schicht löschen")
            }
        }
    }
}

@Composable
private fun RotationsTab(viewModel: JobDetailViewModel, onOpenRotationEditor: (Long?) -> Unit) {
    val rotations by viewModel.rotations.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (rotations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch keine Rotation angelegt")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rotations, key = { it.id }) { rotation ->
                    RotationCard(
                        rotation = rotation,
                        onClick = { onOpenRotationEditor(rotation.id) },
                        onDelete = { viewModel.deleteRotation(rotation) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onOpenRotationEditor(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Rotation hinzufügen")
        }
    }
}

@Composable
private fun RotationCard(rotation: RotationEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp, 40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(rotation.name, style = MaterialTheme.typography.titleMedium)
                Text("Vorlage – im Kalender einem Zeitraum zuweisen", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Rotation löschen")
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}
