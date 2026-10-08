package de.letheapp.nocturn.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Editor für eine Rotation: Nutzer trägt Tag für Tag die geltende Schicht ein, bis sich
 * das Muster wiederholen würde. Die Zykluslänge ist schlicht die Anzahl der eingegebenen
 * Tage; beim Speichern schreibt [CalendarRepository.saveRotation] daraus den Kalender für
 * mehrere Jahre fort.
 */
@HiltViewModel
class RotationEditorViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = checkNotNull(savedStateHandle.get<String>("jobId")).toLong()
    private val rotationId: Long? = savedStateHandle.get<String>("rotationId")?.toLongOrNull()?.takeIf { it >= 0 }
    val isNew: Boolean = rotationId == null

    val shifts: StateFlow<List<ShiftEntity>> = calendarRepository.observeShiftsForJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var name by mutableStateOf("")
    var startDateEpochDay by mutableStateOf(LocalDate.now().toEpochDay())
    var loaded by mutableStateOf(isNew)

    val days = mutableStateListOf<Long?>()

    init {
        val id = rotationId
        if (id != null) {
            viewModelScope.launch {
                calendarRepository.getRotation(id)?.let { rotation ->
                    name = rotation.name
                    startDateEpochDay = rotation.startDateEpochDay
                }
                val rotationDays = calendarRepository.getRotationDaysOnce(id).sortedBy { it.dayIndex }
                days.clear()
                days.addAll(rotationDays.map { it.shiftId })
                loaded = true
            }
        }
    }

    fun addDay() {
        days.add(null)
    }

    fun removeLastDay() {
        if (days.isNotEmpty()) days.removeAt(days.lastIndex)
    }

    fun setDay(index: Int, shiftId: Long?) {
        if (index in days.indices) days[index] = shiftId
    }

    fun save(onDone: () -> Unit) {
        if (name.isBlank() || days.isEmpty()) return
        viewModelScope.launch {
            calendarRepository.saveRotation(
                jobId = jobId,
                rotationId = rotationId,
                name = name.trim(),
                startDateEpochDay = startDateEpochDay,
                dayShiftIds = days.toList()
            )
            onDone()
        }
    }
}
