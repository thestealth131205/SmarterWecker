package de.letheapp.nocturn.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.calendar.AppointmentReminderScheduler
import de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer
import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var appointmentDao: AppointmentDao
    @Inject lateinit var appointmentReminderScheduler: AppointmentReminderScheduler
    @Inject lateinit var shiftWakeupAlarmSyncer: ShiftWakeupAlarmSyncer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Zuerst neu berechnen, damit ein evtl. zwischenzeitlich abgelaufenes Datum des
                // automatischen "Nächste Schicht"-Alarms nicht als abgelaufener Alarm neu geplant wird.
                shiftWakeupAlarmSyncer.sync()
                alarmRepository.observeAll().first()
                    .filter { it.enabled }
                    .forEach { alarmScheduler.schedule(it) }
                appointmentDao.getAllWithReminder().forEach { appointmentReminderScheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
