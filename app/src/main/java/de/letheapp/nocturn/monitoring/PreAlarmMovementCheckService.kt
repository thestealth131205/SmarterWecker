package de.letheapp.nocturn.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.AlarmScheduler
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

const val CHANNEL_PRE_ALARM_CHECK = "pre_alarm_check"
const val NOTIFICATION_ID_PRE_ALARM_CHECK = 1004
const val NOTIFICATION_ID_PRE_ALARM_SKIPPED = 1005

private const val PRE_CHECK_INTERVAL_MILLIS = 20_000L
private const val PRE_CHECK_SAMPLE_WINDOW_MILLIS = 3_000L

/**
 * Nur für automatische Schicht-Wecker (siehe [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]):
 * ab 2 Minuten vor dem geplanten Klingeln (siehe [de.letheapp.nocturn.alarm.AlarmScheduler]) wird
 * alle [PRE_CHECK_INTERVAL_MILLIS] für [PRE_CHECK_SAMPLE_WINDOW_MILLIS] per Gyroskop/
 * Beschleunigungssensor geprüft, ob der Nutzer bereits aufgestanden ist. Da das Prüffenster selbst
 * kurz ist, gilt hier eine kürzere Sustained-Schwelle als beim Stop-Tasten-Check (siehe
 * [DEFAULT_SUSTAINED_MOVEMENT_MILLIS]). Wird Bewegung bestätigt, wird der eigentliche Alarm
 * abgesagt und eine "Wecken geskippt"-Benachrichtigung gezeigt statt normal zu klingeln. Bleibt es
 * bis zum geplanten Klingeln ohne Bewegung, beendet sich der Dienst einfach selbst — der normale
 * Alarm läuft davon unberührt über [de.letheapp.nocturn.alarm.AlarmReceiver] an.
 */
@AndroidEntryPoint
class PreAlarmMovementCheckService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var sensorMovementMonitorFactory: SensorMovementMonitorFactory

    // Main-Dispatcher: SensorManager.registerListener() braucht einen Thread mit Looper.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val mainTriggerAtMillis = intent?.getLongExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, -1L) ?: -1L
        if (alarmId == -1L || mainTriggerAtMillis <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID_PRE_ALARM_CHECK, buildNotification())

        job?.cancel()
        job = scope.launch {
            val alarm = alarmRepository.getById(alarmId)
            if (alarm == null || alarm.autoShiftWakeupEpochDay == null) {
                stopSelfCleanly()
                return@launch
            }

            while (System.currentTimeMillis() < mainTriggerAtMillis) {
                val confirmed = sensorMovementMonitorFactory.quickCheck(
                    scope = scope,
                    timeoutMillis = PRE_CHECK_SAMPLE_WINDOW_MILLIS,
                    sustainedMovementMillis = PRE_CHECK_SAMPLE_WINDOW_MILLIS / 2
                )
                if (confirmed) {
                    alarmScheduler.cancel(alarmId)
                    showSkippedNotification()
                    stopSelfCleanly()
                    return@launch
                }
                val remainingMillis = mainTriggerAtMillis - System.currentTimeMillis()
                if (remainingMillis <= 0L) break
                delay(minOf(PRE_CHECK_INTERVAL_MILLIS, remainingMillis))
            }
            stopSelfCleanly()
        }
        return START_STICKY
    }

    private fun showSkippedNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID_PRE_ALARM_SKIPPED,
            NotificationCompat.Builder(this, CHANNEL_PRE_ALARM_CHECK)
                .setContentTitle("Wecken geskippt")
                .setContentText("Du bist schon wach – der Wecker klingelt diesmal nicht.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_PRE_ALARM_CHECK, "Aufsteh-Vorprüfung", NotificationManager.IMPORTANCE_DEFAULT)
        )
        return NotificationCompat.Builder(this, CHANNEL_PRE_ALARM_CHECK)
            .setContentTitle("Prüfe, ob du schon wach bist")
            .setContentText("Kurz vor dem Wecken wird per Sensor geprüft, ob der Alarm nötig ist.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        scope.cancel()
    }
}
