package de.letheapp.nocturn.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.ringing.ACTION_RING
import de.letheapp.nocturn.ringing.AlarmRingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = alarmRepository.getById(alarmId)
                if (alarm != null && (isSnooze || alarm.enabled)) {
                    val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                        action = ACTION_RING
                        putExtra(EXTRA_ALARM_ID, alarmId)
                        putExtra(EXTRA_IS_SNOOZE, isSnooze)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)

                    // Für wiederholende bzw. rotationsgebundene Alarme direkt den nächsten
                    // Termin vorplanen, damit ein Snooze-Durchlauf den regulären Zyklus nicht kappt.
                    if (!isSnooze && (alarm.repeatDays != 0 || alarm.rotationId != null || alarm.timetableJobId != null)) {
                        alarmScheduler.schedule(alarm)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
