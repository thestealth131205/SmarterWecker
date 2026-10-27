package de.letheapp.nocturn.ringing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.letheapp.nocturn.ui.theme.NocTurnTheme

class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        setContent {
            NocTurnTheme {
                RingingScreen(
                    hour = hour,
                    minute = minute,
                    label = label,
                    onSnooze = {
                        sendServiceAction(ACTION_SNOOZE)
                        finish()
                    },
                    onStop = {
                        sendServiceAction(ACTION_STOP)
                        finish()
                    }
                )
            }
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, AlarmRingingService::class.java).setAction(action)
        startService(intent)
    }

    /**
     * `singleInstance` liefert einen neu klingelnden Alarm sonst per [onNewIntent] an eine noch
     * hängende alte Instanz zurück (z. B. nach einem Schlummer-Zyklus oder falls die vorherige
     * Instanz nie über Snooze/Stop beendet wurde) — ohne diesen Override bliebe die alte Uhrzeit/
     * Label-Anzeige stehen, während im Hintergrund schon der neue Klingel-Zyklus läuft. Ein
     * `recreate()` nach [setIntent] erzwingt einen frischen [onCreate]-Durchlauf mit den neuen Extras.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}

@Composable
private fun RingingScreen(hour: Int, minute: Int, label: String, onSnooze: () -> Unit, onStop: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displayLarge)
            if (label.isNotBlank()) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(onClick = onSnooze) { Text("Schlummern") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStop) { Text("Stop") }
        }
    }
}
