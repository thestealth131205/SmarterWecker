package de.letheapp.nocturn.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.contacts.ContactBirthday
import de.letheapp.nocturn.contacts.ContactBirthdayRepository
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.HolidayEntity
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.db.TaskEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Ein Eintrag in der Tagesansicht — entweder ganztägig (Feiertag/Ferien, ganztägiger Termin) oder zeitlich verortet. */
sealed interface DayAgendaItem {
    val title: String
    val subtitle: String?
    val colorArgb: Int
}

data class AllDayAgendaItem(
    override val title: String,
    override val subtitle: String?,
    override val colorArgb: Int
) : DayAgendaItem

data class TimedAgendaItem(
    override val title: String,
    override val subtitle: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    override val colorArgb: Int
) : DayAgendaItem

/** Geburtstag eines Geräte-Kontakts — ganztägig, zusätzlich mit optionalem Kontaktbild. */
data class BirthdayAgendaItem(
    override val title: String,
    override val subtitle: String?,
    val photoUri: String?,
    override val colorArgb: Int
) : DayAgendaItem

/** Eine Aufgabe (Task) — abhakbar statt zeitlich verortet, siehe [CalendarDayViewModel.toggleTaskCompleted]. */
data class TaskAgendaItem(
    val taskId: Long,
    override val title: String,
    override val subtitle: String?,
    val completed: Boolean,
    override val colorArgb: Int
) : DayAgendaItem

/**
 * Tagesansicht für einen einzelnen Kalendertag (siehe CLAUDE.md Kernfunktion 6): zeigt alle
 * Schichten (aus Rotation oder manuell eingetragen), Termine und Feiertage/Ferien dieses Tages
 * an, ganztägige Einträge zuerst, danach zeitlich sortiert — an die Tagesansicht der
 * Google-Kalender-App angelehnt, aber auf NocTurns Eintragstypen reduziert.
 */
@HiltViewModel
class CalendarDayViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val contactBirthdayRepository: ContactBirthdayRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val epochDay: Long = checkNotNull(savedStateHandle.get<String>("epochDay")).toLong()
    val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    /** Für den "+"-Dialog: bestehende Jobs zur Auswahl, wenn eine Rotation/ein Stundenplan
     *  direkt aus der Tagesansicht heraus angelegt werden soll. */
    val jobs: StateFlow<List<JobEntity>> = calendarRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createJob(name: String, mode: JobMode, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = calendarRepository.addJob(name.trim(), mode)
            onCreated(id)
        }
    }

    private val holidaySettings = holidayRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<DayAgendaItem>> = holidaySettings.flatMapLatest { settings ->
        val shiftFlow = combine(
            calendarRepository.observeCalendarEntries(epochDay, epochDay),
            calendarRepository.observeManualShifts(epochDay, epochDay),
            calendarRepository.observeAllShifts(),
            calendarRepository.observeJobs()
        ) { entries, manual, shifts, jobs ->
            val shiftsById = shifts.associateBy { it.id }
            val jobsById = jobs.associateBy { it.id }

            val fromRotation = entries.mapNotNull { entry ->
                val shiftId = entry.shiftId ?: return@mapNotNull null
                val shift = shiftsById[shiftId] ?: return@mapNotNull null
                shiftToItem(shift, jobsById[entry.jobId]?.name.orEmpty())
            }
            val fromManual = manual.mapNotNull { entry ->
                val shift = shiftsById[entry.shiftId] ?: return@mapNotNull null
                shiftToItem(shift, jobsById[entry.jobId]?.name.orEmpty())
            }
            fromRotation + fromManual
        }

        val appointmentFlow = calendarRepository.observeAppointments(epochDay, epochDay).map { appointments ->
            appointments.map(::appointmentToItem)
        }

        val holidayFlow = if (settings != null && (settings.showPublicHolidays || settings.showSchoolHolidays)) {
            holidayRepository.observeHolidays(settings.subdivisionCode, epochDay, epochDay).map { holidays ->
                holidays
                    .filter {
                        (it.type == HolidayType.PUBLIC.name && settings.showPublicHolidays) ||
                            (it.type == HolidayType.SCHOOL.name && settings.showSchoolHolidays)
                    }
                    .map(::holidayToItem)
            }
        } else {
            flowOf(emptyList())
        }

        val birthdayFlow = flow {
            emit(contactBirthdayRepository.birthdaysInRange(epochDay, epochDay))
        }.flowOn(Dispatchers.IO).map { birthdays ->
            birthdays.map { (_, birthday) -> birthdayToItem(birthday) }
        }

        val taskFlow = calendarRepository.observeTasks(epochDay, epochDay).map { tasks ->
            tasks.map(::taskToItem)
        }

        combine(shiftFlow, appointmentFlow, holidayFlow, birthdayFlow, taskFlow) { a, b, c, d, e -> a + b + c + d + e }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Hakt eine Aufgabe ab oder macht das rückgängig (Checkbox in [CalendarDayScreen]). */
    fun toggleTaskCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch { calendarRepository.setTaskCompleted(taskId, completed) }
    }

    private fun shiftToItem(shift: ShiftEntity, jobName: String): DayAgendaItem {
        val start = shift.startHour * 60 + shift.startMinute
        return TimedAgendaItem(
            title = shift.name,
            subtitle = jobName.ifBlank { null },
            startMinuteOfDay = start,
            endMinuteOfDay = start + shift.durationMinutes,
            colorArgb = shift.colorArgb
        )
    }

    private fun appointmentToItem(appointment: AppointmentEntity): DayAgendaItem {
        if (appointment.allDay) {
            return AllDayAgendaItem(
                title = appointment.title,
                subtitle = appointment.location.ifBlank { null },
                colorArgb = appointment.colorArgb
            )
        }
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(appointment.startEpochMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(appointment.endEpochMillis).atZone(zone).toLocalTime()
        return TimedAgendaItem(
            title = appointment.title,
            subtitle = appointment.location.ifBlank { null },
            startMinuteOfDay = start.hour * 60 + start.minute,
            endMinuteOfDay = end.hour * 60 + end.minute,
            colorArgb = appointment.colorArgb
        )
    }

    private fun birthdayToItem(birthday: ContactBirthday): DayAgendaItem {
        val age = birthday.year?.let { date.year - it }?.takeIf { it > 0 }
        return BirthdayAgendaItem(
            title = birthday.displayName,
            subtitle = if (age != null) "Geburtstag · wird $age" else "Geburtstag",
            photoUri = birthday.photoUri,
            colorArgb = BIRTHDAY_COLOR_ARGB
        )
    }

    private fun taskToItem(task: TaskEntity): DayAgendaItem = TaskAgendaItem(
        taskId = task.id,
        title = task.title,
        subtitle = task.notes.ifBlank { null },
        completed = task.completed,
        colorArgb = task.colorArgb
    )

    private fun holidayToItem(holiday: HolidayEntity): DayAgendaItem {
        val isSchool = holiday.type == HolidayType.SCHOOL.name
        return AllDayAgendaItem(
            title = holiday.name,
            subtitle = if (isSchool) "Ferien" else "Feiertag",
            colorArgb = if (isSchool) SCHOOL_HOLIDAY_COLOR_ARGB else PUBLIC_HOLIDAY_COLOR_ARGB
        )
    }
}
