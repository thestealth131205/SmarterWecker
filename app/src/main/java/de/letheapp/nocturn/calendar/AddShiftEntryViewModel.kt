package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddShiftEntryViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    val jobs: StateFlow<List<JobEntity>> = calendarRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedJobId = MutableStateFlow<Long?>(null)
    val selectedJobId: StateFlow<Long?> = _selectedJobId

    @OptIn(ExperimentalCoroutinesApi::class)
    val shiftsForSelectedJob: StateFlow<List<ShiftEntity>> = _selectedJobId
        .flatMapLatest { jobId -> if (jobId == null) flowOf(emptyList()) else calendarRepository.observeShiftsForJob(jobId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectJob(jobId: Long) {
        _selectedJobId.value = jobId
    }

    fun save(jobId: Long, shiftId: Long, epochDay: Long, onSaved: () -> Unit) {
        viewModelScope.launch {
            calendarRepository.saveManualShiftEntry(jobId, shiftId, epochDay)
            onSaved()
        }
    }
}
