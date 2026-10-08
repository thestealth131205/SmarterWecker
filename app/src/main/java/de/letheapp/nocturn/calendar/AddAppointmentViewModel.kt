package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAppointmentViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    fun save(
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        onSaved: () -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            calendarRepository.addAppointment(
                title = title,
                location = location,
                description = description,
                startEpochMillis = startEpochMillis,
                endEpochMillis = maxOf(startEpochMillis, endEpochMillis),
                allDay = allDay
            )
            onSaved()
        }
    }
}
