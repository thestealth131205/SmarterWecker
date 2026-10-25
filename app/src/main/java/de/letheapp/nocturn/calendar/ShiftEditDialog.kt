package de.letheapp.nocturn.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.iobroker.IoBrokerActionPickerDialog
import de.letheapp.nocturn.iobroker.IoBrokerDataPoint
import de.letheapp.nocturn.iobroker.ioBrokerAction1
import de.letheapp.nocturn.iobroker.ioBrokerAction2
import de.letheapp.nocturn.iobroker.ioBrokerScriptAction
import de.letheapp.nocturn.iobroker.summaryLabel
import de.letheapp.nocturn.iobroker.withIoBrokerAction1
import de.letheapp.nocturn.iobroker.withIoBrokerAction2
import de.letheapp.nocturn.iobroker.withIoBrokerScriptAction

// "Freischicht" statt "Frei", da ein Tag ohne zugewiesene Schicht in der Rotation bereits
// "Frei" heißt – eine gleichnamige Schicht würde beides verwechselbar machen.
private val SHIFT_NAME_SUGGESTIONS = listOf("Urlaub", "Freischicht", "Früh", "Nacht", "Spät")

/** Tippt man auf eine Schnellauswahl, werden zusätzlich zum Titel auch die typischen Start-/Endzeiten vorbelegt. */
private val SHIFT_DEFAULT_TIMES: Map<String, IntArray> = mapOf(
    "Früh" to intArrayOf(6, 0, 14, 0),
    "Spät" to intArrayOf(14, 0, 22, 0),
    "Nacht" to intArrayOf(22, 0, 6, 0)
)

