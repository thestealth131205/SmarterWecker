package de.letheapp.nocturn.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Ein an einem Kalendertag anzuzeigender Eintrag — Schicht, freier Termin oder Feiertag/Ferien. */
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
    val priority: String,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = title
}

data class HolidayDayEntry(
    val name: String,
    val isSchoolHoliday: Boolean,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = name
}

private const val PUBLIC_HOLIDAY_COLOR_ARGB = 0xFFE53935.toInt()
private const val SCHOOL_HOLIDAY_COLOR_ARGB = 0xFF43A047.toInt()

@HiltViewModel
class CalendarMonthViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val holidaySettings = holidayRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(_month, holidaySettings) { month, settings -> month to settings }
                .collectLatest { (month, settings) ->
                    if (settings != null && (settings.showPublicHolidays || settings.showSchoolHolidays)) {
                        holidayRepository.ensureYearLoaded(settings.subdivisionCode, month.year)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesByDay: StateFlow<Map<LocalDate, List<CalendarDayEntry>>> =
        combine(_month, holidaySettings) { month, settings -> month to settings }
            .flatMapLatest { (month, settings) ->
                val fromEpochDay = month.atDay(1).toEpochDay()
                val toEpochDay = month.atEndOfMonth().toEpochDay()

                val shiftAndAppointmentFlow = combine(
                    calendarRepository.observeCalendarEntries(fromEpochDay, toEpochDay),
                    calendarRepository.observeAllShifts(),
                    calendarRepository.observeJobs(),
                    calendarRepository.observeAppointments(fromEpochDay, toEpochDay)
                ) { entries, shifts, jobs, appointments ->
                    val shiftsById = shifts.associateBy { it.id }
                    val jobsById = jobs.associateBy { it.id }

                    val shiftEntries = entries.mapNotNull { entry ->
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
                                        priority = appointment.priority,
                                        colorArgb = appointment.colorArgb
                                    ) as CalendarDayEntry)
                            }
                    }

                    shiftEntries + appointmentEntries
                }

                val manualShiftFlow = combine(
                    calendarRepository.observeManualShifts(fromEpochDay, toEpochDay),
                    calendarRepository.observeAllShifts(),
                    calendarRepository.observeJobs()
                ) { manualEntries, shifts, jobs ->
                    val shiftsById = shifts.associateBy { it.id }
                    val jobsById = jobs.associateBy { it.id }
                    manualEntries.mapNotNull { entry ->
                        val shift = shiftsById[entry.shiftId] ?: return@mapNotNull null
                        val jobName = jobsById[entry.jobId]?.name.orEmpty()
                        LocalDate.ofEpochDay(entry.dateEpochDay) to
                            (ShiftDayEntry(jobName = jobName, shiftName = shift.name, colorArgb = shift.colorArgb) as CalendarDayEntry)
                    }
                }

                val holidayFlow = if (settings != null && (settings.showPublicHolidays || settings.showSchoolHolidays)) {
                    holidayRepository.observeHolidays(settings.subdivisionCode, fromEpochDay, toEpochDay).map { holidays ->
                        holidays
                            .filter {
                                (it.type == HolidayType.PUBLIC.name && settings.showPublicHolidays) ||
                                    (it.type == HolidayType.SCHOOL.name && settings.showSchoolHolidays)
                            }
                            .flatMap { holiday ->
                                (maxOf(holiday.startEpochDay, fromEpochDay)..minOf(holiday.endEpochDay, toEpochDay))
                                    .map { epochDay ->
                                        val isSchool = holiday.type == HolidayType.SCHOOL.name
                                        LocalDate.ofEpochDay(epochDay) to
                                            (HolidayDayEntry(
                                                name = holiday.name,
                                                isSchoolHoliday = isSchool,
                                                colorArgb = if (isSchool) SCHOOL_HOLIDAY_COLOR_ARGB else PUBLIC_HOLIDAY_COLOR_ARGB
                                            ) as CalendarDayEntry)
                                    }
                            }
                    }
                } else {
                    flowOf(emptyList())
                }

                combine(shiftAndAppointmentFlow, manualShiftFlow, holidayFlow) { a, b, c ->
                    (a + b + c).groupBy({ it.first }, { it.second })
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
