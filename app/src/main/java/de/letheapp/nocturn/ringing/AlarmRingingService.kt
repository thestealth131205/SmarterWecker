package de.letheapp.nocturn.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.AlarmScheduler
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.emergency.EmergencyAlarmActivity
import de.letheapp.nocturn.monitoring.PostAlarmMonitoringService
import de.letheapp.nocturn.sound.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

const val ACTION_RING = "de.letheapp.nocturn.action.RING"
const val ACTION_SNOOZE = "de.letheapp.nocturn.action.SNOOZE"
const val ACTION_STOP = "de.letheapp.nocturn.action.STOP"

const val EXTRA_LABEL = "extra_label"
const val EXTRA_HOUR = "extra_hour"
const val EXTRA_MINUTE = "extra_minute"

const val CHANNEL_ALARM = "alarm_ringing"
const val NOTIFICATION_ID_RINGING = 1001

private const val SNOOZE_MIN_MINUTES = 3L
private const val SNOOZE_MAX_MINUTES = 5L
private const val ESCALATION_STEP_MILLIS = 60_000L

@AndroidEntryPoint
class AlarmRingingService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var soundRepository: SoundRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob())
    private var escalationJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> { handleSnooze(); return START_NOT_STICKY }
            ACTION_STOP -> { handleStop(); return START_NOT_STICKY }
            else -> {
                val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
                if (alarmId == -1L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentAlarmId = alarmId
                startRinging(alarmId)
            }
        }
        return START_STICKY
    }

    private fun startRinging(alarmId: Long) {
        acquireWakeLock()
        startForeground(NOTIFICATION_ID_RINGING, buildNotification(alarmId))

        scope.launch {
            val alarm = alarmRepository.getById(alarmId)
            val primaryUri = alarm?.soundUri?.let { Uri.parse(it) } ?: soundRepository.defaultAlarmSoundUri()
            playSound(primaryUri)
            startEscalation(primaryUri)
            launchRingingUi(alarm)
        }
    }

    private fun playSound(uri: Uri) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmRingingService, uri)
            isLooping = true
            setVolume(0.4f, 0.4f)
            prepare()
            start()
        }
    }

    /**
     * Eskalation während des Klingelns: Lautstärke steigt schrittweise an, und der Ton
     * wechselt alle zwei Minuten zwischen Weckton und Systemstandard, damit man sich nicht
     * an einen einzelnen Sound gewöhnt.
     */
    private fun startEscalation(primaryUri: Uri) {
        escalationJob?.cancel()
        escalationJob = scope.launch {
            val fallbackUri = soundRepository.defaultAlarmSoundUri()
            val alternatingAvailable = fallbackUri != Uri.EMPTY && fallbackUri != primaryUri
            var step = 0
            while (true) {
                delay(ESCALATION_STEP_MILLIS)
                step++
                val volume = (0.4f + 0.15f * step).coerceAtMost(1.0f)

                if (alternatingAvailable && step % 2 == 0) {
                    val nextUri = if ((step / 2) % 2 == 0) fallbackUri else primaryUri
                    playSound(nextUri)
                }
                mediaPlayer?.setVolume(volume, volume)
            }
        }
    }

    private fun launchRingingUi(alarm: AlarmEntity?) {
        val uiIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_ALARM_ID, alarm?.id ?: currentAlarmId)
            putExtra(EXTRA_LABEL, alarm?.label.orEmpty())
            putExtra(EXTRA_HOUR, alarm?.hour ?: 0)
            putExtra(EXTRA_MINUTE, alarm?.minute ?: 0)
        }
        startActivity(uiIntent)
    }

    private fun handleSnooze() {
        stopSoundAndEscalation()
        val delayMinutes = Random.nextLong(SNOOZE_MIN_MINUTES, SNOOZE_MAX_MINUTES + 1)
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        alarmScheduler.scheduleSnooze(currentAlarmId, triggerAt)
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * "Stop" ist kein Freifahrtschein: statt den Wecker einfach verstummen zu lassen, öffnet
     * sich sofort die Notfall-Alarm-Aufgabe (eskalierender Ton + Zuordnungs-Puzzle) – der Ton
     * reißt dabei nicht ab, da der Notfall-Alarm-Dienst direkt übernimmt. Parallel überwacht
     * [PostAlarmMonitoringService] per Sensorik/Health Connect, ob tatsächlich aufgestanden und
     * gelaufen wird; wird das erkannt, beendet sich der Notfall-Alarm automatisch auch ohne
     * gelöstes Puzzle.
     */
    private fun handleStop() {
        stopSoundAndEscalation()

        val emergencyIntent = Intent(this, EmergencyAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
        }
        startActivity(emergencyIntent)

        val monitoringIntent = Intent(this, PostAlarmMonitoringService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
        }
        ContextCompat.startForegroundService(this, monitoringIntent)

        stopForegroundCompat()
        stopSelf()
    }

    private fun stopSoundAndEscalation() {
        escalationJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nocturn:ringing").apply {
            acquire(15 * 60_000L)
        }
    }

    private fun buildNotification(alarmId: Long): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Wecker", NotificationManager.IMPORTANCE_HIGH)
        )

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId.toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getService(
            this, alarmId.toInt(), Intent(this, AlarmRingingService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, alarmId.toInt() + 1, Intent(this, AlarmRingingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setContentTitle("Wecker")
            .setContentText("Zeit zum Aufstehen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Schlummern", snoozeIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSoundAndEscalation()
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
    }
}
