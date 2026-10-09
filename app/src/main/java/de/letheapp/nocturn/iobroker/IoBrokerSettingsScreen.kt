package de.letheapp.nocturn.iobroker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoBrokerSettingsScreen(onBack: () -> Unit, viewModel: IoBrokerSettingsViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsState()
    val connectState by viewModel.connectState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart-Home (ioBroker)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            val currentAccount = account
            if (currentAccount == null) {
                ConnectForm(connectState = connectState, onConnect = viewModel::connect)
            } else {
                ConnectedStatus(account = currentAccount, onDisconnect = viewModel::disconnect)
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
    var port by remember { mutableStateOf("8087") }
    var useHttps by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        "Verbinde NocTurn mit dem ioBroker-Simple-API-Adapter (Standardport 8087) deines Smart-Homes. " +
            "Beim Auslösen des Notfall-Alarms kann dann pro Wecker eine Lampe oder Steckdose automatisch eingeschaltet werden."
    )
    Spacer(modifier = Modifier.padding(top = 12.dp))

    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("Host/IP (z. B. 192.168.1.10)") },
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
                onClick = { onConnect(host, port.toIntOrNull() ?: 8087, useHttps, username, password) },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Erneut versuchen") }
        }
        is IoBrokerConnectState.Idle -> {
            Button(
                onClick = { onConnect(host, port.toIntOrNull() ?: 8087, useHttps, username, password) },
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
        "Im Bearbeiten-Screen eines Alarms kann jetzt unter \"Lampe bei Notfall-Alarm\" ein Datenpunkt gewählt werden.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Verbindung trennen")
    }
}
