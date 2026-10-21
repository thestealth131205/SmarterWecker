package de.letheapp.nocturn.timer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.letheapp.nocturn.EXTRA_OPEN_TIMER
import de.letheapp.nocturn.MainActivity

private const val EXTRA_ALARM_LENGTH = "android.intent.extra.alarm.LENGTH"
private const val EXTRA_ALARM_MESSAGE = "android.intent.extra.alarm.MESSAGE"
private const val EXTRA_ALARM_SKIP_UI = "android.intent.extra.alarm.SKIP_UI"

/**
 * Durchreich-Einstiegspunkt für Androids systemweite `SET_TIMER`-Aktion (z. B. Google Assistant:
 * "stelle einen Timer über 10 Minuten") — damit ist der NocTurn-Timer wie der von der Google-Uhr-
 * App auch für andere Apps/Systemkomponenten nutzbar. Ohne `SKIP_UI` öffnet sich stattdessen der
 * normale Timer-Screen mit vorausgefüllter Dauer zur Bestätigung.
 */
class TimerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lengthSeconds = intent.getIntExtra(EXTRA_ALARM_LENGTH, 0)
        val label = intent.getStringExtra(EXTRA_ALARM_MESSAGE).orEmpty()
        val skipUi = intent.getBooleanExtra(EXTRA_ALARM_SKIP_UI, false)
        val durationMillis = lengthSeconds * 1000L

        if (skipUi && durationMillis > 0) {
            val serviceIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_TIMER_START
                putExtra(EXTRA_TIMER_DURATION_MILLIS, durationMillis)
                putExtra(EXTRA_TIMER_LABEL, label)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_OPEN_TIMER, true)
                if (durationMillis > 0) putExtra(EXTRA_TIMER_DURATION_MILLIS, durationMillis)
                if (label.isNotBlank()) putExtra(EXTRA_TIMER_LABEL, label)
            }
            startActivity(mainIntent)
        }
        finish()
    }
}
