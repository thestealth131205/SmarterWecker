package de.letheapp.nocturn.ocr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import de.letheapp.nocturn.calendar.SHIFT_COLOR_PALETTE
import de.letheapp.nocturn.data.db.JobMode
import java.io.File

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * Foto-Import-Screen: Foto aufnehmen → OCR → Korrektur-Liste → Übernahme (siehe CLAUDE.md
 * Kernfunktion 5). Die Tabellen-Spaltenstruktur eines Fotos (z. B. welcher Wochentag) kann
 * reiner Fließtext-OCR nicht zuverlässig entnehmen, daher weist der Nutzer das im
 * Schüler/Student-Modus pro Zeile selbst einem Wochentag zu (siehe PLAN.md, bewusste
 * Vereinfachung).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoImportScreen(
    onBack: () -> Unit,
    viewModel: PhotoImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var editingRow by remember { mutableStateOf<ParsedScheduleRow?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
            if (bitmap != null) viewModel.processImage(bitmap)
        }
    }

    LaunchedEffect(viewModel.importFinished) {
        if (viewModel.importFinished) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foto-Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (viewModel.rows.isNotEmpty()) {
                        IconButton(onClick = { viewModel.confirmImport() }) {
                            Icon(Icons.Filled.Check, contentDescription = "Übernehmen")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Button(
                onClick = {
                    val uri = createCaptureUri(context)
                    pendingCaptureUri = uri
                    captureLauncher.launch(uri)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Text(" Foto aufnehmen", modifier = Modifier.padding(start = 8.dp))
            }

            viewModel.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (viewModel.isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Erkenne Text…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            } else if (viewModel.rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch kein Foto ausgewertet")
                }
            } else {
                Text(
                    text = "Erkannte Einträge prüfen und bei Bedarf korrigieren:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.rows, key = { it.id }) { row ->
                        ScheduleRowCard(
                            row = row,
                            isStudentMode = viewModel.jobMode == JobMode.STUDENT,
                            onToggleIncluded = { viewModel.updateRow(row.copy(included = it)) },
                            onClick = { editingRow = row },
                            onDelete = { viewModel.removeRow(row) }
                        )
                    }
                }
            }
        }
    }

    editingRow?.let { row ->
        ScheduleRowEditDialog(
            row = row,
            isStudentMode = viewModel.jobMode == JobMode.STUDENT,
            onConfirm = { viewModel.updateRow(it); editingRow = null },
            onDismiss = { editingRow = null }
        )
    }
}

private fun createCaptureUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "ocr_captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun ScheduleRowCard(
    row: ParsedScheduleRow,
    isStudentMode: Boolean,
    onToggleIncluded: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.included, onCheckedChange = onToggleIncluded)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(row.colorArgb), CircleShape)
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                val weekdayPrefix = if (isStudentMode) "${WEEKDAY_LABELS[row.weekday]} · " else ""
                Text(
                    "$weekdayPrefix%02d:%02d–%02d:%02d".format(row.startHour, row.startMinute, row.endHour, row.endMinute),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eintrag entfernen")
            }
        }
    }
}

@Composable
private fun ScheduleRowEditDialog(
    row: ParsedScheduleRow,
    isStudentMode: Boolean,
    onConfirm: (ParsedScheduleRow) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(row.label) }
    var colorArgb by remember { mutableStateOf(row.colorArgb) }
    var startHour by remember { mutableStateOf(row.startHour) }
    var startMinute by remember { mutableStateOf(row.startMinute) }
    var endHour by remember { mutableStateOf(row.endHour) }
    var endMinute by remember { mutableStateOf(row.endMinute) }
    var weekday by remember { mutableStateOf(row.weekday) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Bezeichnung") },
                    singleLine = true
                )

                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHIFT_COLOR_PALETTE.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(color, CircleShape)
                                .clickable { colorArgb = color.toArgb() }
                        )
                    }
                }

                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }) {
                        Text("Start %02d:%02d".format(startHour, startMinute))
                    }
                    OutlinedButton(onClick = { showEndPicker = true }) {
                        Text("Ende %02d:%02d".format(endHour, endMinute))
                    }
                }

                if (isStudentMode) {
                    Text(
                        "Wochentag",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAY_LABELS.forEachIndexed { index, dayLabel ->
                            val selected = weekday == index
                            OutlinedButton(onClick = { weekday = index }) {
                                Text(if (selected) "✓ $dayLabel" else dayLabel)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        row.copy(
                            label = label.trim().ifBlank { row.label },
                            colorArgb = colorArgb,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            weekday = weekday
                        )
                    )
                }
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )

    if (showStartPicker) {
        NocturnTimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m -> startHour = h; startMinute = m; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        NocturnTimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m -> endHour = h; endMinute = m; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}
