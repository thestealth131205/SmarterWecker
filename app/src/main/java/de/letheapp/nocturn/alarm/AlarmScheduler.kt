package de.letheapp.nocturn.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AlarmEntity
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val EXTRA_ALARM_ID = "extra_alarm_id"
const val EXTRA_IS_SNOOZE = "extra_is_snooze"

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val trigger = AlarmRepeat.nextTrigger(alarm.hour, alarm.minute, alarm.repeatDays)
        val triggerAtMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleExact(alarm.id, triggerAtMillis, isSnooze = false)
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
