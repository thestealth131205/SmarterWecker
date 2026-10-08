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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftEntity

/**
 * Vorlagen-Detailansicht eines Jobs mit zwei Tabs: "Schichten" (Schritt 9) und
 * "Rotationen" (Schritt 10, verweist auf den Rotations-Editor).
 */
@Composable
fun JobDetailScreen(
    jobName: String,
    onOpenRotationEditor: (rotationId: Long?) -> Unit,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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

@Composable
private fun ShiftsTab(viewModel: JobDetailViewModel) {
    val shifts by viewModel.shifts.collectAsState()
    var editingShift by remember { mutableStateOf<ShiftEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (shifts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch keine Schichten angelegt")
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
    val startDate = remember(rotation.startDateEpochDay) { java.time.LocalDate.ofEpochDay(rotation.startDateEpochDay) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp, 40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(rotation.name, style = MaterialTheme.typography.titleMedium)
                Text("Start: $startDate", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Rotation löschen")
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}
