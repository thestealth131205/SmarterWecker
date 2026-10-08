package de.letheapp.nocturn.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

/** Obergrenze für die Tage-Suche nach dem nächsten Schicht-Termin einer Rotation (Sicherheitsnetz). */
private const val MAX_ROTATION_LOOKAHEAD_DAYS = 400

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarRepository: CalendarRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * Plant den Alarm. Ist er mit einer Rotation verknüpft ([AlarmEntity.rotationId]), wird die
     * Uhrzeit aus der nächsten anstehenden Schicht abgeleitet statt aus [AlarmEntity.hour]/[minute]
     * — freie Tage laut Rotation werden dabei automatisch übersprungen.
     */
    suspend fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val triggerAtMillis = nextTriggerMillis(alarm) ?: return
        scheduleExact(alarm.id, triggerAtMillis, isSnooze = false)
    }

    private suspend fun nextTriggerMillis(alarm: AlarmEntity): Long? {
        val rotationId = alarm.rotationId
        val now = LocalDateTime.now(ZoneId.systemDefault())

        if (rotationId == null) {
            val trigger = AlarmRepeat.nextTrigger(alarm.hour, alarm.minute, alarm.repeatDays, now)
            return trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        var fromEpochDay = now.toLocalDate().toEpochDay()
        repeat(MAX_ROTATION_LOOKAHEAD_DAYS) {
            val entry = calendarRepository.getNextCalendarEntryWithShift(rotationId, fromEpochDay)
                ?: return null
            val shift = entry.shiftId?.let { calendarRepository.getShift(it) }
            if (shift == null) {
                fromEpochDay = entry.dateEpochDay + 1
                return@repeat
            }
            val candidate = LocalDate.ofEpochDay(entry.dateEpochDay).atTime(shift.startHour, shift.startMinute)
            if (candidate.isAfter(now)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            fromEpochDay = entry.dateEpochDay + 1
        }
        return null
    }

    fun scheduleSnooze(alarmId: Long, triggerAtMillis: Long) {
        scheduleExact(alarmId, triggerAtMillis, isSnooze = true)
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntentFor(alarmId))
    }

    private fun scheduleExact(alarmId: Long, triggerAtMillis: Long, isSnooze: Boolean) {
        val pendingIntent = pendingIntentFor(alarmId, isSnooze)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun pendingIntentFor(alarmId: Long, isSnooze: Boolean = false): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
