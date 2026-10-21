package de.letheapp.nocturn.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timerRepository: TimerRepository
) : ViewModel() {

    val state: StateFlow<TimerState> = timerRepository.state

    fun start(durationMillis: Long, label: String) {
        sendAction(ACTION_TIMER_START) {
            putExtra(EXTRA_TIMER_DURATION_MILLIS, durationMillis)
            putExtra(EXTRA_TIMER_LABEL, label)
        }
    }

    fun pauseOrResume() {
        val action = if (timerRepository.state.value.isPaused) ACTION_TIMER_RESUME else ACTION_TIMER_PAUSE
        sendAction(action)
    }

    fun addMinute() = sendAction(ACTION_TIMER_ADD_MINUTE)

    fun cancel() = sendAction(ACTION_TIMER_CANCEL)

    private fun sendAction(action: String, configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
            configure()
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
