package de.letheapp.nocturn.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.emergency.ACTION_EMERGENCY_AUTO_RESOLVED
import de.letheapp.nocturn.emergency.ACTION_EMERGENCY_SOLVED
import de.letheapp.nocturn.emergency.EmergencyAlarmActivity
import de.letheapp.nocturn.emergency.EmergencyAlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

const val CHANNEL_MONITORING = "post_alarm_monitoring"
const val NOTIFICATION_ID_MONITORING = 1002
const val POST_ALARM_TIMEOUT_MILLIS = 10 * 60_000L

/**
 * Läuft nach einem "Stop"-Tastendruck parallel zum sofort gestarteten Notfall-Alarm
 * (Ton + Puzzle, siehe [EmergencyAlarmActivity]): überwacht per Sensorik/Health Connect, ob
 * der Nutzer tatsächlich aufsteht und läuft. Wird echte Bewegung erkannt, wird der
 * Notfall-Alarm automatisch beendet, auch ohne gelöstes Puzzle. Nach [POST_ALARM_TIMEOUT_MILLIS]
 * ohne Bewegung oder gelöstes Puzzle stoppt die Sensor-Überwachung als Akku-Sicherheitsnetz
 * von selbst (der Notfall-Alarm läuft davon unberührt weiter).
 */
@AndroidEntryPoint
class PostAlarmMonitoringService : Service() {

    @Inject lateinit var movementMonitor: CombinedMovementMonitor

    private val scope = CoroutineScope(SupervisorJob())
    private var safetyStopJob: Job? = null
    private var alarmId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        startForeground(NOTIFICATION_ID_MONITORING, buildNotification())

        movementMonitor.start { onMovementConfirmed() }
        safetyStopJob = scope.launch {
            delay(POST_ALARM_TIMEOUT_MILLIS)
            stopSelfCleanly()
        }
        return START_STICKY
    }

    private fun onMovementConfirmed() {
        startService(
            Intent(this, EmergencyAlarmService::class.java).setAction(ACTION_EMERGENCY_SOLVED)
        )
        sendBroadcast(Intent(ACTION_EMERGENCY_AUTO_RESOLVED).setPackage(packageName))
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        safetyStopJob?.cancel()
        movementMonitor.stop()
        stopForegroundCompat()
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Aufsteh-Überwachung", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val contentIntent = PendingIntent.getActivity(
            this, alarmId.toInt(),
            Intent(this, EmergencyAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setContentTitle("Noch nicht fertig")
            .setContentText("Löse die Aufgabe oder steh auf und geh ein paar Schritte.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        movementMonitor.stop()
        scope.cancel()
    }
}
