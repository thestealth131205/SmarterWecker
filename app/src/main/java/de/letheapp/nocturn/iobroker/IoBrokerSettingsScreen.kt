package de.letheapp.nocturn.iobroker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.letheapp.nocturn.data.db.IoBrokerAccountEntity
import de.letheapp.nocturn.onboarding.HomeAssistantConfig
import de.letheapp.nocturn.onboarding.SmartHomeBackend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoBrokerSettingsScreen(onBack: () -> Unit, viewModel: IoBrokerSettingsViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val backend by viewModel.backend.collectAsState()
    val homeAssistantConfig by viewModel.homeAssistantConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (backend == SmartHomeBackend.HOME_ASSISTANT) "Smart-Home (Home Assistant)" else "Smart-Home (ioBroker)")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("System (gilt für die gesamte App)", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                FilterChip(
                    selected = backend == SmartHomeBackend.IOBROKER,
                    onClick = { viewModel.setBackend(SmartHomeBackend.IOBROKER) },
                    label = { Text("ioBroker") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = backend == SmartHomeBackend.HOME_ASSISTANT,
                    onClick = { viewModel.setBackend(SmartHomeBackend.HOME_ASSISTANT) },
                    label = { Text("Home Assistant") }
                )
            }
            when (backend) {
                SmartHomeBackend.IOBROKER -> {
                    val currentAccount = account
                    if (currentAccount == null) {
                        ConnectForm(connectState = connectState, onConnect = viewModel::connect)
                    } else {
                        ConnectedStatus(account = currentAccount, onDisconnect = viewModel::disconnect)
                    }
                }
                SmartHomeBackend.HOME_ASSISTANT -> {
                    val config = homeAssistantConfig
                    if (config == null) {
                        HomeAssistantConnectForm(connectState = connectState, onConnect = viewModel::connectHomeAssistant)
                    } else {
                        HomeAssistantConnectedStatus(config = config, onDisconnect = viewModel::disconnectHomeAssistant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectForm(
    connectState: IoBrokerConnectState,
    onConnect: (host: String, port: Int, useHttps: Boolean, username: String, password: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("345") }
    var useHttps by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        "Verbinde NocTurn mit deinem \"iosync\"-ioBroker-Adapter — demselben, den auch die Iosync-App " +
            "nutzt (dort unter Einstellungen → ioBroker Adapter Verbindung einsehbar: vollständiger " +
            "Host inkl. Subdomain, Port, HTTP/HTTPS, Zugangsdaten). Beim Auslösen des Notfall-Alarms kann " +
            "dann pro Wecker eine Lampe oder Steckdose automatisch eingeschaltet werden."
    )
    Spacer(modifier = Modifier.padding(top = 12.dp))

    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("Host/IP (z. B. iosync.thestealth.de)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter { c -> c.isDigit() } },
        label = { Text("Port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("HTTPS verwenden")
        Switch(checked = useHttps, onCheckedChange = { useHttps = it })
    }
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Benutzername (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Passwort (optional)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )

    when (connectState) {
        is IoBrokerConnectState.Loading -> {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
        }
        is IoBrokerConnectState.Error -> {
            Text(
                "Verbindung fehlgeschlagen. Host/Port/Zugangsdaten prüfen.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = { onConnect(host, port.toIntOrNull() ?: 345, useHttps, username, password) },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Erneut versuchen") }
        }
        is IoBrokerConnectState.Idle -> {
            Button(
                onClick = { onConnect(host, port.toIntOrNull() ?: 345, useHttps, username, password) },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Verbinden") }
        }
    }
}

@Composable
private fun ConnectedStatus(account: IoBrokerAccountEntity, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val scheme = if (account.useHttps) "https" else "http"
            Text("Verbunden mit $scheme://${account.host}:${account.port}")
            if (account.username.isNotBlank()) {
                Text("Benutzer: ${account.username}")
            }
        }
    }
    Text(
        "Im Bearbeiten-Screen eines Alarms bzw. einer Schicht lassen sich jetzt unter \"Smart-Home bei Notfall-Alarm\" Datenpunkte wählen.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Verbindung trennen")
    }
}

@Composable
private fun HomeAssistantConnectForm(
    connectState: IoBrokerConnectState,
    onConnect: (host: String, port: Int, useHttps: Boolean, token: String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8123") }
    var useHttps by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    Text(
        "Verbinde NocTurn mit deinem Home Assistant — genau wie in der Iosync-App (dort unter " +
            "Einstellungen → Home Assistant). Den Langzeit-Zugriffstoken erstellst du in Home Assistant " +
            "unter Profil → Sicherheit → Langlebige Zugriffstoken. Beim Notfall-Alarm schaltet NocTurn " +
            "dann die gewählten Entities (Lampe, Rollo, Skript) per Service-Call."
    )
    Spacer(modifier = Modifier.padding(top = 12.dp))

    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("Host/IP (z. B. homeassistant.local)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter { c -> c.isDigit() } },
        label = { Text("Port") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("HTTPS verwenden")
        Switch(checked = useHttps, onCheckedChange = { useHttps = it })
    }
    OutlinedTextField(
        value = token,
        onValueChange = { token = it },
        label = { Text("Langzeit-Zugriffstoken") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )

    if (connectState is IoBrokerConnectState.Loading) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
    } else {
        if (connectState is IoBrokerConnectState.Error) {
            Text(
                "Verbindung fehlgeschlagen. Host/Port/Token prüfen.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Button(
            onClick = { onConnect(host, port.toIntOrNull() ?: 8123, useHttps, token) },
            enabled = host.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text(if (connectState is IoBrokerConnectState.Error) "Erneut versuchen" else "Verbinden") }
    }
}

@Composable
private fun HomeAssistantConnectedStatus(config: HomeAssistantConfig, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val scheme = if (config.useHttps) "https" else "http"
            Text("Verbunden mit Home Assistant: $scheme://${config.host}:${config.port}")
        }
    }
    Text(
        "Im Bearbeiten-Screen eines Alarms bzw. einer Schicht lassen sich jetzt unter \"Smart-Home bei " +
            "Notfall-Alarm\" Entities wählen (inkl. Helligkeit als stufenweise erhöhbarer Wert).",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Verbindung trennen")
    }
}
