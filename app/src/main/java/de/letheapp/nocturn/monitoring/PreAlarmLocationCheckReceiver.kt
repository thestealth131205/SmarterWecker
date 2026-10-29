package de.letheapp.nocturn.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID

/** Startet [PreAlarmLocationCheckService], siehe dort. */
class PreAlarmLocationCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val mainTriggerAtMillis = intent.getLongExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, -1L)
        if (alarmId == -1L || mainTriggerAtMillis <= 0L) return

        val serviceIntent = Intent(context, PreAlarmLocationCheckService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, mainTriggerAtMillis)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
