package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    fun save(
        title: String,
        notes: String,
        dueEpochDay: Long,
        dueEpochMillis: Long?,
        allDay: Boolean,
        onSaved: () -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            calendarRepository.addTask(
                title = title,
                notes = notes,
                dueEpochDay = dueEpochDay,
                dueEpochMillis = if (allDay) null else dueEpochMillis,
                allDay = allDay
            )
            onSaved()
        }
    }
}
