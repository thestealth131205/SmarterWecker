package de.letheapp.nocturn.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = checkNotNull(savedStateHandle.get<String>("jobId")).toLong()

    val shifts: StateFlow<List<ShiftEntity>> = calendarRepository.observeShiftsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rotations: StateFlow<List<RotationEntity>> = calendarRepository.observeRotationsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveShift(shift: ShiftEntity) {
        viewModelScope.launch { calendarRepository.saveShift(shift) }
    }

    fun deleteShift(shift: ShiftEntity) {
        viewModelScope.launch { calendarRepository.deleteShift(shift) }
    }

    fun deleteRotation(rotation: RotationEntity) {
        viewModelScope.launch { calendarRepository.deleteRotation(rotation) }
    }
}
