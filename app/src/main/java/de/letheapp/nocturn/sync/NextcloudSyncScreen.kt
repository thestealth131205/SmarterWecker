package de.letheapp.nocturn.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudSyncScreen(onBack: () -> Unit, viewModel: NextcloudSyncViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nextcloud-Synchronisierung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val currentAccount = account
            if (currentAccount == null) {
                ConnectForm(
                    discoveryState = discoveryState,
                    onDiscover = viewModel::discover,
                    onConnect = viewModel::connect,
                    onResetDiscovery = viewModel::resetDiscovery
                )
            } else {
                ConnectedStatus(
                    account = currentAccount,
                    isSyncing = isSyncing,
                    onSyncNow = viewModel::syncNow,
                    onUpdateOptions = viewModel::updateSyncOptions,
                    onDisconnect = viewModel::disconnect
                )
            }
        }
    }
}

@Composable
private fun ConnectForm(
    discoveryState: DiscoveryState,
    onDiscover: (String, String, String) -> Unit,
    onConnect: (CalDavCalendarInfo, Boolean, Boolean) -> Unit,
    onResetDiscovery: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var syncAppointments by remember { mutableStateOf(true) }
    var syncShifts by remember { mutableStateOf(true) }
    var selectedCalendar by remember { mutableStateOf<CalDavCalendarInfo?>(null) }

    Text(
        "Verbinde NocTurn mit deinem Nextcloud-Kalender. Beim ersten Verbinden werden alle " +
            "bereits in Nextcloud vorhandenen Termine einmalig nach NocTurn übernommen. Danach " +
            "läuft der Abgleich automatisch alle 4 Stunden im Hintergrund weiter in beide " +
            "Richtungen: neue/geänderte Termine aus Nextcloud werden übernommen, neue/geänderte " +
            "Termine aus NocTurn hochgeladen — bereits abgeglichene Einträge werden dabei erkannt " +
            "und nicht doppelt angelegt. Deine Schichten werden zusätzlich einseitig " +
            "(NocTurn -> Nextcloud) als Termine exportiert."
    )
    Spacer(modifier = Modifier.padding(top = 12.dp))

    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server-URL (z. B. https://cloud.example.de)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Benutzername") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.Username }
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("App-Passwort") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentType = ContentType.Password }
    )
    Text(
        "Tipp: in Nextcloud unter Einstellungen -> Sicherheit ein eigenes App-Passwort erzeugen, " +
            "statt das Hauptpasswort zu verwenden.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )

    when (val state = discoveryState) {
        is DiscoveryState.Idle -> {
            Button(
                onClick = { onDiscover(serverUrl, username, password) },
                enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kalender suchen") }
        }

        is DiscoveryState.Loading -> {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
        }

        is DiscoveryState.Error -> {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onResetDiscovery) { Text("Erneut versuchen") }
        }

        is DiscoveryState.Success -> {
            Text("Kalender wählen:", modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                state.calendars.forEach { calendar ->
                    ListItem(
                        headlineContent = { Text(calendar.displayName) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedCalendar == calendar,
                                onClick = { selectedCalendar = calendar }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().clickable { selectedCalendar = calendar }
                    )
                }
            }
            LabeledSwitch(
                label = "Freie Termine synchronisieren",
                checked = syncAppointments,
                onCheckedChange = { syncAppointments = it }
            )
            LabeledSwitch(
                label = "Schichten exportieren",
                checked = syncShifts,
                onCheckedChange = { syncShifts = it }
            )
            Button(
                onClick = { selectedCalendar?.let { onConnect(it, syncAppointments, syncShifts) } },
                enabled = selectedCalendar != null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Verbinden") }
        }
    }
}

@Composable
private fun ConnectedStatus(
    account: CalDavAccountEntity,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onUpdateOptions: (Boolean, Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Verbunden mit ${account.serverUrl}")
            Text("Kalender: ${account.calendarDisplayName}")
            Text(
                if (account.lastSyncEpochMillis > 0) {
                    val formatted = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(account.lastSyncEpochMillis))
                    "Letzter Sync: $formatted"
                } else {
                    "Noch nicht synchronisiert"
                }
            )
            account.lastSyncError?.let { error ->
                Text("Letzter Fehler: $error", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Läuft automatisch alle 4 Stunden im Hintergrund weiter (beide Richtungen, " +
                    "bereits bekannte Einträge werden erkannt und nicht doppelt angelegt).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    LabeledSwitch(
        label = "Freie Termine synchronisieren",
        checked = account.syncAppointments,
        onCheckedChange = { onUpdateOptions(it, account.syncShifts) }
    )
    LabeledSwitch(
        label = "Schichten exportieren",
        checked = account.syncShifts,
        onCheckedChange = { onUpdateOptions(account.syncAppointments, it) }
    )

    Button(onClick = onSyncNow, enabled = !isSyncing, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(if (isSyncing) "Synchronisiere..." else "Jetzt synchronisieren")
    }

    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
        Text("Verbindung trennen")
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
