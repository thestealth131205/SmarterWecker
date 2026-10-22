package de.letheapp.nocturn.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.ui.NocturnBottomBar
import de.letheapp.nocturn.ui.NocturnTab

/**
 * Orte-Liste, erreichbar über das 3-Punkte-Menü des Wetter-Screens ("Ort"). Der erste Eintrag ist
 * immer der aktive Hauptort — Tippen darauf öffnet [onOpenMainLocationSettings] (die bisherige
 * Ortswahl: Automatisch/manuell per Suche, Live-Standort-Schalter). Darunter folgen die vom
 * Nutzer zusätzlich angelegten Orte, über das "+" (schwebend über der unteren Tab-Leiste) kommen
 * weitere hinzu — alle zusammen lassen sich im Wetter-Screen per Wischen durchblättern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    onBack: () -> Unit,
    onOpenMainLocationSettings: () -> Unit,
    onSelectTab: (NocturnTab) -> Unit,
    viewModel: LocationListViewModel = hiltViewModel()
) {
    val mainLocationName by viewModel.mainLocationName.collectAsState()
    val additionalLocations by viewModel.additionalLocations.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orte") },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Filled.Add, contentDescription = "Ort hinzufügen")
            }
        },
        bottomBar = {
            NocturnBottomBar(selected = NocturnTab.WETTER, onSelect = onSelectTab)
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Hauptort") },
                    supportingContent = { Text(mainLocationName) },
                    leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMainLocationSettings)
                )
            }
            items(additionalLocations) { location ->
                ListItem(
                    headlineContent = { Text(location.name) },
                    leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeLocation(location.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Entfernen")
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddLocationDialog(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLocationDialog(viewModel: LocationListViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searching by viewModel.searching.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = viewModel::dismissAddDialog) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Ort hinzufügen", style = MaterialTheme.typography.titleMedium)

                ExposedDropdownMenuBox(
                    expanded = menuExpanded && suggestions.isNotEmpty(),
                    onExpandedChange = { menuExpanded = it },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            viewModel.onSearchQueryChange(it)
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
                                    viewModel.addLocation(suggestion)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = viewModel::dismissAddDialog, modifier = Modifier.fillMaxWidth()) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    }
}
