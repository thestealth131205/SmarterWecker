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

/** Ein an einem Kalendertag anzuzeigender Eintrag — entweder eine Schicht oder ein freier Termin. */
sealed interface CalendarDayEntry {
    val colorArgb: Int
    val label: String
}

data class ShiftDayEntry(
    val jobName: String,
    val shiftName: String,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = shiftName
}

data class AppointmentDayEntry(
    val appointmentId: Long,
    val title: String,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = title
}

@HiltViewModel
class CalendarMonthViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entriesByDay: StateFlow<Map<LocalDate, List<CalendarDayEntry>>> = _month
        .flatMapLatest { month ->
            val fromEpochDay = month.atDay(1).toEpochDay()
            val toEpochDay = month.atEndOfMonth().toEpochDay()

            combine(
                calendarRepository.observeCalendarEntries(fromEpochDay, toEpochDay),
                calendarRepository.observeAllShifts(),
                calendarRepository.observeJobs(),
                calendarRepository.observeAppointments(fromEpochDay, toEpochDay)
            ) { entries, shifts, jobs, appointments ->
                val shiftsById = shifts.associateBy { it.id }
                val jobsById = jobs.associateBy { it.id }

                val shiftEntries = entries
                    .mapNotNull { entry ->
                        val shiftId = entry.shiftId ?: return@mapNotNull null
                        val shift = shiftsById[shiftId] ?: return@mapNotNull null
                        val jobName = jobsById[entry.jobId]?.name.orEmpty()
                        LocalDate.ofEpochDay(entry.dateEpochDay) to
                            (ShiftDayEntry(jobName = jobName, shiftName = shift.name, colorArgb = shift.colorArgb) as CalendarDayEntry)
                    }

                val appointmentEntries = appointments.flatMap { appointment ->
                    (appointment.startEpochDay..appointment.endEpochDay)
                        .filter { it in fromEpochDay..toEpochDay }
                        .map { epochDay ->
                            LocalDate.ofEpochDay(epochDay) to
                                (AppointmentDayEntry(
                                    appointmentId = appointment.id,
                                    title = appointment.title,
                                    colorArgb = appointment.colorArgb
                                ) as CalendarDayEntry)
                        }
                }

                (shiftEntries + appointmentEntries).groupBy({ it.first }, { it.second })
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
