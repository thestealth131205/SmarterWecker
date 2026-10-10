package de.letheapp.nocturn.sound

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.sound.lethe.LetheMediaTrack

private val STREAMING_TABS = listOf("Spotify", "Deezer", "Lethe-Bibliothek")
private const val LETHE_TAB_INDEX = 3 // Tab 0 = Töne, 1 = Spotify, 2 = Deezer, 3 = Lethe-Bibliothek

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
    val bundledSounds = remember { viewModel.bundledSounds() }
    val systemSounds = remember { viewModel.systemSounds() }
    val letheState by viewModel.letheState.collectAsState()

    LaunchedEffect(selectedTab) {
        viewModel.stopPreview()
        if (selectedTab == LETHE_TAB_INDEX) viewModel.onLetheTabOpened()
    }

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
                    val tabIndex = index + 1
                    val isLethe = tabIndex == LETHE_TAB_INDEX
                    Tab(
                        selected = selectedTab == tabIndex,
                        onClick = { if (isLethe) selectedTab = tabIndex },
                        enabled = isLethe,
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

                    item { SectionLabel("Wecker-Töne") }
                    items(bundledSounds) { option ->
                        SoundRow(option, selectedUri) { uri ->
                            selectedUri = uri
                            onSoundSelected(uri)
                            viewModel.previewSound(uri)
                        }
                    }

                    if (customSounds.isNotEmpty()) {
                        item { SectionLabel("Eigene Dateien") }
                        items(customSounds) { option ->
                            SoundRow(option, selectedUri) { uri ->
                                selectedUri = uri
                                onSoundSelected(uri)
                                viewModel.previewSound(uri)
                            }
                        }
                    }

                    item { SectionLabel("Systemtöne") }
                    items(systemSounds) { option ->
                        SoundRow(option, selectedUri) { uri ->
                            selectedUri = uri
                            onSoundSelected(uri)
                            viewModel.previewSound(uri)
                        }
                    }
                }
            } else if (selectedTab == LETHE_TAB_INDEX) {
                LetheLibraryTab(
                    state = letheState,
                    selectedUri = selectedUri,
                    onStartPairing = { phone, password -> viewModel.startLethePairing(phone, password) },
                    onCancelPairing = { viewModel.cancelPairing() },
                    onRetry = { viewModel.onLetheTabOpened() },
                    onForgetPairing = { viewModel.forgetLethePairing() },
                    onTrackSelected = { uri ->
                        selectedUri = uri
                        onSoundSelected(uri)
                        viewModel.previewSound(uri)
                    }
                )
            }
        }
    }
}

@Composable
private fun LetheLibraryTab(
    state: LetheLibraryState,
    selectedUri: String?,
    onStartPairing: (String, String) -> Unit,
    onCancelPairing: () -> Unit,
    onRetry: () -> Unit,
    onForgetPairing: () -> Unit,
    onTrackSelected: (String) -> Unit
) {
    when (state) {
        is LetheLibraryState.NotPaired -> LethePairingForm(onStartPairing)

        is LetheLibraryState.Loading -> Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is LetheLibraryState.AwaitingConfirmation -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Bitte auf deinem Lethe-Messenger-Gerät bestätigen …",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Du erhältst dort eine Benachrichtigung, um NocTurn Lesezugriff auf deine Musikbibliothek zu geben.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onCancelPairing) { Text("Abbrechen") }
        }

        is LetheLibraryState.Error -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }

        is LetheLibraryState.Paired -> Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mit Lethe gekoppelt", color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onForgetPairing) {
                    Icon(Icons.Filled.LinkOff, contentDescription = "Kopplung entfernen")
                }
            }
            Divider()
            if (state.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Deine Lethe-Musikbibliothek ist noch leer.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn {
                    items(state.tracks, key = { it.id }) { track ->
                        LetheTrackRow(track, selectedUri, onTrackSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun LethePairingForm(onStartPairing: (String, String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Mit deinem Lethe-Konto koppeln, um Weckertöne aus deiner Musikbibliothek zu nutzen. " +
                "Dein Passwort wird nur zur Anfrage genutzt und nicht gespeichert — die endgültige " +
                "Freigabe bestätigst du anschließend auf deinem Lethe-Messenger-Gerät."
        )
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Telefonnummer (Lethe-Konto)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Lethe-Passwort") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onStartPairing(phoneNumber.trim(), password) },
            enabled = phoneNumber.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Koppeln")
        }
    }
}

@Composable
private fun LetheTrackRow(track: LetheMediaTrack, selectedUri: String?, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(track.streamUrl) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = track.streamUrl == selectedUri,
            onClick = { onSelected(track.streamUrl) }
        )
        Column {
            Text(track.title ?: "Unbenannter Titel")
            track.artist?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
