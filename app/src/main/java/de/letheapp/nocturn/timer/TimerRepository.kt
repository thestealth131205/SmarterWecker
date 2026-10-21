package de.letheapp.nocturn.timer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ob/wie ein einzelner Timer gerade läuft — NocTurn unterstützt bewusst nur einen aktiven Timer gleichzeitig. */
data class TimerState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isRinging: Boolean = false,
    val totalDurationMillis: Long = 0L,
    val targetEpochMillis: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
    val label: String = ""
) {
    /** Verbleibende Zeit jetzt — berücksichtigt Pause (eingefroren) vs. laufend (aus Zielzeitpunkt berechnet). */
    fun remainingMillisNow(nowMillis: Long = System.currentTimeMillis()): Long = when {
        !isActive -> 0L
        isPaused -> pausedRemainingMillis
        else -> (targetEpochMillis - nowMillis).coerceAtLeast(0L)
    }
}

private const val PREFS_NAME = "nocturn_timer"
private const val KEY_ACTIVE = "active"
private const val KEY_PAUSED = "paused"
private const val KEY_RINGING = "ringing"
private const val KEY_TOTAL_DURATION = "total_duration"
private const val KEY_TARGET = "target"
private const val KEY_PAUSED_REMAINING = "paused_remaining"
private const val KEY_LABEL = "label"

/**
 * Persistiert den Timer-Zustand in SharedPreferences, damit er einen Prozessabbruch der App
 * überlebt — der tatsächliche fristgerechte Ablauf wird aber nicht hierüber, sondern über einen
 * echten [android.app.AlarmManager]-Alarm in [TimerService] sichergestellt.
 */
@Singleton
class TimerRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun start(durationMillis: Long, label: String) {
        val target = System.currentTimeMillis() + durationMillis
        update(
            TimerState(
                isActive = true,
                isPaused = false,
                isRinging = false,
                totalDurationMillis = durationMillis,
                targetEpochMillis = target,
                pausedRemainingMillis = 0L,
                label = label
            )
        )
    }

    fun pause() {
        val current = _state.value
        if (!current.isActive || current.isPaused) return
        update(current.copy(isPaused = true, pausedRemainingMillis = current.remainingMillisNow()))
    }

    /** Gibt die neue Zielzeit zurück (zum Neuplanen des [android.app.AlarmManager]-Alarms), oder null falls nichts zu tun ist. */
    fun resume(): Long? {
        val current = _state.value
        if (!current.isActive || !current.isPaused) return null
        val newTarget = System.currentTimeMillis() + current.pausedRemainingMillis
        update(current.copy(isPaused = false, targetEpochMillis = newTarget))
        return newTarget
    }

    /** Verlängert um [extraMillis]; gibt die neue Zielzeit (laufend) bzw. null (pausiert, nur Rest erhöht) zurück. */
    fun addTime(extraMillis: Long): Long? {
        val current = _state.value
        if (!current.isActive) return null
        return if (current.isPaused) {
            update(current.copy(pausedRemainingMillis = current.pausedRemainingMillis + extraMillis))
            null
        } else {
            val newTarget = current.targetEpochMillis + extraMillis
            update(current.copy(targetEpochMillis = newTarget))
            newTarget
        }
    }

    fun markRinging() {
        val current = _state.value
        update(current.copy(isRinging = true, isPaused = false))
    }

    fun cancel() {
        update(TimerState())
    }

    private fun load(): TimerState = TimerState(
        isActive = prefs.getBoolean(KEY_ACTIVE, false),
        isPaused = prefs.getBoolean(KEY_PAUSED, false),
        isRinging = prefs.getBoolean(KEY_RINGING, false),
        totalDurationMillis = prefs.getLong(KEY_TOTAL_DURATION, 0L),
        targetEpochMillis = prefs.getLong(KEY_TARGET, 0L),
        pausedRemainingMillis = prefs.getLong(KEY_PAUSED_REMAINING, 0L),
        label = prefs.getString(KEY_LABEL, "") ?: ""
    )

    private fun update(newState: TimerState) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, newState.isActive)
            .putBoolean(KEY_PAUSED, newState.isPaused)
            .putBoolean(KEY_RINGING, newState.isRinging)
            .putLong(KEY_TOTAL_DURATION, newState.totalDurationMillis)
            .putLong(KEY_TARGET, newState.targetEpochMillis)
            .putLong(KEY_PAUSED_REMAINING, newState.pausedRemainingMillis)
            .putString(KEY_LABEL, newState.label)
            .apply()
        _state.value = newState
    }
}
