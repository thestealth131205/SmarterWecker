package de.letheapp.nocturn.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.calendar.NocturnDatePickerDialog
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Druckbarer Kalender-Export (siehe CLAUDE.md, Kernfunktion 6): Zeitraum, Seitenlayout und
 * Dateiformat wählen, dann über das Android-Teilen-Menü direkt ausdrucken/speichern/versenden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarExportScreen(
    onBack: () -> Unit,
    viewModel: CalendarExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var editingStartDate by remember { mutableStateOf(false) }
    var editingEndDate by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    LaunchedEffect(state.exportedFiles) {
        val uris = state.exportedFiles
        if (uris != null) {
            shareFiles(context, uris, state.format.mimeType)
            viewModel.consumeExportedFiles()
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null) {
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalender exportieren") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Exportiert Schichten, Termine und Feiertage/Ferien aus dem gewählten Zeitraum als druckbare Datei.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { editingStartDate = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Von", modifier = Modifier.weight(1f))
                Text(state.startDate.format(dateFormatter), style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { editingEndDate = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bis", modifier = Modifier.weight(1f))
                Text(state.endDate.format(dateFormatter), style = MaterialTheme.typography.bodyMedium)
            }

            Text("Seitenlayout", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.layout == ExportLayout.ONE_PAGE_TOTAL,
                    onClick = { viewModel.setLayout(ExportLayout.ONE_PAGE_TOTAL) },
                    label = { Text("Alles auf eine Seite") }
                )
                FilterChip(
                    selected = state.layout == ExportLayout.ONE_PAGE_PER_MONTH,
                    onClick = { viewModel.setLayout(ExportLayout.ONE_PAGE_PER_MONTH) },
                    label = { Text("Jeder Monat eine Seite") }
                )
            }

            Text("Dateiformat", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.format == ExportFormat.PDF,
                    onClick = { viewModel.setFormat(ExportFormat.PDF) },
                    label = { Text("PDF") }
                )
                FilterChip(
                    selected = state.format == ExportFormat.PNG,
                    onClick = { viewModel.setFormat(ExportFormat.PNG) },
                    label = { Text("PNG") }
                )
                FilterChip(
                    selected = state.format == ExportFormat.JPG,
                    onClick = { viewModel.setFormat(ExportFormat.JPG) },
                    label = { Text("JPG") }
                )
            }
            if (state.format == ExportFormat.PNG) {
                Text(
                    "Empfehlung: PNG liefert beim Ausdrucken schärfere Linien und Schrift als JPG, da es verlustfrei komprimiert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = viewModel::export,
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (state.isExporting) "Wird erstellt…" else "Exportieren")
            }
        }
    }

    if (editingStartDate) {
        NocturnDatePickerDialog(
            initialDate = state.startDate,
            onConfirm = { date ->
                viewModel.setStartDate(date)
                editingStartDate = false
            },
            onDismiss = { editingStartDate = false }
        )
    }

    if (editingEndDate) {
        NocturnDatePickerDialog(
            initialDate = state.endDate,
            onConfirm = { date ->
                viewModel.setEndDate(date)
                editingEndDate = false
            },
            onDismiss = { editingEndDate = false }
        )
    }
}

private fun shareFiles(context: Context, uris: List<Uri>, mimeType: String) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Kalender-Export teilen/drucken"))
}
