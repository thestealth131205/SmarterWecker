package de.letheapp.nocturn.calendar

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Ausstehende Löschbestätigung, siehe [DeleteConfirmDialog]. */
private data class PendingDeletion(val label: String, val onConfirm: () -> Unit)

/**
 * Tagesansicht beim Antippen eines Kalendertags mit Termin/Feiertag/Schicht (CLAUDE.md
 * Kernfunktion 6, an die Google-Kalender-Tagesansicht angelehnt): ganztägige Einträge als
 * Banner oben, darunter nach Abschnitten gruppiert — Termine, Erinnerungen, Aufgaben,
 * Schichten (in dieser Reihenfolge) — und innerhalb jedes Abschnitts zeitlich sortiert.
 * Antippen einer Termin-/Aufgaben-/Erinnerungs-/Schicht-Kachel klappt sie auf und zeigt
 * Bearbeiten-/Löschen-Icons; Feiertage/Ferien/importierte ICS-Termine bleiben reine
 * Anzeigedaten ohne diese Aktionen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDayScreen(
    onBack: () -> Unit,
    onAddAppointment: (LocalDate) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onAddShift: (LocalDate) -> Unit,
    onEditAppointment: (appointmentId: Long) -> Unit,
    onEditTask: (taskId: Long) -> Unit,
    onEditShift: (jobId: Long, jobName: String, shiftId: Long) -> Unit,
    onOpenRotationEditor: (jobId: Long) -> Unit,
    onOpenJobTimetable: (jobId: Long, jobName: String) -> Unit,
    viewModel: CalendarDayViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showRotationPicker by remember { mutableStateOf(false) }
    var attachmentsDialogItem by remember { mutableStateOf<DayAgendaItem?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<PendingDeletion?>(null) }
    val toggleExpand: (String) -> Unit = { key -> expandedKey = if (expandedKey == key) null else key }

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
                val reminderItems = items.filterIsInstance<ReminderAgendaItem>().sortedBy { it.triggerMinuteOfDay }
                val allDayItems = items.filterIsInstance<AllDayAgendaItem>()
                val timedItems = items.filterIsInstance<TimedAgendaItem>().sortedBy { it.startMinuteOfDay }

                val infoAllDay = allDayItems.filter { it.kind == AgendaItemKind.HOLIDAY }
                val appointmentAllDay = allDayItems.filter {
                    it.kind == AgendaItemKind.APPOINTMENT || it.kind == AgendaItemKind.IMPORTED
                }
                val appointmentTimed = timedItems.filter {
                    it.kind == AgendaItemKind.APPOINTMENT || it.kind == AgendaItemKind.IMPORTED
                }
                val shiftTimed = timedItems.filter { it.kind == AgendaItemKind.SHIFT }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(birthdayItems, key = { it.agendaKey }) { BirthdayCard(it) }
                    items(infoAllDay, key = { it.agendaKey }) { AllDayCard(it) }

                    if (appointmentAllDay.isNotEmpty() || appointmentTimed.isNotEmpty()) {
                        item { SectionLabel("Termine") }
                        items(appointmentAllDay, key = { it.agendaKey }) { item ->
                            AllDayCard(
                                item = item,
                                expanded = expandedKey == item.agendaKey,
                                onToggleExpand = { toggleExpand(item.agendaKey) },
                                onShowAttachments = { attachmentsDialogItem = it },
                                onEdit = item.appointmentId?.let { id -> { onEditAppointment(id) } },
                                onDelete = item.appointmentId?.let { id ->
                                    {
                                        pendingDeletion = PendingDeletion("Termin") { viewModel.deleteAppointment(id) }
                                    }
                                }
                            )
                        }
                        items(appointmentTimed, key = { it.agendaKey }) { item ->
                            TimedCard(
                                item = item,
                                expanded = expandedKey == item.agendaKey,
                                onToggleExpand = { toggleExpand(item.agendaKey) },
                                onShowAttachments = { attachmentsDialogItem = it },
                                onEdit = item.appointmentId?.let { id -> { onEditAppointment(id) } },
                                onDelete = item.appointmentId?.let { id ->
                                    {
                                        pendingDeletion = PendingDeletion("Termin") { viewModel.deleteAppointment(id) }
                                    }
                                }
                            )
                        }
                    }

                    if (reminderItems.isNotEmpty()) {
                        item { SectionLabel("Erinnerungen") }
                        items(reminderItems, key = { it.agendaKey }) { item ->
                            ReminderCard(
                                item = item,
                                expanded = expandedKey == item.agendaKey,
                                onToggleExpand = { toggleExpand(item.agendaKey) },
                                onEdit = { onEditAppointment(item.appointmentId) },
                                onDelete = {
                                    pendingDeletion = PendingDeletion("Erinnerung") { viewModel.deleteReminder(item.reminderId) }
                                }
                            )
                        }
                    }

                    if (taskItems.isNotEmpty()) {
                        item { SectionLabel("Aufgaben") }
                        items(taskItems, key = { it.agendaKey }) { item ->
                            TaskCard(
                                item = item,
                                onToggle = { completed -> viewModel.toggleTaskCompleted(item.taskId, completed) },
                                expanded = expandedKey == item.agendaKey,
                                onToggleExpand = { toggleExpand(item.agendaKey) },
                                onEdit = { onEditTask(item.taskId) },
                                onDelete = {
                                    pendingDeletion = PendingDeletion("Aufgabe") { viewModel.deleteTask(item.taskId) }
                                }
                            )
                        }
                    }

                    if (shiftTimed.isNotEmpty()) {
                        item { SectionLabel("Schichten") }
                        items(shiftTimed, key = { it.agendaKey }) { item ->
                            val jobId = item.jobId
                            val shiftId = item.shiftId
                            val ref = item.occurrenceRef
                            TimedCard(
                                item = item,
                                expanded = expandedKey == item.agendaKey,
                                onToggleExpand = { toggleExpand(item.agendaKey) },
                                onShowAttachments = { attachmentsDialogItem = it },
                                onEdit = if (jobId != null && shiftId != null) {
                                    { onEditShift(jobId, item.subtitle.orEmpty(), shiftId) }
                                } else null,
                                onDelete = ref?.let {
                                    { pendingDeletion = PendingDeletion("Schicht") { viewModel.deleteShiftOccurrence(it) } }
                                }
                            )
                        }
                    }
                }
            }

            attachmentsDialogItem?.let { item ->
                AttachmentsDialog(item = item, onDismiss = { attachmentsDialogItem = null })
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

    pendingDeletion?.let { deletion ->
        DeleteConfirmDialog(
            label = deletion.label,
            onConfirm = {
                deletion.onConfirm()
                pendingDeletion = null
                expandedKey = null
            },
            onDismiss = { pendingDeletion = null }
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
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun DeleteConfirmDialog(label: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label löschen?") },
        text = { Text("Dieser Eintrag wird endgültig gelöscht.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Bearbeiten-/Löschen-Zeile einer aufgeklappten Kachel — fehlende Aktionen (z. B. bei reinen Anzeigedaten) werden ausgeblendet. */
