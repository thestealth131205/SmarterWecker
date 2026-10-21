package de.letheapp.nocturn.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Vom per [android.app.AlarmManager] geplanten exakten Alarm ausgelöst, wenn der Timer abgelaufen ist. */
class TimerCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, TimerService::class.java).setAction(ACTION_TIMER_RING)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