/**
 * Anlegen/Bearbeiten einer Schicht als eigener Vollbild-Screen (statt Dialog) nach dem
 * Vorbild gängiger Schichtplan-Apps: Titel/Farbe-Karte, darunter "Standardwerte" mit
 * Start-/Endzeit, darunter die NocTurn-eigenen Weckzeit-Abstände.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditDialog(
    jobId: Long,
    existing: ShiftEntity?,
    ioBrokerConnected: Boolean = false,
    ioBrokerDataPoints: List<IoBrokerDataPoint> = emptyList(),
    onRefreshIoBrokerDataPoints: () -> Unit = {},
    onConfirm: (ShiftEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var colorArgb by remember { mutableStateOf(existing?.colorArgb ?: DEFAULT_SHIFT_COLOR_ARGB) }
    var startHour by remember { mutableStateOf(existing?.startHour ?: 6) }
    var startMinute by remember { mutableStateOf(existing?.startMinute ?: 0) }
    val initialEnd = remember {
        val totalEnd = (existing?.startHour ?: 6) * 60 + (existing?.startMinute ?: 0) + (existing?.durationMinutes ?: 480)
        (totalEnd / 60) % 24 to totalEnd % 60
    }
    var endHour by remember { mutableStateOf(initialEnd.first) }
    var endMinute by remember { mutableStateOf(initialEnd.second) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var earliestWakeOffsetText by remember { mutableStateOf((existing?.earliestWakeOffsetMinutes ?: 0).toString()) }
    var latestWakeupOffsetText by remember { mutableStateOf((existing?.latestWakeupOffsetMinutes ?: 0).toString()) }
    var wakeupAlarmEnabled by remember { mutableStateOf(existing?.latestWakeupHour != null) }
    var wakeupAlarmHour by remember { mutableStateOf(existing?.latestWakeupHour ?: startHour) }
    var wakeupAlarmMinute by remember { mutableStateOf(existing?.latestWakeupMinute ?: 0) }
    var wakeupAlarmCustomized by remember { mutableStateOf(existing?.latestWakeupHour != null) }
    var showWakeupAlarmPicker by remember { mutableStateOf(false) }
    var ioBrokerAction1Config by remember { mutableStateOf(existing?.ioBrokerAction1()) }
    var ioBrokerAction2Config by remember { mutableStateOf(existing?.ioBrokerAction2()) }
    var ioBrokerScriptActionConfig by remember { mutableStateOf(existing?.ioBrokerScriptAction()) }
    var showIoBrokerDialog by remember { mutableStateOf<Int?>(null) }

    // Solange der Nutzer die Weckzeit nicht selbst über den Zeit-Dialog festgelegt hat, folgt sie
    // der Start-Zeit (auch wenn diese erst später über einen Schnellauswahl-Chip wie "Spät" gesetzt
    // wird) – sonst bliebe sie beim anfänglichen Default (6 Uhr) hängen, während die Schicht z. B.
    // auf 14 Uhr umgestellt wird.
    LaunchedEffect(startHour, startMinute) {
        if (!wakeupAlarmCustomized) {
            wakeupAlarmHour = startHour
            wakeupAlarmMinute = startMinute
        }
    }

    fun save() {
        val durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
        val normalizedDuration = if (durationMinutes <= 0) durationMinutes + 24 * 60 else durationMinutes
        val earliestWakeOffset = earliestWakeOffsetText.toIntOrNull() ?: 0
        // "Spätestens aufstehen" darf nicht nach dem Wecker-Klingeln selbst liegen.
        val latestWakeupOffset = (latestWakeupOffsetText.toIntOrNull() ?: 0).coerceAtMost(earliestWakeOffset)
        var result = ShiftEntity(
                id = existing?.id ?: 0,
                jobId = jobId,
                name = name.trim(),
                colorArgb = colorArgb,
                startHour = startHour,
                startMinute = startMinute,
                durationMinutes = normalizedDuration,
                earliestWakeOffsetMinutes = earliestWakeOffset,
                latestWakeupOffsetMinutes = latestWakeupOffset,
                latestWakeupHour = if (wakeupAlarmEnabled) wakeupAlarmHour else null,
                latestWakeupMinute = if (wakeupAlarmEnabled) wakeupAlarmMinute else null
            )
        result = result.withIoBrokerAction1(ioBrokerAction1Config)
        result = result.withIoBrokerAction2(ioBrokerAction2Config)
        result = result.withIoBrokerScriptAction(ioBrokerScriptActionConfig)
        onConfirm(result)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Schicht", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    actions = {
                        TextButton(onClick = { save() }, enabled = name.isNotBlank()) {
                            Text("Speichern")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Titel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SHIFT_NAME_SUGGESTIONS.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        name = suggestion
                                        SHIFT_DEFAULT_TIMES[suggestion]?.let { (sh, sm, eh, em) ->
                                            startHour = sh
                                            startMinute = sm
                                            endHour = eh
                                            endMinute = em
                                        }
                                    },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SHIFT_COLOR_PALETTE.forEach { color ->
                                ColorSwatch(
                                    color = color,
                                    selected = color.toArgb() == colorArgb,
                                    onClick = { colorArgb = color.toArgb() }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Standardwerte",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                )
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column {
                        ShiftFormRow(
                            label = "Start",
                            value = "%02d:%02d".format(startHour, startMinute),
                            onClick = { showStartPicker = true },
                            showChevron = false
                        )
                        ShiftFormRow(
                            label = "Ende",
                            value = "%02d:%02d".format(endHour, endMinute),
                            onClick = { showEndPicker = true },
                            showChevron = false
                        )
                    }
                }

                Text(
                    text = "Weckzeit-Abstand vor Schichtbeginn (0 = genau zum Schichtbeginn)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                )
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = earliestWakeOffsetText,
                                onValueChange = { earliestWakeOffsetText = it.filter(Char::isDigit) },
                                label = { Text("Frühestes Wecken (Min.)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = latestWakeupOffsetText,
                                onValueChange = { latestWakeupOffsetText = it.filter(Char::isDigit) },
                                label = { Text("Spätestens aufstehen (Min.)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Text(
                    text = "Automatischer Wecker im Wecker-Screen (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                )
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Zeigt immer die Weckzeit für die nächste anstehende Schicht an",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = wakeupAlarmEnabled, onCheckedChange = { wakeupAlarmEnabled = it })
                        }
                        if (wakeupAlarmEnabled) {
                            ShiftFormRow(
                                label = "Spätestens aufstehen",
                                value = "%02d:%02d".format(wakeupAlarmHour, wakeupAlarmMinute),
                                onClick = { showWakeupAlarmPicker = true },
                                showChevron = false
                            )
                        }
                    }
                }

                Text(
                    text = "Smart-Home bei Notfall-Alarm (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                )
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column {
                        ShiftFormRow(
                            label = "ioBroker-Datenpunkt 1 (z. B. Lampe)",
                            value = ioBrokerAction1Config?.summaryLabel(),
                            onClick = {
                                onRefreshIoBrokerDataPoints()
                                showIoBrokerDialog = 1
                            },
                            showChevron = false
                        )
                        ShiftFormRow(
                            label = "ioBroker-Datenpunkt 2 (z. B. Rollo)",
                            value = ioBrokerAction2Config?.summaryLabel(),
                            onClick = {
                                onRefreshIoBrokerDataPoints()
                                showIoBrokerDialog = 2
                            },
                            showChevron = false
                        )
                        ShiftFormRow(
                            label = "Skript auslösen (z. B. Radio)",
                            value = ioBrokerScriptActionConfig?.summaryLabel(),
                            onClick = {
                                onRefreshIoBrokerDataPoints()
                                showIoBrokerDialog = 3
                            },
                            showChevron = false
                        )
                    }
                }

                // Bodenabschluss statt eines zweiten Speichern-Buttons: der in der Topbar
                // war am unteren Rand des Dialog-Fensters (hinter der System-Navigationsleiste)
                // nicht sichtbar/erreichbar.
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

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

    if (showWakeupAlarmPicker) {
        NocturnTimePickerDialog(
            initialHour = wakeupAlarmHour,
            initialMinute = wakeupAlarmMinute,
            onConfirm = { h, m -> wakeupAlarmHour = h; wakeupAlarmMinute = m; wakeupAlarmCustomized = true; showWakeupAlarmPicker = false },
            onDismiss = { showWakeupAlarmPicker = false }
        )
    }

    if (showIoBrokerDialog != null) {
        val slot = showIoBrokerDialog
        IoBrokerActionPickerDialog(
            title = when (slot) {
                1 -> "ioBroker-Datenpunkt 1 (z. B. Lampe)"
                2 -> "ioBroker-Datenpunkt 2 (z. B. Rollo)"
                else -> "Skript auslösen (z. B. Radio)"
            },
            connected = ioBrokerConnected,
            dataPoints = ioBrokerDataPoints,
            current = when (slot) {
                1 -> ioBrokerAction1Config
                2 -> ioBrokerAction2Config
                else -> ioBrokerScriptActionConfig
            },
            onConfirm = { config ->
                when (slot) {
                    1 -> ioBrokerAction1Config = config
                    2 -> ioBrokerAction2Config = config
                    else -> ioBrokerScriptActionConfig = config
                }
                showIoBrokerDialog = null
            },
            onDismiss = { showIoBrokerDialog = null }
        )
    }
}

@Composable
private fun ShiftFormRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (showChevron) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(width = if (selected) 3.dp else 0.dp, color = Color.Black, shape = CircleShape)
            .clickable(onClick = onClick)
    ) {}
}
