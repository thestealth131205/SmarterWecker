package de.letheapp.nocturn.stopwatch

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    private val repository: StopwatchRepository
) : ViewModel() {

    val state: StateFlow<StopwatchState> = repository.state

    fun start() = repository.start()
    fun pause() = repository.pause()
    fun lap() = repository.lap()
    fun reset() = repository.reset()
}
