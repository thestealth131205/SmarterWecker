package de.letheapp.nocturn.sound

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val STREAMING_TABS = listOf("Spotify", "Deezer", "Lethe-Bibliothek")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerScreen(
    currentSoundUri: String?,
    onSoundSelected: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SoundPickerViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedUri by remember { mutableStateOf(currentSoundUri) }
    var customSounds by remember { mutableStateOf(viewModel.customSounds()) }
    val systemSounds = remember { viewModel.systemSounds() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.onCustomFilePicked(uri)
            customSounds = viewModel.customSounds()
            selectedUri = uri.toString()
            onSoundSelected(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weckton wählen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Töne") })
                STREAMING_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index + 1,
                        onClick = { /* in Phase 1 deaktiviert */ },
                        enabled = false,
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filePicker.launch(arrayOf("audio/*")) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Text("Eigene Audiodatei wählen…", modifier = Modifier.padding(start = 16.dp))
                        }
                        Divider()
                    }

                    if (customSounds.isNotEmpty()) {
                        item { SectionLabel("Eigene Dateien") }
                        items(customSounds) { option ->
                            SoundRow(option, selectedUri) { uri ->
                                selectedUri = uri
                                onSoundSelected(uri)
                            }
                        }
                    }

                    item { SectionLabel("Systemtöne") }
                    items(systemSounds) { option ->
                        SoundRow(option, selectedUri) { uri ->
                            selectedUri = uri
                            onSoundSelected(uri)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SoundRow(option: SoundOption, selectedUri: String?, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(option.uri.toString()) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = option.uri.toString() == selectedUri,
            onClick = { onSelected(option.uri.toString()) }
        )
        Text(option.title)
    }
}
