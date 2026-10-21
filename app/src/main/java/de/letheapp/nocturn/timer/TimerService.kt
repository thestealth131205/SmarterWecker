package de.letheapp.nocturn.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.MainActivity
import de.letheapp.nocturn.sound.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

const val ACTION_TIMER_START = "de.letheapp.nocturn.timer.action.START"
const val ACTION_TIMER_PAUSE = "de.letheapp.nocturn.timer.action.PAUSE"
const val ACTION_TIMER_RESUME = "de.letheapp.nocturn.timer.action.RESUME"
const val ACTION_TIMER_ADD_MINUTE = "de.letheapp.nocturn.timer.action.ADD_MINUTE"
const val ACTION_TIMER_CANCEL = "de.letheapp.nocturn.timer.action.CANCEL"
/** Von [TimerCompletionReceiver] gesendet, sobald der per [AlarmManager] geplante Zeitpunkt erreicht ist. */
const val ACTION_TIMER_RING = "de.letheapp.nocturn.timer.action.RING"

const val EXTRA_TIMER_DURATION_MILLIS = "extra_timer_duration_millis"
const val EXTRA_TIMER_LABEL = "extra_timer_label"

const val CHANNEL_TIMER = "timer_running"
const val CHANNEL_TIMER_RING = "timer_ring"
const val NOTIFICATION_ID_TIMER = 1101

private const val EXTEND_MINUTE_MILLIS = 60_000L
private const val TICK_INTERVAL_MILLIS = 1000L

/**
 * Hält den Timer am Leben, solange er aktiv ist: zeigt eine live tickende Vordergrund-
 * Benachrichtigung und plant den tatsächlichen Ablauf über einen exakten [AlarmManager]-Alarm
 * ([TimerCompletionReceiver]) statt sich auf eine eigene `delay()`-Schleife zu verlassen — die
 * würde Doze/Prozessabbruch nicht zuverlässig überleben.
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var timerRepository: TimerRepository
    @Inject lateinit var soundRepository: SoundRepository

    private val scope = CoroutineScope(SupervisorJob())
    private var tickJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TIMER_START -> {
                val duration = intent.getLongExtra(EXTRA_TIMER_DURATION_MILLIS, 0L)
                val label = intent.getStringExtra(EXTRA_TIMER_LABEL).orEmpty()
                if (duration <= 0L) { stopSelf(); return START_NOT_STICKY }
                timerRepository.start(duration, label)
                scheduleCompletion(timerRepository.state.value.targetEpochMillis)
                startForeground(NOTIFICATION_ID_TIMER, buildRunningNotification())
                startTicking()
            }
            ACTION_TIMER_PAUSE -> {
                cancelCompletion()
                timerRepository.pause()
                updateNotification()
            }
            ACTION_TIMER_RESUME -> {
                val newTarget = timerRepository.resume()
                if (newTarget != null) scheduleCompletion(newTarget)
                updateNotification()
            }
            ACTION_TIMER_ADD_MINUTE -> {
                val newTarget = timerRepository.addTime(EXTEND_MINUTE_MILLIS)
                if (newTarget != null) scheduleCompletion(newTarget)
                updateNotification()
            }
            ACTION_TIMER_CANCEL -> {
                stopRingingSound()
                cancelCompletion()
                timerRepository.cancel()
                stopTicking()
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_TIMER_RING -> startRinging()
        }
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                if (!timerRepository.state.value.isActive || timerRepository.state.value.isRinging) break
                updateNotification()
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun scheduleCompletion(targetEpochMillis: Long) {
        val pendingIntent = completionPendingIntent()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMillis, pendingIntent)
        }
    }

    private fun cancelCompletion() {
        alarmManager.cancel(completionPendingIntent())
    }

    private fun completionPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerCompletionReceiver::class.java)
        return PendingIntent.getBroadcast(
            this, NOTIFICATION_ID_TIMER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startRinging() {
        stopTicking()
        timerRepository.markRinging()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID_TIMER, buildRingingNotification())
        playRingingSound()
    }

    private fun playRingingSound() {
        mediaPlayer?.release()
        val uri = soundRepository.defaultAlarmSoundUri()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@TimerService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopRingingSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nocturn:timer").apply {
            acquire(30 * 60_000L)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_TIMER, buildRunningNotification())
    }

    private fun contentActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "de.letheapp.nocturn.timer.ACTION_OPEN_TIMER"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(de.letheapp.nocturn.EXTRA_OPEN_TIMER, true)
        }
        return PendingIntent.getActivity(
            this, NOTIFICATION_ID_TIMER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildRunningNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Timer", NotificationManager.IMPORTANCE_LOW)
        )
        val state = timerRepository.state.value
        val remaining = state.remainingMillisNow()
        val text = if (state.isPaused) "Pausiert · ${formatRemaining(remaining)}" else formatRemaining(remaining)

        val pauseResumeAction = if (state.isPaused) {
            NotificationCompat.Action(0, "Fortsetzen", actionPendingIntent(ACTION_TIMER_RESUME))
        } else {
            NotificationCompat.Action(0, "Pause", actionPendingIntent(ACTION_TIMER_PAUSE))
        }

        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setContentTitle(state.label.ifBlank { "Timer" })
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setContentIntent(contentActivityIntent())
            .addAction(pauseResumeAction)
            .addAction(0, "+1 Min.", actionPendingIntent(ACTION_TIMER_ADD_MINUTE))
            .addAction(0, "Abbrechen", actionPendingIntent(ACTION_TIMER_CANCEL))
            .build()
    }

    private fun buildRingingNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER_RING, "Timer abgelaufen", NotificationManager.IMPORTANCE_HIGH)
        )
        val state = timerRepository.state.value
        return NotificationCompat.Builder(this, CHANNEL_TIMER_RING)
            .setContentTitle(state.label.ifBlank { "Timer" })
            .setContentText("Abgelaufen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(contentActivityIntent(), true)
            .setContentIntent(contentActivityIntent())
            .setOngoing(true)
            .addAction(0, "Stop", actionPendingIntent(ACTION_TIMER_CANCEL))
            .build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingingSound()
        stopTicking()
        scope.cancel()
    }

    companion object {
        fun formatRemaining(millis: Long): String {
            val totalSeconds = max(0L, millis) / 1000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}
