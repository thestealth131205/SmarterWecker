package de.letheapp.nocturn.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.monitoring.AutoShiftRetryReceiver
import de.letheapp.nocturn.monitoring.EXTRA_MAIN_TRIGGER_AT_MILLIS
import de.letheapp.nocturn.monitoring.PreAlarmCheckReceiver
import de.letheapp.nocturn.monitoring.PreAlarmLocationCheckReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

/**
 * Wie weit vor dem Klingeln eines automatischen Schicht-Weckers (siehe
 * [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]) die periodische Vorprüfung
 * ([de.letheapp.nocturn.monitoring.PreAlarmMovementCheckService]) zu laufen beginnt.
 */
private const val PRE_CHECK_LEAD_MILLIS = 2 * 60_000L

/**
 * Wie weit vor dem Klingeln zusätzlich die Standort-Vorprüfung
 * ([de.letheapp.nocturn.monitoring.PreAlarmLocationCheckService]) zu laufen beginnt. Deutlich
 * länger als [PRE_CHECK_LEAD_MILLIS], da hier mehrere, über das Fenster verteilte Positions-
 * messungen nötig sind, um echte Bewegung von normalem Standort-Messrauschen zu unterscheiden.
 */
private const val PRE_LOCATION_CHECK_LEAD_MILLIS = 10 * 60_000L

/** Getrennte Request-Codes, damit Vorprüfung/Wieder-Klingeln nicht mit dem Haupt-Alarm kollidieren. */
private const val PRE_CHECK_REQUEST_CODE_OFFSET = 2_000_000
private const val RETRY_REQUEST_CODE_OFFSET = 3_000_000
private const val PRE_LOCATION_CHECK_REQUEST_CODE_OFFSET = 4_000_000

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
        // Ein frisch geplanter Ring-Zyklus macht ein eventuell noch ausstehendes Wieder-Klingeln
        // (siehe AutomaticShiftRetryScheduler) aus einem vorherigen Zyklus desselben Alarms
        // ungültig – sonst könnte es für eine inzwischen andere Schicht-Belegung auslösen.
        cancelAutomaticRetry(alarm.id)

        val triggerAtMillis = nextTriggerMillis(alarm)
        if (triggerAtMillis == null) {
            cancelPreCheck(alarm.id)
            cancelLocationPreCheck(alarm.id)
            return
        }
        scheduleExact(alarm.id, triggerAtMillis, isSnooze = false)

        if (alarm.autoShiftWakeupEpochDay != null) {
            schedulePreCheck(alarm.id, triggerAtMillis)
            scheduleLocationPreCheck(alarm.id, triggerAtMillis)
        } else {
            cancelPreCheck(alarm.id)
            cancelLocationPreCheck(alarm.id)
        }
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
        cancelPreCheck(alarmId)
        cancelLocationPreCheck(alarmId)
        cancelAutomaticRetry(alarmId)
    }

    /**
     * Startet [de.letheapp.nocturn.monitoring.PreAlarmMovementCheckService] [PRE_CHECK_LEAD_MILLIS]
     * vor dem eigentlichen Klingeln eines automatischen Schicht-Weckers. Liegt der Vorprüf-
     * Zeitpunkt bereits in der Vergangenheit (sehr naher Alarm), entfällt die Vorprüfung.
     */
    private fun schedulePreCheck(alarmId: Long, mainTriggerAtMillis: Long) {
        val preCheckTriggerAtMillis = mainTriggerAtMillis - PRE_CHECK_LEAD_MILLIS
        if (preCheckTriggerAtMillis <= System.currentTimeMillis()) {
            cancelPreCheck(alarmId)
            return
        }
        val pendingIntent = preCheckPendingIntentFor(alarmId, mainTriggerAtMillis)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preCheckTriggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preCheckTriggerAtMillis, pendingIntent)
        }
    }

    private fun cancelPreCheck(alarmId: Long) {
        alarmManager.cancel(preCheckPendingIntentFor(alarmId, 0L))
    }

    /**
     * Startet [de.letheapp.nocturn.monitoring.PreAlarmLocationCheckService]
     * [PRE_LOCATION_CHECK_LEAD_MILLIS] vor dem eigentlichen Klingeln eines automatischen
     * Schicht-Weckers — zusätzlich zur kürzeren, rein sensorbasierten Vorprüfung
     * ([schedulePreCheck]). Liegt der Vorprüf-Zeitpunkt bereits in der Vergangenheit (sehr naher
     * Alarm), entfällt die Standort-Vorprüfung.
     */
    private fun scheduleLocationPreCheck(alarmId: Long, mainTriggerAtMillis: Long) {
        val preCheckTriggerAtMillis = mainTriggerAtMillis - PRE_LOCATION_CHECK_LEAD_MILLIS
        if (preCheckTriggerAtMillis <= System.currentTimeMillis()) {
            cancelLocationPreCheck(alarmId)
            return
        }
        val pendingIntent = locationPreCheckPendingIntentFor(alarmId, mainTriggerAtMillis)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preCheckTriggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preCheckTriggerAtMillis, pendingIntent)
        }
    }

    private fun cancelLocationPreCheck(alarmId: Long) {
        alarmManager.cancel(locationPreCheckPendingIntentFor(alarmId, 0L))
    }

    /**
     * Plant das Wieder-Klingeln eines automatischen Schicht-Weckers nach einem ohne Bewegung
     * gelösten Notfall-Puzzle, siehe [de.letheapp.nocturn.monitoring.AutomaticShiftRetryScheduler].
     */
    fun scheduleAutomaticRetry(alarmId: Long, triggerAtMillis: Long) {
        val pendingIntent = retryPendingIntentFor(alarmId)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAutomaticRetry(alarmId: Long) {
        alarmManager.cancel(retryPendingIntentFor(alarmId))
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

    private fun preCheckPendingIntentFor(alarmId: Long, mainTriggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, PreAlarmCheckReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, mainTriggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            PRE_CHECK_REQUEST_CODE_OFFSET + alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun locationPreCheckPendingIntentFor(alarmId: Long, mainTriggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, PreAlarmLocationCheckReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, mainTriggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            PRE_LOCATION_CHECK_REQUEST_CODE_OFFSET + alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun retryPendingIntentFor(alarmId: Long): PendingIntent {
        val intent = Intent(context, AutoShiftRetryReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE_OFFSET + alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
