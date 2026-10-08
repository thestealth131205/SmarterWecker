package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Eine für einen Kalendertag anzuzeigende Schicht, bereits mit Job-/Schicht-Namen und Farbe. */
data class DayShiftInfo(
    val jobName: String,
    val shiftName: String,
    val colorArgb: Int
)

@HiltViewModel
class CalendarMonthViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entriesByDay: StateFlow<Map<LocalDate, List<DayShiftInfo>>> = _month
        .flatMapLatest { month ->
            val fromEpochDay = month.atDay(1).toEpochDay()
            val toEpochDay = month.atEndOfMonth().toEpochDay()

            combine(
                calendarRepository.observeCalendarEntries(fromEpochDay, toEpochDay),
                calendarRepository.observeAllShifts(),
                calendarRepository.observeJobs()
            ) { entries, shifts, jobs ->
                val shiftsById = shifts.associateBy { it.id }
                val jobsById = jobs.associateBy { it.id }

                entries
                    .mapNotNull { entry ->
                        val shiftId = entry.shiftId ?: return@mapNotNull null
                        val shift = shiftsById[shiftId] ?: return@mapNotNull null
                        val jobName = jobsById[entry.jobId]?.name.orEmpty()
                        LocalDate.ofEpochDay(entry.dateEpochDay) to
                            DayShiftInfo(jobName = jobName, shiftName = shift.name, colorArgb = shift.colorArgb)
                    }
                    .groupBy({ it.first }, { it.second })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun goToCurrentMonth() {
        _month.value = YearMonth.now()
    }
}
