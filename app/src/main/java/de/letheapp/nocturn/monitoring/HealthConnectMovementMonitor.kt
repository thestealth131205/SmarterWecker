package de.letheapp.nocturn.monitoring

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val STEP_THRESHOLD = 15
private const val POLL_INTERVAL_MILLIS = 20_000L

val READ_STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)

/**
 * Bewegungserkennung über Health Connect (z.B. Schritte von einer verbundenen Smartwatch).
 * Läuft nur an, wenn Health Connect verfügbar ist und die Lese-Berechtigung bereits erteilt
 * wurde – sonst bleibt die reine Sensor-Fallback-Erkennung maßgeblich.
 */
class HealthConnectMovementMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) : MovementMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    override fun start(onConfirmed: () -> Unit) {
        val client = client() ?: return
        val startTime = Instant.now()

        pollJob = scope.launch {
            val granted = runCatching {
                client.permissionController.getGrantedPermissions()
            }.getOrDefault(emptySet())
            if (READ_STEPS_PERMISSION !in granted) return@launch

            while (true) {
                val steps = runCatching {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(startTime, Instant.now())
                        )
                    )[StepsRecord.COUNT_TOTAL] ?: 0L
                }.getOrDefault(0L)

                if (steps >= STEP_THRESHOLD) {
                    onConfirmed()
                    return@launch
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
    }
}
