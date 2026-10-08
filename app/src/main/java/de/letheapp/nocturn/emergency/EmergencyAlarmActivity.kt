package de.letheapp.nocturn.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.emergency.puzzle.PuzzleGenerator
import de.letheapp.nocturn.emergency.puzzle.PuzzleScreen
import de.letheapp.nocturn.monitoring.PostAlarmMonitoringService
import de.letheapp.nocturn.ui.theme.NocTurnTheme

/**
 * Notfall-Alarm-UI: lässt sich weder über die Zurück-Taste noch über "Zuhause" sinnvoll
 * umgehen (Alarm läuft als Foreground Service unabhängig weiter) – einzige Möglichkeit zum
 * Stoppen ist das korrekt gelöste Zuordnungs-Puzzle.
 */
class EmergencyAlarmActivity : ComponentActivity() {

    private val autoResolvedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val serviceIntent = Intent(this, EmergencyAlarmService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, intent.getLongExtra(EXTRA_ALARM_ID, -1L))
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        ContextCompat.registerReceiver(
            this,
            autoResolvedReceiver,
            IntentFilter(ACTION_EMERGENCY_AUTO_RESOLVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            NocTurnTheme {
                // Zurück-Taste absichtlich wirkungslos: einziger Ausweg ist das Puzzle
                // (oder tatsächlich erkanntes Aufstehen+Laufen, siehe autoResolvedReceiver).
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
                                stopService(Intent(this@EmergencyAlarmActivity, PostAlarmMonitoringService::class.java))
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(autoResolvedReceiver)
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
