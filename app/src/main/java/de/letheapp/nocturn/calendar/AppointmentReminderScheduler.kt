package de.letheapp.nocturn.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentReminderEntity
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_REMINDER_ID = "extra_reminder_id"

/**
 * Plant/stornoiert die Erinnerungen eines Termins ([AppointmentReminderEntity.minutesBefore]
 * Minuten vor [AppointmentEntity.startEpochMillis]) — ein Termin kann mehrere Erinnerungen haben,
 * jede bekommt ihren eigenen [AlarmManager]-Alarm, analog zu [de.letheapp.nocturn.alarm.AlarmScheduler].
 */
@Singleton
class AppointmentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(appointment: AppointmentEntity, reminder: AppointmentReminderEntity) {
        cancel(reminder.id)
        val triggerAtMillis = appointment.startEpochMillis - reminder.minutesBefore * 60_000L
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val pendingIntent = pendingIntentFor(reminder.id)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(pendingIntentFor(reminderId))
    }

    private fun pendingIntentFor(reminderId: Long): PendingIntent {
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
