package de.letheapp.nocturn.holidays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Einstellt-Screen "Ferien & Feiertage" (3-Punkte-Menü der Alarmliste, siehe CLAUDE.md
 * Kernfunktion 6): Bundesland wählen, Datenquelle ist die kostenlose OpenHolidaysAPI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaySettingsScreen(onBack: () -> Unit, viewModel: HolidaySettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    var selectedState by remember(settings) {
        mutableStateOf(settings?.subdivisionCode?.let(GermanState::fromCode) ?: GermanState.NORDRHEIN_WESTFALEN)
    }
    var showPublic by remember(settings) { mutableStateOf(settings?.showPublicHolidays ?: true) }
    var showSchool by remember(settings) { mutableStateOf(settings?.showSchoolHolidays ?: true) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ferien & Feiertage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Zeigt gesetzliche Feiertage und Schulferien deines Bundeslands im Kalender an. " +
                    "Datenquelle: OpenHolidaysAPI (openholidaysapi.org), kostenlos und ohne Anmeldung.",
                style = MaterialTheme.typography.bodySmall
            )

            ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedState.displayName,
                    onValueChange = {},
                    label = { Text("Bundesland") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    GermanState.entries.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(state.displayName) },
                            onClick = {
                                selectedState = state
                                menuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Gesetzliche Feiertage anzeigen", modifier = Modifier.weight(1f))
                Switch(checked = showPublic, onCheckedChange = { showPublic = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Schulferien anzeigen", modifier = Modifier.weight(1f))
                Switch(checked = showSchool, onCheckedChange = { showSchool = it })
            }

            Button(
                onClick = {
                    viewModel.save(selectedState, showPublic, showSchool)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Speichern") }
        }
    }
}
