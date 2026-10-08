package de.letheapp.nocturn.monitoring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.letheapp.nocturn.ui.theme.NocTurnTheme

/**
 * Pflicht-Gegenstück zum `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`-Intent-Filter:
 * Health Connect verlinkt im Berechtigungsdialog auf diese Aktivität ("Datenschutzerklärung"),
 * ohne eine registrierte Aktivität dafür bleibt die Berechtigungsvergabe bei manchen
 * Health-Connect-Versionen unzuverlässig (siehe offizielle Doku "Get started with Health
 * Connect" — Ursache für den gemeldeten Bug "Berechtigung wird trotz mehrfachem Versuch nicht
 * erteilt").
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NocTurnTheme {
                PermissionsRationaleScreen()
            }
        }
    }
}

@Composable
private fun PermissionsRationaleScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Warum NocTurn Health-Connect-Daten nutzt", style = MaterialTheme.typography.titleLarge)
            Text(
                "NocTurn fragt über Health Connect ausschließlich deine Schrittdaten ab. Diese " +
                    "werden nur genutzt, um nach dem Klingeln eines Alarms zu erkennen, ob du " +
                    "tatsächlich aufgestanden und losgelaufen bist — reines Wegdrücken des Alarms " +
                    "reicht sonst nicht aus. Die Daten verlassen dein Gerät nicht, werden nicht an " +
                    "Server übertragen und nicht dauerhaft gespeichert."
            )
        }
    }
}
