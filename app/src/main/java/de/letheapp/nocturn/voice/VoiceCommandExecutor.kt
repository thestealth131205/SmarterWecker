package de.letheapp.nocturn.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.alarm.AlarmScheduler
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.stopwatch.StopwatchRepository
import de.letheapp.nocturn.timer.ACTION_TIMER_START
import de.letheapp.nocturn.timer.EXTRA_TIMER_DURATION_MILLIS
import de.letheapp.nocturn.timer.EXTRA_TIMER_LABEL
import de.letheapp.nocturn.timer.TimerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Führt per [VoiceCommandParser] erkannte Sprachbefehle tatsächlich aus (Timer starten,
 * Stoppuhr steuern, einmaligen Wecker anlegen) und liefert eine kurze Text-Rückmeldung für die
 * Anzeige als Toast/Snackbar.
 */
@Singleton
class VoiceCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val stopwatchRepository: StopwatchRepository
) {
    suspend fun execute(spokenText: String): String =
        when (val command = VoiceCommandParser.parse(spokenText)) {
            is VoiceCommand.StartTimer -> {
                val intent = Intent(context, TimerService::class.java).apply {
                    action = ACTION_TIMER_START
                    putExtra(EXTRA_TIMER_DURATION_MILLIS, command.durationMillis)
                    putExtra(EXTRA_TIMER_LABEL, "Sprachbefehl")
                }
                ContextCompat.startForegroundService(context, intent)
                "Timer über ${describeDuration(command.durationMillis)} gestartet."
            }
            VoiceCommand.StartStopwatch -> {
                stopwatchRepository.start()
                "Stoppuhr gestartet."
            }
            VoiceCommand.PauseStopwatch -> {
                stopwatchRepository.pause()
                "Stoppuhr angehalten."
            }
            VoiceCommand.ResetStopwatch -> {
                stopwatchRepository.reset()
                "Stoppuhr zurückgesetzt."
            }
            is VoiceCommand.CreateAlarm -> {
                val alarm = AlarmEntity(
                    hour = command.hour,
                    minute = command.minute,
                    repeatDays = 0,
                    enabled = true,
                    label = "Sprachbefehl"
                )
                val id = alarmRepository.save(alarm)
                alarmScheduler.schedule(alarm.copy(id = id))
                "Wecker für %02d:%02d Uhr gestellt.".format(command.hour, command.minute)
            }
            is VoiceCommand.Unrecognized -> "Befehl nicht verstanden: \"${command.rawText}\""
        }

    private fun describeDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours Std. $minutes Min."
            hours > 0 -> "$hours Std."
            else -> "$minutes Min."
        }
    }
}
