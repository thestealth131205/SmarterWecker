package de.letheapp.nocturn.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.sound.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ACTION_EMERGENCY_SOLVED = "de.letheapp.nocturn.action.EMERGENCY_SOLVED"
const val CHANNEL_EMERGENCY = "emergency_alarm"
const val NOTIFICATION_ID_EMERGENCY = 1003
private const val ESCALATION_STEP_MILLIS = 30_000L

/**
 * Notfall-Alarm: lässt sich NICHT durch einfaches Stoppen beenden, sondern nur über
 * [ACTION_EMERGENCY_SOLVED], ausgelöst vom gelösten Zuordnungs-Puzzle. Ton eskaliert
 * fortlaufend weiter.
 */
@AndroidEntryPoint
class EmergencyAlarmService : Service() {

    @Inject lateinit var soundRepository: SoundRepository

    private val scope = CoroutineScope(SupervisorJob())
    private var escalationJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EMERGENCY_SOLVED) {
            stopRinging()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        startForeground(NOTIFICATION_ID_EMERGENCY, buildNotification())
        playSound()
        startEscalation()
        return START_STICKY
    }

    private fun playSound() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@EmergencyAlarmService, soundRepository.defaultAlarmSoundUri())
            isLooping = true
            setVolume(0.7f, 0.7f)
            prepare()
            start()
        }
    }

    private fun startEscalation() {
        escalationJob?.cancel()
        escalationJob = scope.launch {
            var step = 0
            while (true) {
                delay(ESCALATION_STEP_MILLIS)
                step++
                val volume = (0.7f + 0.1f * step).coerceAtMost(1.0f)
                mediaPlayer?.setVolume(volume, volume)
                mediaPlayer?.let { player ->
                    player.playbackParams = player.playbackParams.setPitch((1.0f + 0.03f * step).coerceAtMost(1.5f))
                }
            }
        }
    }

    private fun stopRinging() {
        escalationJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForegroundCompat()
        stopSelf()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nocturn:emergency").apply {
            acquire(30 * 60_000L)
        }
    }

    private fun buildNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_EMERGENCY, "Notfall-Alarm", NotificationManager.IMPORTANCE_HIGH)
        )
        return NotificationCompat.Builder(this, CHANNEL_EMERGENCY)
            .setContentTitle("Notfall-Alarm")
            .setContentText("Löse das Puzzle, um den Alarm zu stoppen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
        scope.cancel()
    }
}
