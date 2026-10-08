package de.letheapp.nocturn.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val STEP_THRESHOLD = 6

/**
 * Bewegungserkennung ohne Health Connect (z.B. kein verbundenes Wearable): zählt Schritte
 * über den Schrittzähler-Sensor seit dem Start der Überwachung. Fällt auf den
 * Beschleunigungssensor zurück, falls das Gerät keinen Schrittzähler besitzt.
 */
class SensorMovementMonitor(context: Context) : MovementMonitor {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var onConfirmed: (() -> Unit)? = null
    private var baselineSteps: Float? = null
    private var accelerationStepCount = 0
    private var lastAccelMagnitude = SensorManager.GRAVITY_EARTH

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> handleStepCounter(event.values[0])
                Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start(onConfirmed: () -> Unit) {
        this.onConfirmed = onConfirmed
        baselineSteps = null
        accelerationStepCount = 0

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(listener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
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
        if (totalStepsSinceBoot - baseline >= STEP_THRESHOLD) {
            confirm()
        }
    }

    private fun handleAccelerometer(values: FloatArray) {
        val magnitude = kotlin.math.sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        // Starke Abweichung von der reinen Erdbeschleunigung deutet auf aktive Bewegung
        // (Aufstehen/Gehen) statt eines ruhig liegenden Geräts hin.
        if (kotlin.math.abs(magnitude - lastAccelMagnitude) > 4f) {
            accelerationStepCount++
            if (accelerationStepCount >= STEP_THRESHOLD) {
                confirm()
            }
        }
        lastAccelMagnitude = magnitude
    }

    private fun confirm() {
        onConfirmed?.invoke()
        stop()
    }
}

class SensorMovementMonitorFactory @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(): SensorMovementMonitor = SensorMovementMonitor(context)
}
