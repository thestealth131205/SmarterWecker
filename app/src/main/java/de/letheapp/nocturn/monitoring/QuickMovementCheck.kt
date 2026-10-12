package de.letheapp.nocturn.monitoring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Startet einen frischen [SensorMovementMonitor] und wartet bis zu [timeoutMillis] auf eine
 * Bewegungsbestätigung — gemeinsame Grundlage für alle kurzen "ist der Nutzer schon wach"-Checks
 * (Stop-Taste, Vorprüfung vor dem automatischen Schicht-Wecker, Wieder-Klingeln-Vorprüfung nach
 * ohne Bewegung gelöstem Notfall-Puzzle). Muss auf einem Dispatcher mit Looper (z. B. Main)
 * aufgerufen werden, da [SensorMovementMonitor] intern `SensorManager.registerListener()` nutzt.
 */
suspend fun SensorMovementMonitorFactory.quickCheck(
    scope: CoroutineScope,
    timeoutMillis: Long,
    sustainedMovementMillis: Long = DEFAULT_SUSTAINED_MOVEMENT_MILLIS
): Boolean = suspendCancellableCoroutine { cont ->
    val monitor = create(sustainedMovementMillis)
    var finished = false
    fun finish(result: Boolean) {
        if (finished) return
        finished = true
        monitor.stop()
        if (cont.isActive) cont.resumeWith(Result.success(result))
    }

    monitor.start { finish(true) }
    // Main-Dispatcher: SensorManager.registerListener()/unregisterListener() erwarten einen
    // Thread mit Looper, siehe Aufrufer (z. B. AlarmRingingService.handleStop()).
    val timeoutJob = scope.launch(Dispatchers.Main) {
        delay(timeoutMillis)
        finish(false)
    }
    cont.invokeOnCancellation {
        timeoutJob.cancel()
        monitor.stop()
    }
}
