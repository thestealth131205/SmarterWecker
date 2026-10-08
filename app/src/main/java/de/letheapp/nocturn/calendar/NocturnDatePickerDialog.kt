package de.letheapp.nocturn.calendar

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tages-Auswahl (ohne Uhrzeit) für Termin-/Schicht-Formulare, z. B. beim "+"-Eintrag im Kalender. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NocturnDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val utc = ZoneId.of("UTC")
    val initialMillis = initialDate.atStartOfDay(utc).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: initialMillis
                onConfirm(Instant.ofEpochMilli(millis).atZone(utc).toLocalDate())
            }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    ) {
        DatePicker(state = state)
    }
}
