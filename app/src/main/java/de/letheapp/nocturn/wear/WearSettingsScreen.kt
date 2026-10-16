package de.letheapp.nocturn.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * "WearOS-Verwaltung" (3-Punkte-Menü der Kalender-Monatsansicht): wählt aus, welche Kalenderquellen
 * an eine gekoppelte WearOS-Smartwatch übertragen werden — unabhängig von der Handy-Anzeige, analog
 * zur bestehenden "Ferien & Feiertage"-Einstellung (Opt-in, Default aus). Jede Umschaltung wirkt
 * sofort (kein Speichern-Button), siehe [WearSettingsViewModel.toggle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearSettingsScreen(onBack: () -> Unit, viewModel: WearSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WearOS-Verwaltung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Watch, contentDescription = null)
                    Text(
                        "Wählt aus, was an eine gekoppelte WearOS-Smartwatch (benötigt die NocTurn-" +
                            "Begleit-App auf der Uhr) übertragen wird. Unabhängig davon, was im " +
                            "Kalender auf dem Handy angezeigt wird.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            item { SectionHeader("Kalender") }
            if (state.jobs.isEmpty()) {
                item { Text("Noch keine Jobs angelegt.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.jobs, key = { "job_${it.id}" }) { job ->
                ToggleRow(
                    title = job.name,
                    checked = job.id in state.enabledJobIds,
                    onCheckedChange = { viewModel.setJobEnabled(job.id, it) }
                )
            }

            item { SectionHeader("Rotationen") }
            if (state.rotations.isEmpty()) {
                item { Text("Noch keine Rotationen angelegt.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.rotations, key = { "rotation_${it.id}" }) { rotation ->
                ToggleRow(
                    title = rotation.name,
                    subtitle = rotation.jobName,
                    checked = rotation.id in state.enabledRotationIds,
                    onCheckedChange = { viewModel.setRotationEnabled(rotation.id, it) }
                )
            }

            item { SectionHeader("Geburtstage") }
            item {
                ToggleRow(
                    title = "Geburtstage anzeigen",
                    checked = state.birthdaysEnabled,
                    onCheckedChange = viewModel::setBirthdaysEnabled
                )
            }

            item { SectionHeader("Feiertage & Ferien") }
            if (state.holidaySubdivisionLabel == null) {
                item {
                    Text(
                        "Noch kein Bundesland unter \"Ferien & Feiertage\" hinterlegt — aktivieren " +
                            "kannst du hier trotzdem schon, übertragen wird aber erst, sobald dort " +
                            "ein Bundesland gewählt ist.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                ToggleRow(
                    title = "Gesetzliche Feiertage",
                    checked = state.publicHolidaysEnabled,
                    onCheckedChange = viewModel::setPublicHolidaysEnabled
                )
            }
            item {
                ToggleRow(
                    title = "Schulferien",
                    checked = state.schoolHolidaysEnabled,
                    onCheckedChange = viewModel::setSchoolHolidaysEnabled
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
