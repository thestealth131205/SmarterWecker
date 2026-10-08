package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobListViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    val jobs: StateFlow<List<JobEntity>> = calendarRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addJob(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { calendarRepository.addJob(name.trim()) }
    }

    fun deleteJob(job: JobEntity) {
        viewModelScope.launch { calendarRepository.deleteJob(job) }
    }
}
