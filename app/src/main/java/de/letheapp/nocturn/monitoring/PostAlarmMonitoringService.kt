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
import de.letheapp.nocturn.alarm.WakeWindowResolver
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.emergency.EmergencyAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

const val CHANNEL_MONITORING = "post_alarm_monitoring"
const val NOTIFICATION_ID_MONITORING = 1002
const val POST_ALARM_TIMEOUT_MILLIS = 10 * 60_000L

/**
 * Läuft nach einem "Stop"-Tastendruck weiter: der Alarm ist zwar verstummt, aber erst
 * fertig, wenn innerhalb von [POST_ALARM_TIMEOUT_MILLIS] echte Bewegung (Aufstehen +
 * Schritte) erkannt wurde. Sonst wird der Notfall-Alarm ausgelöst.
 */
@AndroidEntryPoint
class PostAlarmMonitoringService : Service() {

    @Inject lateinit var movementMonitor: CombinedMovementMonitor
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var wakeWindowResolver: WakeWindowResolver

    private val scope = CoroutineScope(SupervisorJob())
    private var timeoutJob: Job? = null
    private var alarmId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        startForeground(NOTIFICATION_ID_MONITORING, buildNotification())

        movementMonitor.start { onMovementConfirmed() }
        timeoutJob = scope.launch {
            delay(computeTimeoutMillis())
            onTimeout()
        }
        return START_STICKY
    }

    /**
     * Ermittelt die Aufsteh-Deadline: ist der Alarm mit einer Schicht/Unterrichtsstunde
     * verknüpft, die ein "spätestens aufstehen"-Offset konfiguriert hat, gilt diese Deadline
     * statt des festen 10-Minuten-Fensters (siehe CLAUDE.md Kernfunktion 5).
     */
    private suspend fun computeTimeoutMillis(): Long {
        val alarm = alarmRepository.getById(alarmId) ?: return POST_ALARM_TIMEOUT_MILLIS
        val now = LocalDateTime.now()
        val deadline = wakeWindowResolver.currentDeadline(alarm, now) ?: return POST_ALARM_TIMEOUT_MILLIS
        val millis = Duration.between(now, deadline).toMillis()
        return if (millis > 0) millis else POST_ALARM_TIMEOUT_MILLIS
    }

    private fun onMovementConfirmed() {
        timeoutJob?.cancel()
        movementMonitor.stop()
        stopForegroundCompat()
        stopSelf()
    }

    private fun onTimeout() {
        movementMonitor.stop()
        val emergencyIntent = Intent(this, EmergencyAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        // Direkter Start als schneller Pfad (Bildschirm ggf. schon an), zusätzlich über ein
        // Fullscreen-Intent auf der Notification abgesichert, falls Android 10+ den direkten
        // Activity-Start aus dem Hintergrund blockiert.
        startActivity(emergencyIntent)
        postEmergencyFullScreenNotification(emergencyIntent)
        stopForegroundCompat()
        stopSelf()
    }

    private fun postEmergencyFullScreenNotification(emergencyIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this, alarmId.toInt(), emergencyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Aufsteh-Überwachung", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setContentTitle("Notfall-Alarm")
            .setContentText("Du bist nicht rechtzeitig aufgestanden.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_MONITORING, notification)
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Aufsteh-Überwachung", NotificationManager.IMPORTANCE_DEFAULT)
        )
        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setContentTitle("Noch nicht fertig")
            .setContentText("Steh auf und geh ein paar Schritte, sonst gibt's den Notfall-Alarm.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
