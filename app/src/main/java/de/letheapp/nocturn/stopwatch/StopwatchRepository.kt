package de.letheapp.nocturn.stopwatch

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class StopwatchState(
    val isRunning: Boolean = false,
    val startEpochMillis: Long = 0L,
    val accumulatedMillis: Long = 0L,
    val laps: List<Long> = emptyList()
) {
    fun elapsedMillisNow(nowMillis: Long = System.currentTimeMillis()): Long =
        if (isRunning) accumulatedMillis + (nowMillis - startEpochMillis) else accumulatedMillis
}

private const val PREFS_NAME = "nocturn_stopwatch"
private const val KEY_RUNNING = "running"
private const val KEY_START = "start"
private const val KEY_ACCUMULATED = "accumulated"
private const val KEY_LAPS = "laps"

/**
 * Persistiert den Stopwatch-Zustand in SharedPreferences (überlebt Prozessabbruch/Rotation),
 * bewusst ohne eigenen Foreground-Service — anders als der Timer gibt es hier keinen festen,
 * im Hintergrund zuverlässig abzufeuernden Zeitpunkt, der das rechtfertigen würde; die laufende
 * Zeit wird beim nächsten Öffnen des Screens einfach aus [StopwatchState.startEpochMillis] neu
 * berechnet.
 */
@Singleton
class StopwatchRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    fun start() {
        val current = _state.value
        if (current.isRunning) return
        update(current.copy(isRunning = true, startEpochMillis = System.currentTimeMillis()))
    }

    fun pause() {
        val current = _state.value
        if (!current.isRunning) return
        update(
            current.copy(
                isRunning = false,
                accumulatedMillis = current.elapsedMillisNow(),
                startEpochMillis = 0L
            )
        )
    }

    fun lap() {
        val current = _state.value
        update(current.copy(laps = current.laps + current.elapsedMillisNow()))
    }

    fun reset() {
        update(StopwatchState())
    }

    private fun load(): StopwatchState = StopwatchState(
        isRunning = prefs.getBoolean(KEY_RUNNING, false),
        startEpochMillis = prefs.getLong(KEY_START, 0L),
        accumulatedMillis = prefs.getLong(KEY_ACCUMULATED, 0L),
        laps = prefs.getString(KEY_LAPS, "")
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()
    )

    private fun update(newState: StopwatchState) {
        prefs.edit()
            .putBoolean(KEY_RUNNING, newState.isRunning)
            .putLong(KEY_START, newState.startEpochMillis)
            .putLong(KEY_ACCUMULATED, newState.accumulatedMillis)
            .putString(KEY_LAPS, newState.laps.joinToString(","))
            .apply()
        _state.value = newState
    }
}
