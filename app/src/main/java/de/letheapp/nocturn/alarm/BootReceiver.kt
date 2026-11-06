package de.letheapp.nocturn.alarm

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.calendar.AppointmentReminderScheduler
import de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer
import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.widget.ClockWidgetProvider
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

        // Android loescht beim Neustart ALLE AlarmManager-Alarme, auch die selbst-nachplanende
        // Minuten-/20-Minuten-Tick-Kette des Uhr-Widgets - ohne diesen Aufruf bliebe die dort
        // zuletzt angezeigte Uhrzeit nach jedem Reboot fuer immer auf dem alten Stand eingefroren.
        // Nur neu anstossen, wenn ueberhaupt eine Instanz auf einem Homescreen liegt - sonst
        // wuerden hier unnoetig fuer immer leere Weck-Alarme weiterlaufen.
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val clockWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
        if (clockWidgetIds.isNotEmpty()) {
            ClockWidgetProvider.scheduleTicks(context)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Zuerst neu berechnen, damit ein evtl. zwischenzeitlich abgelaufenes Datum des
                // automatischen "Nächste Schicht"-Alarms nicht als abgelaufener Alarm neu geplant wird.
                shiftWakeupAlarmSyncer.sync()
                alarmRepository.observeAll().first()
                    .filter { it.enabled }
                    .forEach { alarmScheduler.schedule(it) }
                appointmentDao.getAllReminders().forEach { reminder ->
                    appointmentDao.getById(reminder.appointmentId)?.let { appointment ->
                        appointmentReminderScheduler.schedule(appointment, reminder)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
