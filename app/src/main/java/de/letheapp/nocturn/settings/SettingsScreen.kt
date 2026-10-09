package de.letheapp.nocturn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val weatherEnabled by viewModel.weatherEnabled.collectAsState()
    val manualLocation by viewModel.manualLocation.collectAsState()
    val locationQuery by viewModel.locationQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searching by viewModel.searching.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp)) {
                        Text("Wetter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Zeigt das aktuelle Wetter (OpenWeatherMap) oben in der Titelleiste " +
                                "sowie die Temperatur im Uhr-Widget. Benötigt die Standort-Berechtigung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = weatherEnabled, onCheckedChange = viewModel::setWeatherEnabled)
                }
            }

            if (weatherEnabled) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                Text("Ort für das Wetter", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    manualLocation?.name
                                        ?: "Automatisch (letzte bekannte Geräteposition)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (manualLocation != null) {
                                IconButton(onClick = viewModel::useAutomaticLocation) {
                                    Icon(Icons.Filled.Close, contentDescription = "Automatisch nutzen")
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = menuExpanded && suggestions.isNotEmpty(),
                            onExpandedChange = { menuExpanded = it },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            OutlinedTextField(
                                value = locationQuery,
                                onValueChange = {
                                    viewModel.onLocationQueryChange(it)
                                    menuExpanded = true
                                },
                                label = { Text("Ort suchen") },
                                placeholder = { Text("z. B. Köln") },
                                singleLine = true,
                                trailingIcon = {
                                    if (searching) {
                                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                    }
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = menuExpanded && suggestions.isNotEmpty(),
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                suggestions.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = { Text(suggestion.displayName) },
                                        onClick = {
                                            viewModel.selectLocation(suggestion)
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (manualLocation != null) {
                            TextButton(
                                onClick = viewModel::useAutomaticLocation,
                                modifier = Modifier.padding(top = 4.dp)
                            ) { Text("Automatische Standorterkennung nutzen") }
                        }
                    }
                }
            }
        }
    }
}
