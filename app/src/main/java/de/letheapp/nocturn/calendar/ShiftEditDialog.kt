package de.letheapp.nocturn.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import de.letheapp.nocturn.alarm.NocturnTimePickerDialog
import de.letheapp.nocturn.data.db.ShiftEntity

/**
 * Anlegen/Bearbeiten einer Schicht: Name, Farbe (Palette), Start-/Endzeit — die
 * Dauer wird daraus berechnet (Nachtschichten über Mitternacht werden unterstützt).
 */
@Composable
fun ShiftEditDialog(
    jobId: Long,
    existing: ShiftEntity?,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Neue Schicht" else "Schicht bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (z. B. Frühschicht)") },
                    singleLine = true
                )

                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHIFT_COLOR_PALETTE.forEach { color ->
                        ColorSwatch(
                            color = color,
                            selected = color.toArgb() == colorArgb,
                            onClick = { colorArgb = color.toArgb() }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val durationMinutes = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                    val normalizedDuration = if (durationMinutes <= 0) durationMinutes + 24 * 60 else durationMinutes
                    onConfirm(
                        ShiftEntity(
                            id = existing?.id ?: 0,
                            jobId = jobId,
                            name = name.trim(),
                            colorArgb = colorArgb,
                            startHour = startHour,
                            startMinute = startMinute,
                            durationMinutes = normalizedDuration
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
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
