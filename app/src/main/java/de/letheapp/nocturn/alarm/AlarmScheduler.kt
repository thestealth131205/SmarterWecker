package de.letheapp.nocturn.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AlarmEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wakeWindowResolver: WakeWindowResolver
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * Plant den Alarm. Ist er mit einer Rotation oder einem Stundenplan verknüpft
     * ([AlarmEntity.rotationId]/[AlarmEntity.timetableJobId]), wird die Uhrzeit aus dem nächsten
     * anstehenden Schicht-/Unterrichtstermin abgeleitet statt aus [AlarmEntity.hour]/[minute] —
     * freie Tage werden dabei automatisch übersprungen, und ein konfigurierter
     * "frühestes Wecken"-Vorlauf wird vom Termin-Start abgezogen.
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
        val now = LocalDateTime.now(ZoneId.systemDefault())

        val autoEpochDay = alarm.autoShiftWakeupEpochDay
        if (autoEpochDay != null) {
            // Automatisch von ShiftWakeupAlarmSyncer verwaltet: hour/minute sind bereits die
            // Uhrzeit der konkreten Schicht an diesem Datum, keine Wiederholungslogik nötig.
            val trigger = LocalDate.ofEpochDay(autoEpochDay).atTime(alarm.hour, alarm.minute)
            return if (trigger.isAfter(now)) trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
        }

        if (alarm.rotationId == null && alarm.timetableJobId == null) {
            val trigger = AlarmRepeat.nextTrigger(alarm.hour, alarm.minute, alarm.repeatDays, now)
            return trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        val occurrence = wakeWindowResolver.nextOccurrence(alarm, now) ?: return null
        return occurrence.wakeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
