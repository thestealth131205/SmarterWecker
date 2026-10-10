package de.letheapp.nocturn.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.ShiftEntity

/**
 * Zwischenschritt beim Anlegen einer neuen Rotation über den "+"-Dialog ("Rotation anlegen" →
 * Job wählen/anlegen → **dieser Screen** → [RotationEditorScreen]): Schichten gehören zum Job,
 * nicht zur Rotation, daher legt man sie hier optional an, bevor man zum Rotationsraster
 * weitergeht – wer schon Schichten für diesen Job hat oder sie später über "Schichten" im
 * Kalender-Menü ergänzen will, tippt direkt auf "Weiter"/"Überspringen".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftSetupScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val job by viewModel.job.collectAsState()
    val shifts by viewModel.shifts.collectAsState()
    var editingShift by remember { mutableStateOf<ShiftEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schichten für ${job?.name.orEmpty()}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    TextButton(onClick = onContinue) {
                        Text(if (shifts.isEmpty()) "Überspringen" else "Weiter")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (shifts.isEmpty()) {
                Text(
                    "Lege hier die Schichten an, die in deiner Rotation vorkommen sollen " +
                        "(z. B. Früh/Spät/Nacht/Frei), oder überspringe diesen Schritt und " +
                        "lege sie später über „Schichten“ im Kalender-Menü an.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center
                )
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
