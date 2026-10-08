package de.letheapp.nocturn.emergency

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import de.letheapp.nocturn.emergency.puzzle.PuzzleGenerator
import de.letheapp.nocturn.emergency.puzzle.PuzzleScreen
import de.letheapp.nocturn.ui.theme.NocTurnTheme

/**
 * Notfall-Alarm-UI: lässt sich weder über die Zurück-Taste noch über "Zuhause" sinnvoll
 * umgehen (Alarm läuft als Foreground Service unabhängig weiter) – einzige Möglichkeit zum
 * Stoppen ist das korrekt gelöste Zuordnungs-Puzzle.
 */
class EmergencyAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        ContextCompat.startForegroundService(this, Intent(this, EmergencyAlarmService::class.java))

        setContent {
            NocTurnTheme {
                // Zurück-Taste absichtlich wirkungslos: einziger Ausweg ist das Puzzle.
                BackHandler(enabled = true) { }

                Scaffold { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        PuzzleScreen(
                            puzzle = remember { PuzzleGenerator.generate() },
                            onSolved = {
                                startService(
                                    Intent(this@EmergencyAlarmActivity, EmergencyAlarmService::class.java)
                                        .setAction(ACTION_EMERGENCY_SOLVED)
                                )
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
