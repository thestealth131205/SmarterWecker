package de.letheapp.nocturn.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AppointmentEntity
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_APPOINTMENT_ID = "extra_appointment_id"

/**
 * Plant/stornoiert die Erinnerungs-Benachrichtigung für einen Termin ([AppointmentEntity.reminderMinutesBefore]
 * Minuten vor [AppointmentEntity.startEpochMillis]), analog zu [de.letheapp.nocturn.alarm.AlarmScheduler].
 */
@Singleton
class AppointmentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(appointment: AppointmentEntity) {
        cancel(appointment.id)
        val minutesBefore = appointment.reminderMinutesBefore ?: return
        val triggerAtMillis = appointment.startEpochMillis - minutesBefore * 60_000L
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val pendingIntent = pendingIntentFor(appointment.id)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(appointmentId: Long) {
        alarmManager.cancel(pendingIntentFor(appointmentId))
    }

    private fun pendingIntentFor(appointmentId: Long): PendingIntent {
        val intent = Intent(context, AppointmentReminderReceiver::class.java).apply {
            putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
        }
        return PendingIntent.getBroadcast(
            context,
            appointmentId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
