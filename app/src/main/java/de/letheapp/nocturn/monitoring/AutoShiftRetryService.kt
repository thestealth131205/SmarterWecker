package de.letheapp.nocturn.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.ringing.ACTION_RING
import de.letheapp.nocturn.ringing.AlarmRingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

const val NOTIFICATION_ID_RETRY_CHECK = 1006
private const val RETRY_CHECK_WINDOW_MILLIS = 5_000L

/**
 * Wird zu einem von [AutomaticShiftRetryScheduler] zufällig gewählten späteren Zeitpunkt nach
 * einem ohne Bewegung gelösten Notfall-Puzzle eines automatischen Schicht-Weckers ausgelöst. Prüft kurz
 * per Sensorik, ob der Nutzer inzwischen doch aufgestanden ist — falls ja, nur eine Benachrichtigung
 * statt erneutem Klingeln; falls nein, klingelt [AlarmRingingService] für denselben Alarm erneut,
 * als wäre es das erste Mal (inkl. eigener Stop-Bewegungsprüfung und ggf. erneutem Notfall-Alarm).
 */
@AndroidEntryPoint
class AutoShiftRetryService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var sensorMovementMonitorFactory: SensorMovementMonitorFactory

    // Main-Dispatcher: SensorManager.registerListener() braucht einen Thread mit Looper.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId == -1L) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID_RETRY_CHECK, buildNotification())

        scope.launch {
            val alarm = alarmRepository.getById(alarmId)
            if (alarm == null || alarm.autoShiftWakeupEpochDay == null) {
                stopSelfCleanly()
                return@launch
            }

            val alreadyAwake = sensorMovementMonitorFactory.quickCheck(scope, RETRY_CHECK_WINDOW_MILLIS)
            if (alreadyAwake) {
                showSkippedNotification()
            } else {
                val ringIntent = Intent(this@AutoShiftRetryService, AlarmRingingService::class.java).apply {
                    action = ACTION_RING
                    putExtra(EXTRA_ALARM_ID, alarmId)
                }
                ContextCompat.startForegroundService(this@AutoShiftRetryService, ringIntent)
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
            .setContentText("Vor dem erneuten Klingeln wird kurz per Sensor geprüft.")
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
        scope.cancel()
    }
}
