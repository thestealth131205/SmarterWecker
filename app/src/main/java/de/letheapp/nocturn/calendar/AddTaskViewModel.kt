package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.TaskEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    suspend fun loadForEdit(taskId: Long): TaskEntity? = calendarRepository.getTask(taskId)

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

    fun update(
        taskId: Long,
        title: String,
        notes: String,
        dueEpochDay: Long,
        dueEpochMillis: Long?,
        allDay: Boolean,
        onSaved: () -> Unit
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            calendarRepository.updateTaskFull(
                id = taskId,
                title = title,
                notes = notes,
                dueEpochDay = dueEpochDay,
                dueEpochMillis = dueEpochMillis,
                allDay = allDay
            )
            onSaved()
        }
    }

    fun delete(taskId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            calendarRepository.getTask(taskId)?.let { calendarRepository.deleteTask(it) }
            onDeleted()
        }
    }
}