@Composable
private fun AgendaActionRow(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    if (onEdit == null && onDelete == null) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        onEdit?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten") }
        }
        onDelete?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
        }
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
private fun TaskCard(
    item: TaskAgendaItem,
    onToggle: (Boolean) -> Unit,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val accentColor = Color(item.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.18f))
    ) {
        Column {
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
            if (expanded) {
                AgendaActionRow(onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun ReminderCard(
    item: ReminderAgendaItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(56.dp)) {
                    Text(formatMinuteOfDay(item.triggerMinuteOfDay), style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(item.colorArgb), RoundedCornerShape(2.dp))
                )
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    item.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expanded) {
                AgendaActionRow(onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun AllDayCard(
    item: AllDayAgendaItem,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onShowAttachments: (AllDayAgendaItem) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val accentColor = Color(item.colorArgb)
    val borderModifier = if (item.highPriority) {
        Modifier.border(2.dp, Color(0xFFD32F2F), MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(
        modifier = Modifier.fillMaxWidth().then(borderModifier).clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.18f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(12.dp).background(accentColor, CircleShape))
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    item.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.attachments.isNotEmpty()) {
                    AttachmentBadge(count = item.attachments.size, onClick = { onShowAttachments(item) })
                }
            }
            if (expanded) {
                AgendaActionRow(onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun TimedCard(
    item: TimedAgendaItem,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onShowAttachments: (TimedAgendaItem) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val borderModifier = if (item.highPriority) {
        Modifier.border(2.dp, Color(0xFFD32F2F), MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(modifier = Modifier.fillMaxWidth().then(borderModifier).clickable(onClick = onToggleExpand)) {
        Column {
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
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    item.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.attachments.isNotEmpty()) {
                    AttachmentBadge(count = item.attachments.size, onClick = { onShowAttachments(item) })
                }
            }
            if (expanded) {
                AgendaActionRow(onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun AttachmentBadge(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Zeigt die Anhänge eines Termins (siehe [AttachmentRef]); Tippen öffnet die Datei über die registrierte Fremd-App. */
@Composable
private fun AttachmentsDialog(item: DayAgendaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val attachments = when (item) {
        is TimedAgendaItem -> item.attachments
        is AllDayAgendaItem -> item.attachments
        else -> emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anhänge") },
        text = {
            Column {
                attachments.forEach { attachment ->
                    Text(
                        attachment.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(attachment.uri), context.contentResolver.getType(Uri.parse(attachment.uri)))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

private fun formatMinuteOfDay(totalMinutes: Int): String {
    val hour = (totalMinutes / 60) % 24
    val minute = totalMinutes % 60
    return "%02d:%02d".format(hour, minute)
}

private fun formattedDayTitle(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)).replaceFirstChar { it.uppercase() }
