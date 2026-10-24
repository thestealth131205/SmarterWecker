package de.letheapp.nocturn.iobroker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog zur Auswahl/Konfiguration einer einzelnen ioBroker-Aktion (siehe [IoBrokerActionConfig]),
 * wiederverwendet für alle drei Aktions-Slots sowohl im Alarm- als auch im Schicht-Bearbeiten-
 * Screen. Zweistufig: erst einen Datenpunkt wählen, danach je nach zuletzt gelesenem Wert Ein/Aus
 * oder Schrittweite+Endwert festlegen — die Modus-Chips lassen sich jederzeit manuell
 * übersteuern, falls die automatische Erkennung danebenliegt.
 */
@Composable
fun IoBrokerActionPickerDialog(
    title: String,
    connected: Boolean,
    dataPoints: List<IoBrokerDataPoint>,
    current: IoBrokerActionConfig?,
    onConfirm: (IoBrokerActionConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(dataPoints.find { it.id == current?.dataPointId }) }
    var mode by remember { mutableStateOf(current?.mode ?: IoBrokerActionMode.ON_OFF) }
    var turnOn by remember { mutableStateOf(current?.turnOn ?: true) }
    var rampStepText by remember { mutableStateOf(current?.rampStep?.let { formatIoBrokerNumber(it) } ?: "") }
    var rampTargetText by remember { mutableStateOf(current?.rampTarget?.let { formatIoBrokerNumber(it) } ?: "") }

    val filtered = if (search.isBlank()) dataPoints else dataPoints.filter {
        it.name.contains(search, ignoreCase = true) || it.id.contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val point = selected
                if (!connected) {
                    Text(
                        "Noch kein ioBroker-Server verbunden. Unter dem 3-Punkte-Menü \"Smart-Home " +
                            "(ioBroker)\" zuerst eine Verbindung einrichten."
                    )
                } else if (point == null) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Suche") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(if (current == null) "✓ Keine Verknüpfung" else "Keine Verknüpfung")
                    }
                    if (filtered.isEmpty()) {
                        Text("Keine Datenpunkte gefunden", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(filtered) { dataPoint ->
                                ListItem(
                                    headlineContent = { Text(dataPoint.name) },
                                    supportingContent = { Text(dataPoint.id, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selected = dataPoint
                                        val looksNumeric = dataPoint.value?.toDoubleOrNull() != null &&
                                            !dataPoint.value.equals("true", ignoreCase = true) &&
                                            !dataPoint.value.equals("false", ignoreCase = true)
                                        mode = if (looksNumeric) IoBrokerActionMode.RAMP else IoBrokerActionMode.ON_OFF
                                        turnOn = true
                                        rampStepText = ""
                                        rampTargetText = ""
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(point.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        point.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    point.value?.let {
                        Text(
                            "Aktueller Wert: $it",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                    Row(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                        FilterChip(
                            selected = mode == IoBrokerActionMode.ON_OFF,
                            onClick = { mode = IoBrokerActionMode.ON_OFF },
                            label = { Text("Ein/Aus") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = mode == IoBrokerActionMode.RAMP,
                            onClick = { mode = IoBrokerActionMode.RAMP },
                            label = { Text("Stufenweise erhöhen") }
                        )
                    }
                    if (mode == IoBrokerActionMode.ON_OFF) {
                        Row {
                            FilterChip(
                                selected = turnOn,
                                onClick = { turnOn = true },
                                label = { Text("Einschalten") },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            FilterChip(
                                selected = !turnOn,
                                onClick = { turnOn = false },
                                label = { Text("Ausschalten") }
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = rampStepText,
                            onValueChange = { rampStepText = it },
                            label = { Text("Pro Schritt erhöhen um") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = rampTargetText,
                            onValueChange = { rampTargetText = it },
                            label = { Text("Endwert") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(onClick = { selected = null }) { Text("Anderen Datenpunkt wählen") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val point = selected
                    if (point == null) {
                        onConfirm(null)
                    } else {
                        onConfirm(
                            IoBrokerActionConfig(
                                dataPointId = point.id,
                                dataPointLabel = point.name,
                                mode = mode,
                                turnOn = turnOn,
                                rampStep = if (mode == IoBrokerActionMode.RAMP) rampStepText.replace(',', '.').toDoubleOrNull() else null,
                                rampTarget = if (mode == IoBrokerActionMode.RAMP) rampTargetText.replace(',', '.').toDoubleOrNull() else null
                            )
                        )
                    }
                },
                enabled = selected != null
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
