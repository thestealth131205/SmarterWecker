package de.letheapp.nocturn.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Eine wählbare Verknüpfung für einen Alarm (job-übergreifend): entweder eine Rotation
 * (Schichtarbeit, [AlarmEntity.rotationId]) oder ein Job im Schüler/Student-Modus
 * (Wochenstundenplan, [AlarmEntity.timetableJobId]). Beide Felder sind nie gleichzeitig gesetzt.
 */
data class RotationOption(
    val label: String,
    val rotationId: Long? = null,
    val timetableJobId: Long? = null
)

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
        val rotationOptions = rotations.map { rotation ->
            RotationOption(
                label = "${rotation.name} · ${jobNameById[rotation.jobId].orEmpty()}",
                rotationId = rotation.id
            )
        }
        val timetableOptions = jobs.filter { it.mode == JobMode.STUDENT.name }.map { job ->
            RotationOption(label = "Stundenplan · ${job.name}", timetableJobId = job.id)
        }
        rotationOptions + timetableOptions
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
