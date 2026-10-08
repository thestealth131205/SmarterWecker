package de.letheapp.nocturn.monitoring

import javax.inject.Inject

/**
 * Kombiniert Health-Connect- und Sensor-Erkennung: Aufstehen wird bestätigt, sobald
 * eine der beiden Quellen genug Bewegung meldet.
 */
class CombinedMovementMonitor @Inject constructor(
    private val healthConnectMonitor: HealthConnectMovementMonitor,
    private val sensorMonitorFactory: SensorMovementMonitorFactory
) : MovementMonitor {

    private val sensorMonitor by lazy { sensorMonitorFactory.create() }
    private var confirmed = false

    override fun start(onConfirmed: () -> Unit) {
        confirmed = false
        val confirmOnce: () -> Unit = {
            if (!confirmed) {
                confirmed = true
                stop()
                onConfirmed()
            }
        }
        healthConnectMonitor.start(confirmOnce)
        sensorMonitor.start(confirmOnce)
    }

    override fun stop() {
        healthConnectMonitor.stop()
        sensorMonitor.stop()
    }
}
