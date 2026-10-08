package de.letheapp.nocturn.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eine wählbare Rotation (job-übergreifend) zur Verknüpfung mit einem Alarm, siehe [AlarmEntity.rotationId]. */
data class RotationOption(val id: Long, val label: String)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    val alarms: StateFlow<List<AlarmEntity>> = alarmRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rotationOptions: StateFlow<List<RotationOption>> = combine(
        calendarRepository.observeAllRotations(),
        calendarRepository.observeJobs()
    ) { rotations, jobs ->
        val jobNameById = jobs.associateBy({ it.id }, { it.name })
        rotations.map { rotation ->
            RotationOption(rotation.id, "${rotation.name} · ${jobNameById[rotation.jobId].orEmpty()}")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createAlarm(hour: Int, minute: Int) {
        viewModelScope.launch {
            val alarm = AlarmEntity(hour = hour, minute = minute, enabled = true)
            val id = alarmRepository.save(alarm)
            alarmScheduler.schedule(alarm.copy(id = id))
        }
    }

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(enabled = enabled)
            alarmRepository.save(updated)
            if (enabled) alarmScheduler.schedule(updated) else alarmScheduler.cancel(updated.id)
        }
    }

    fun save(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmRepository.save(alarm)
            if (alarm.enabled) alarmScheduler.schedule(alarm) else alarmScheduler.cancel(alarm.id)
        }
    }

    fun delete(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancel(alarm.id)
            alarmRepository.delete(alarm)
        }
    }
}
