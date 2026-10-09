package de.letheapp.nocturn.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val STEP_THRESHOLD = 6
private const val ACCEL_DELTA_THRESHOLD = 4f
private const val GYRO_MAGNITUDE_THRESHOLD = 1.2f

/**
 * Wie lange ununterbrochen Bewegung erkannt werden muss, bevor sie als "aufgestanden/läuft"
 * gilt. Verhindert, dass ein einzelnes kurzes Aufschütteln des Geräts (z. B. beim Greifen nach
 * dem Handy zum Stop-Drücken) fälschlich als Aufstehen gewertet wird.
 */
private const val SUSTAINED_MOVEMENT_MILLIS = 3_500L

/** Pause zwischen zwei Sensor-Ausschlägen, bis eine angefangene Bewegungs-Serie als abgebrochen gilt. */
private const val MOVEMENT_GAP_TOLERANCE_MILLIS = 600L

/**
 * Bewegungserkennung ohne Health Connect (z.B. kein verbundenes Wearable): zählt Schritte über
 * den Schrittzähler-Sensor seit dem Start der Überwachung, ergänzt um Beschleunigungs- und
 * Gyroskop-Auswertung für Geräte ohne Schrittzähler bzw. als Zusatzsignal. Bestätigt wird erst,
 * wenn die Bewegung mindestens [SUSTAINED_MOVEMENT_MILLIS] ununterbrochen anhält — ein kurzer
 * Ausschlag allein reicht nicht.
 */
class SensorMovementMonitor(context: Context) : MovementMonitor {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var onConfirmed: (() -> Unit)? = null
    private var baselineSteps: Float? = null
    private var monitoringStartElapsedMillis = 0L
    private var lastAccelMagnitude = SensorManager.GRAVITY_EARTH
    private var movementStreakStartElapsedMillis: Long? = null
    private var lastMovementEventElapsedMillis: Long = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> handleStepCounter(event.values[0])
                Sensor.TYPE_ACCELEROMETER -> {
                    val magnitude = kotlin.math.sqrt(
                        event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]
                    )
                    // Starke Abweichung von der reinen Erdbeschleunigung deutet auf aktive
                    // Bewegung (Aufstehen/Gehen) statt eines ruhig liegenden Geräts hin.
                    val isSignificant = kotlin.math.abs(magnitude - lastAccelMagnitude) > ACCEL_DELTA_THRESHOLD
                    lastAccelMagnitude = magnitude
                    if (isSignificant) onMovementSample()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val angularMagnitude = kotlin.math.sqrt(
                        event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]
                    )
                    if (angularMagnitude > GYRO_MAGNITUDE_THRESHOLD) onMovementSample()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start(onConfirmed: () -> Unit) {
        this.onConfirmed = onConfirmed
        baselineSteps = null
        movementStreakStartElapsedMillis = null
        monitoringStartElapsedMillis = SystemClock.elapsedRealtime()

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(listener)
        onConfirmed = null
    }

    private fun handleStepCounter(totalStepsSinceBoot: Float) {
        val baseline = baselineSteps
        if (baseline == null) {
            baselineSteps = totalStepsSinceBoot
            return
        }
        val elapsedSinceStart = SystemClock.elapsedRealtime() - monitoringStartElapsedMillis
        if (totalStepsSinceBoot - baseline >= STEP_THRESHOLD && elapsedSinceStart >= SUSTAINED_MOVEMENT_MILLIS) {
            confirm()
        }
    }

    /** Gemeinsame Auswertung für Accelerometer- und Gyroskop-Ausschläge: nur eine ununterbrochene Serie zählt. */
    private fun onMovementSample() {
        val now = SystemClock.elapsedRealtime()
        val streakStart = movementStreakStartElapsedMillis
        movementStreakStartElapsedMillis = if (streakStart == null || now - lastMovementEventElapsedMillis > MOVEMENT_GAP_TOLERANCE_MILLIS) {
            now
        } else {
            streakStart
        }
        lastMovementEventElapsedMillis = now

        if (now - movementStreakStartElapsedMillis!! >= SUSTAINED_MOVEMENT_MILLIS) {
            confirm()
        }
    }

    private fun confirm() {
        onConfirmed?.invoke()
        stop()
    }
}

class SensorMovementMonitorFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(): SensorMovementMonitor = SensorMovementMonitor(context)
}
