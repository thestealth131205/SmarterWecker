package de.letheapp.nocturn.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.iobroker.IoBrokerDataPoint
import de.letheapp.nocturn.iobroker.IoBrokerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val shiftWakeupAlarmSyncer: ShiftWakeupAlarmSyncer,
    private val ioBrokerRepository: IoBrokerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = checkNotNull(savedStateHandle.get<String>("jobId")).toLong()

    val job: StateFlow<JobEntity?> = calendarRepository.observeJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shifts: StateFlow<List<ShiftEntity>> = calendarRepository.observeShiftsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rotations: StateFlow<List<RotationEntity>> = calendarRepository.observeRotationsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessons: StateFlow<List<LessonEntity>> = calendarRepository.observeLessonsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ioBrokerConnected: StateFlow<Boolean> = ioBrokerRepository.observeAccount()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _ioBrokerDataPoints = MutableStateFlow<List<IoBrokerDataPoint>>(emptyList())
    val ioBrokerDataPoints: StateFlow<List<IoBrokerDataPoint>> = _ioBrokerDataPoints

    /** Lädt die verfügbaren ioBroker-Datenpunkte neu (z. B. beim Öffnen eines Aktions-Auswahl-Dialogs im Schicht-Editor). */
    fun refreshIoBrokerDataPoints() {
        viewModelScope.launch {
            ioBrokerRepository.fetchDataPoints()
                .onSuccess { _ioBrokerDataPoints.value = it }
        }
    }

    fun setMode(mode: JobMode) {
        viewModelScope.launch { calendarRepository.setJobMode(jobId, mode) }
    }

    fun saveShift(shift: ShiftEntity) {
        viewModelScope.launch {
            calendarRepository.saveShift(shift)
            shiftWakeupAlarmSyncer.sync()
        }
    }

    fun deleteShift(shift: ShiftEntity) {
        viewModelScope.launch {
            calendarRepository.deleteShift(shift)
            shiftWakeupAlarmSyncer.sync()
        }
    }

    fun deleteRotation(rotation: RotationEntity) {
        viewModelScope.launch { calendarRepository.deleteRotation(rotation) }
    }

    fun saveLesson(lesson: LessonEntity) {
        viewModelScope.launch { calendarRepository.saveLesson(lesson) }
    }

    fun deleteLesson(lesson: LessonEntity) {
        viewModelScope.launch { calendarRepository.deleteLesson(lesson) }
    }
}
