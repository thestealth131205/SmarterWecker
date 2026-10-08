package de.letheapp.nocturn.alarm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 24h-Zeitauswahl im Google-Uhr-Stil: antippbares/ziehbares Zifferblatt, umschaltbar auf
 * direkte Zahleneingabe über die Tastatur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NocturnTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    var keyboardMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weckzeit") },
        text = {
            if (keyboardMode) TimeInput(state = state) else TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Übernehmen") }
        },
        dismissButton = {
            IconButton(onClick = { keyboardMode = !keyboardMode }) {
                Icon(
                    imageVector = if (keyboardMode) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                    contentDescription = "Eingabemodus wechseln"
                )
            }
        }
    )
}
