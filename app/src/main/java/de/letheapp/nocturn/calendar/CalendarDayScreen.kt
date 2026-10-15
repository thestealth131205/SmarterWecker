package de.letheapp.nocturn.calendar

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.JobMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tagesansicht beim Antippen eines Kalendertags mit Termin/Feiertag/Schicht (CLAUDE.md
 * Kernfunktion 6, an die Google-Kalender-Tagesansicht angelehnt): ganztägige Einträge als
 * Banner oben, darunter zeitlich sortierte Einträge mit Start-/Endzeit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDayScreen(
    onBack: () -> Unit,
    onAddAppointment: (LocalDate) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onAddShift: (LocalDate) -> Unit,
    onOpenRotationEditor: (jobId: Long) -> Unit,
    onOpenJobTimetable: (jobId: Long, jobName: String) -> Unit,
    viewModel: CalendarDayViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showRotationPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formattedDayTitle(viewModel.date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        floatingActionButton = {
            AddEntrySpeedDial(
                expanded = addMenuExpanded,
                onExpandedChange = { addMenuExpanded = it },
                onPickAppointment = {
                    addMenuExpanded = false
                    onAddAppointment(viewModel.date)
                },
                onPickTask = {
                    addMenuExpanded = false
                    onAddTask(viewModel.date)
                },
                onPickShift = {
                    addMenuExpanded = false
                    onAddShift(viewModel.date)
                },
                onPickRotation = {
                    addMenuExpanded = false
                    showRotationPicker = true
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keine Einträge an diesem Tag")
                }
            } else {
                val birthdayItems = items.filterIsInstance<BirthdayAgendaItem>()
                val taskItems = items.filterIsInstance<TaskAgendaItem>()
                val allDayItems = items.filterIsInstance<AllDayAgendaItem>()
                val timedItems = items.filterIsInstance<TimedAgendaItem>().sortedBy { it.startMinuteOfDay }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(birthdayItems) { BirthdayCard(it) }
                    items(taskItems) { item ->
                        TaskCard(item, onToggle = { completed -> viewModel.toggleTaskCompleted(item.taskId, completed) })
                    }
                    items(allDayItems) { AllDayCard(it) }
                    items(timedItems) { TimedCard(it) }
                }
            }

            if (addMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { addMenuExpanded = false })
                        }
                )
            }
        }
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
}

@Composable
private fun BirthdayCard(item: BirthdayAgendaItem) {
    val accentColor = Color(item.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactPhoto(
                photoUri = item.photoUri,
                fallbackColor = accentColor,
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Lädt das Kontaktbild (content://-Uri aus den Geräte-Kontakten) ohne zusätzliche Bild-Bibliothek
 * (FOSS-Minimalismus) direkt über den ContentResolver; ohne Bild erscheint ein Torten-Icon im
 * Akzentkreis.
 */
@Composable
private fun ContactPhoto(photoUri: String?, fallbackColor: Color, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photoUri) {
        bitmap = if (photoUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier.background(fallbackColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val luminance = 0.299 * fallbackColor.red + 0.587 * fallbackColor.green + 0.114 * fallbackColor.blue
            val iconTint = if (luminance > 0.6) Color(0xFF212121) else Color.White
            Icon(Icons.Filled.Cake, contentDescription = null, tint = iconTint)
        }
    }
}

@Composable
private fun TaskCard(item: TaskAgendaItem, onToggle: (Boolean) -> Unit) {
    val accentColor = Color(item.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.completed, onCheckedChange = onToggle)
            Column {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else null
                )
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AllDayCard(item: AllDayAgendaItem) {
    val accentColor = Color(item.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(12.dp).background(accentColor, CircleShape))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TimedCard(item: TimedAgendaItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(56.dp)) {
                Text(formatMinuteOfDay(item.startMinuteOfDay), style = MaterialTheme.typography.labelLarge)
                Text(
                    formatMinuteOfDay(item.endMinuteOfDay % (24 * 60)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(Color(item.colorArgb), RoundedCornerShape(2.dp))
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatMinuteOfDay(totalMinutes: Int): String {
    val hour = (totalMinutes / 60) % 24
    val minute = totalMinutes % 60
    return "%02d:%02d".format(hour, minute)
}

private fun formattedDayTitle(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)).replaceFirstChar { it.uppercase() }
