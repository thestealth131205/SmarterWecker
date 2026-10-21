package de.letheapp.nocturn.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import de.letheapp.nocturn.alarm.EXTRA_ALARM_ID
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.iobroker.IoBrokerRepository
import de.letheapp.nocturn.monitoring.AutomaticShiftRetryScheduler
import de.letheapp.nocturn.sound.LoudnessBooster
import de.letheapp.nocturn.sound.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ACTION_EMERGENCY_SOLVED = "de.letheapp.nocturn.action.EMERGENCY_SOLVED"
/** Broadcast an [EmergencyAlarmActivity]: Bewegung wurde erkannt, Screen kann sich ohne gelöstes Puzzle schließen. */
const val ACTION_EMERGENCY_AUTO_RESOLVED = "de.letheapp.nocturn.action.EMERGENCY_AUTO_RESOLVED"
/**
 * Extra zu [ACTION_EMERGENCY_SOLVED]: true, wenn die Auflösung von erkannter Bewegung kam (siehe
 * [de.letheapp.nocturn.monitoring.PostAlarmMonitoringService]), false/fehlend bei einem tatsächlich
 * gelösten Puzzle ohne bestätigte Bewegung. Entscheidet, ob für einen automatischen Schicht-Wecker
 * ein Wieder-Klingeln geplant wird, siehe [AutomaticShiftRetryScheduler].
 */
const val EXTRA_RESOLVED_VIA_MOVEMENT = "extra_resolved_via_movement"
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
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var ioBrokerRepository: IoBrokerRepository
    @Inject lateinit var automaticShiftRetryScheduler: AutomaticShiftRetryScheduler

    private val scope = CoroutineScope(SupervisorJob())
    private var escalationJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmId: Long = -1
    private var isRinging = false
    private var currentVolume = 0.7f
    private var currentPitch = 1.0f
    private var currentUri: Uri = Uri.EMPTY
    private var toneCompletionCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EMERGENCY_SOLVED) {
            val resolvedViaMovement = intent.getBooleanExtra(EXTRA_RESOLVED_VIA_MOVEMENT, false)
            val solvedAlarmId = alarmId
            // Erst das ggf. nötige Wieder-Klingeln verbindlich einplanen, DANACH stopRinging()
            // (stoppt diesen Dienst via stopSelf()) – sonst könnte scope abgebrochen werden,
            // bevor die DB-Abfrage/AlarmManager-Planung abgeschlossen ist.
            scope.launch {
                if (!resolvedViaMovement) {
                    automaticShiftRetryScheduler.scheduleRetryIfApplicable(solvedAlarmId)
                }
                stopRinging()
            }
            return START_NOT_STICKY
        }

        alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: alarmId
        // Full-Screen-Intent-Benachrichtigung zeigt die Aufgabe zuverlässig sofort an, auch
        // wenn die App gerade keine sichtbare Activity hat – deshalb immer aktuell halten,
        // aber Ton/Eskalation nur einmal starten (sonst Audio-Hänger bei mehrfachem Aufruf,
        // z. B. wenn [EmergencyAlarmActivity] den Dienst zusätzlich anstößt).
        startForeground(NOTIFICATION_ID_EMERGENCY, buildNotification())
        if (!isRinging) {
            isRinging = true
            toneCompletionCount = 0
            currentVolume = 0.7f
            currentPitch = 1.0f
            acquireWakeLock()
            playSound()
            startEscalation()
            turnOnLampIfConfigured()
        }
        return START_STICKY
    }

    /** Schaltet die diesem Alarm zugeordnete ioBroker-Lampe/Steckdose ein (siehe CLAUDE.md "Smart-Home-Kopplung"). */
    private fun turnOnLampIfConfigured() {
        scope.launch {
            val dataPointId = alarmRepository.getById(alarmId)?.lampDataPointId ?: return@launch
            ioBrokerRepository.turnOn(dataPointId)
        }
    }

    /**
     * Spielt [uri] (Default: der Standard-Notfall-Ton) einmal durch statt in einer Dauerschleife
     * (`isLooping = false`) – so lässt sich über [onToneCompleted] zählen, wie oft der Ton bereits
     * einmal komplett durchgelaufen ist, und alle zwei solcher Wiederholungen zufällig zu einem
     * anderen Ton aus der Töne-Liste wechseln (siehe [onToneCompleted]), damit man sich auch beim
     * Notfall-Alarm nicht an einen einzelnen Sound gewöhnt.
     */
    private fun playSound(uri: Uri = soundRepository.defaultAlarmSoundUri()) {
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        currentUri = uri
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@EmergencyAlarmService, uri)
            isLooping = false
            setVolume(currentVolume, currentVolume)
            setOnCompletionListener { onToneCompleted() }
            prepare()
            start()
            playbackParams = playbackParams.setPitch(currentPitch)
        }
        mediaPlayer = player
        // Lautheits-Analyse dekodiert kurz Audio (blockierend) – nicht direkt hier, da playSound()
        // aus onStartCommand() (Main-Thread) aufgerufen wird; stattdessen im Hintergrund nachziehen.
        scope.launch {
            val enhancer = LoudnessBooster.attach(this@EmergencyAlarmService, uri, player.audioSessionId)
            if (mediaPlayer === player) {
                loudnessEnhancer = enhancer
            } else {
                enhancer?.release()
            }
        }
    }

    /** Ton ist einmal komplett durchgelaufen – alle zwei solcher Durchläufe zufällig den Ton wechseln. */
    private fun onToneCompleted() {
        if (!isRinging) return
        toneCompletionCount++
        val nextUri = if (toneCompletionCount % 2 == 0) pickRandomTone(excluding = currentUri) else currentUri
        playSound(nextUri)
    }

    private fun pickRandomTone(excluding: Uri): Uri {
        val options = soundRepository.bundledSounds().map { it.uri }
        val candidates = options.filter { it != excluding }.ifEmpty { options }
        return candidates.randomOrNull() ?: excluding
    }

    private fun startEscalation() {
        escalationJob?.cancel()
        escalationJob = scope.launch {
            var step = 0
            while (true) {
                delay(ESCALATION_STEP_MILLIS)
                step++
                currentVolume = (0.7f + 0.1f * step).coerceAtMost(1.0f)
                currentPitch = (1.0f + 0.03f * step).coerceAtMost(1.5f)
                mediaPlayer?.setVolume(currentVolume, currentVolume)
                mediaPlayer?.let { player ->
                    player.playbackParams = player.playbackParams.setPitch(currentPitch)
                }
            }
        }
    }

    private fun stopRinging() {
        isRinging = false
        escalationJob?.cancel()
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
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

        val activityIntent = Intent(this, EmergencyAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this, alarmId.toInt(), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_EMERGENCY)
            .setContentTitle("Notfall-Alarm")
            .setContentText("Löse das Puzzle, um den Alarm zu stoppen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(activityPendingIntent, true)
            .setContentIntent(activityPendingIntent)
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
