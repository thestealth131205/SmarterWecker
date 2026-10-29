package de.letheapp.nocturn.monitoring

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

const val NOTIFICATION_ID_PRE_ALARM_LOCATION_CHECK = 1006

/** Mindestens 3, höchstens 4 Messungen über das Prüffenster verteilt, siehe Klassendoku. */
private const val LOCATION_SAMPLE_COUNT = 4
private const val MIN_SAMPLE_INTERVAL_MILLIS = 60_000L
private const val LOCATION_FIX_TIMEOUT_MILLIS = 8_000L

/**
 * Üblicher Drift einer groben Netzwerk-/WLAN-Positionsbestimmung im Stillstand liegt meist
 * deutlich unter 30 m; ab [LOCATION_DRIFT_THRESHOLD_METERS] gilt eine Positionsänderung als
 * echte Bewegung statt bloßem Messrauschen.
 */
private const val LOCATION_DRIFT_THRESHOLD_METERS = 50.0

/**
 * Zusätzlich zur rein sensorbasierten Vorprüfung ([PreAlarmMovementCheckService]): nimmt ab
 * [de.letheapp.nocturn.alarm.AlarmScheduler] bis zu [LOCATION_SAMPLE_COUNT] über die letzten
 * 10 Minuten vor dem geplanten Klingeln verteilte Standort-Messungen (grobe Position, siehe
 * [android.Manifest.permission.ACCESS_COARSE_LOCATION]) und vergleicht sie paarweise. Weicht eine
 * spätere Messung um mehr als [LOCATION_DRIFT_THRESHOLD_METERS] von einer früheren ab, hat sich
 * der Nutzer nachweislich fortbewegt (z. B. das Zimmer/die Wohnung verlassen) — unabhängig davon,
 * ob das kurze Sensor-Prüffenster selbst eine Bewegung erfasst hat. In diesem Fall wird wie beim
 * Sensor-Treffer der eigentliche Alarm abgesagt und eine "Wecken geskippt"-Benachrichtigung
 * gezeigt. Bleiben alle Messungen innerhalb der Drift-Schwelle (oder fehlt die Berechtigung/ein
 * Standort-Provider), endet der Dienst einfach selbst ohne Wirkung.
 */
@AndroidEntryPoint
class PreAlarmLocationCheckService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        val mainTriggerAtMillis = intent?.getLongExtra(EXTRA_MAIN_TRIGGER_AT_MILLIS, -1L) ?: -1L
        if (alarmId == -1L || mainTriggerAtMillis <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID_PRE_ALARM_LOCATION_CHECK, buildNotification())

        job?.cancel()
        job = scope.launch {
            val alarm = alarmRepository.getById(alarmId)
            if (alarm == null || alarm.autoShiftWakeupEpochDay == null || !hasLocationPermission()) {
                stopSelfCleanly()
                return@launch
            }

            // Feste, einmalig berechnete Taktung statt Nachjustierung pro Durchlauf – sonst würden
            // die Abstände mit jeder Runde kürzer statt gleichmäßig über das Fenster verteilt zu
            // bleiben, und es kämen am Ende deutlich mehr als die gewünschten 3–4 Messungen zusammen.
            val sampleIntervalMillis = ((mainTriggerAtMillis - System.currentTimeMillis()) / (LOCATION_SAMPLE_COUNT - 1))
                .coerceAtLeast(MIN_SAMPLE_INTERVAL_MILLIS)

            val samples = mutableListOf<Location>()
            repeat(LOCATION_SAMPLE_COUNT) { sampleIndex ->
                if (System.currentTimeMillis() >= mainTriggerAtMillis) return@launch

                val sample = freshLocation() ?: lastKnownLocation()
                if (sample != null) {
                    samples.add(sample)
                    if (samples.size >= 2 && maxPairwiseDistanceMeters(samples) >= LOCATION_DRIFT_THRESHOLD_METERS) {
                        alarmScheduler.cancel(alarmId)
                        showSkippedNotification()
                        stopSelfCleanly()
                        return@launch
                    }
                }

                val remainingMillis = mainTriggerAtMillis - System.currentTimeMillis()
                val isLastSample = sampleIndex == LOCATION_SAMPLE_COUNT - 1
                if (remainingMillis > 0L && !isLastSample) {
                    delay(minOf(sampleIntervalMillis, remainingMillis))
                }
            }
            stopSelfCleanly()
        }
        return START_STICKY
    }

    private fun maxPairwiseDistanceMeters(samples: List<Location>): Float {
        var max = 0f
        for (i in samples.indices) {
            for (j in i + 1 until samples.size) {
                max = maxOf(max, samples[i].distanceTo(samples[j]))
            }
        }
        return max
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun lastKnownLocation(): Location? {
        val locationManager = ContextCompat.getSystemService(this, LocationManager::class.java) ?: return null
        return locationManager.allProviders
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Fordert eine frische Positionsmessung an, statt sich auf ggf. veraltete Cache-Werte zu verlassen. */
    private suspend fun freshLocation(): Location? {
        val locationManager = ContextCompat.getSystemService(this, LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null

        return withTimeoutOrNull(LOCATION_FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        { it.run() },
                        { location -> if (continuation.isActive) continuation.resume(location) }
                    )
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun showSkippedNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID_PRE_ALARM_SKIPPED,
            NotificationCompat.Builder(this, CHANNEL_PRE_ALARM_CHECK)
                .setContentTitle("Wecken geskippt")
                .setContentText("Du bist schon unterwegs – der Wecker klingelt diesmal nicht.")
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
            .setContentTitle("Prüfe, ob du dich schon fortbewegt hast")
            .setContentText("Vor dem Wecken wird per Standort geprüft, ob der Alarm nötig ist.")
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
