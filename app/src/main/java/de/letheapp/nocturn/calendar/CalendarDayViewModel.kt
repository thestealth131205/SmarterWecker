package de.letheapp.nocturn.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.letheapp.nocturn.contacts.ContactBirthday
import de.letheapp.nocturn.contacts.ContactBirthdayRepository
import de.letheapp.nocturn.data.db.AppointmentAttachmentEntity
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.db.HolidayEntity
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.db.ImportedEventRow
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.ReminderAgendaRow
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

/**
 * Woher ein [AllDayAgendaItem]/[TimedAgendaItem] stammt — bestimmt sowohl die Reihenfolge der
 * Abschnitte in der Tagesansicht (Termine → Erinnerungen → Aufgaben → Schichten) als auch, ob
 * eine Kachel beim Aufklappen bearbeitbar/löschbar ist (Feiertage/Ferien/importierte ICS-Termine
 * sind reine Anzeigedaten, siehe [CalendarDayScreen]).
 */
enum class AgendaItemKind { APPOINTMENT, SHIFT, HOLIDAY, IMPORTED }

/** Welche der beiden Tageseintrag-Tabellen hinter einem Schicht-Eintrag steckt (für den Papierkorb). */
sealed interface ShiftOccurrenceRef {
    data class Rotation(val entryId: Long) : ShiftOccurrenceRef
    data class Manual(val entryId: Long) : ShiftOccurrenceRef
}

/** Ein Eintrag in der Tagesansicht — entweder ganztägig (Feiertag/Ferien, ganztägiger Termin) oder zeitlich verortet. */
sealed interface DayAgendaItem {
    val title: String
    val subtitle: String?
    val colorArgb: Int
    /** Stabiler Schlüssel für Aufklapp-Zustand und `LazyColumn`-Keys, siehe [CalendarDayScreen]. */
    val agendaKey: String
}

data class AllDayAgendaItem(
    override val title: String,
    override val subtitle: String?,
    override val colorArgb: Int,
    override val agendaKey: String,
    val kind: AgendaItemKind = AgendaItemKind.APPOINTMENT,
    val appointmentId: Long? = null,
    val highPriority: Boolean = false,
    /** Anhänge (Dateiname zu content://-URI) — nur bei Terminen befüllt, siehe [AppointmentAttachmentEntity]. */
    val attachments: List<AttachmentRef> = emptyList()
) : DayAgendaItem

data class TimedAgendaItem(
    override val title: String,
    override val subtitle: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    override val colorArgb: Int,
    override val agendaKey: String,
    val kind: AgendaItemKind = AgendaItemKind.APPOINTMENT,
    val appointmentId: Long? = null,
    val highPriority: Boolean = false,
    /** Nur bei [AgendaItemKind.SHIFT] befüllt — für "bearbeiten → Schicht-Detailansicht". */
    val jobId: Long? = null,
    val shiftId: Long? = null,
    val occurrenceRef: ShiftOccurrenceRef? = null,
    val attachments: List<AttachmentRef> = emptyList()
) : DayAgendaItem

data class AttachmentRef(val uri: String, val fileName: String)

/** Geburtstag eines Geräte-Kontakts — ganztägig, zusätzlich mit optionalem Kontaktbild. */
data class BirthdayAgendaItem(
    override val title: String,
    override val subtitle: String?,
    val photoUri: String?,
    override val colorArgb: Int,
    override val agendaKey: String
) : DayAgendaItem

/** Eine Aufgabe (Task) — abhakbar statt zeitlich verortet, siehe [CalendarDayViewModel.toggleTaskCompleted]. */
data class TaskAgendaItem(
    val taskId: Long,
    override val title: String,
    override val subtitle: String?,
    val completed: Boolean,
    override val colorArgb: Int,
    override val agendaKey: String = "task:$taskId"
) : DayAgendaItem

/**
 * Eine fällige Erinnerung eines Termins ([de.letheapp.nocturn.data.db.AppointmentReminderEntity]),
 * deren Auslöse-Zeitpunkt auf diesen Tag fällt — unabhängig davon, an welchem Tag der Termin
 * selbst liegt. Bearbeiten öffnet denselben Termin-Screen wie bei einem Termin selbst, da
 * Erinnerungen dort verwaltet werden.
 */
data class ReminderAgendaItem(
    val reminderId: Long,
    val appointmentId: Long,
    override val title: String,
    override val subtitle: String?,
    val triggerMinuteOfDay: Int,
    override val colorArgb: Int,
    override val agendaKey: String = "reminder:$reminderId"
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
    private val icsImportRepository: IcsImportRepository,
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
                shiftToItem(shift, jobsById[entry.jobId]?.name.orEmpty(), entry.jobId, ShiftOccurrenceRef.Rotation(entry.id))
            }
            val fromManual = manual.mapNotNull { entry ->
                val shift = shiftsById[entry.shiftId] ?: return@mapNotNull null
                shiftToItem(shift, jobsById[entry.jobId]?.name.orEmpty(), entry.jobId, ShiftOccurrenceRef.Manual(entry.id))
            }
            fromRotation + fromManual
        }

        val appointmentFlow = combine(
            calendarRepository.observeAppointments(epochDay, epochDay),
            calendarRepository.observeAllAppointmentAttachments()
        ) { appointments, attachments ->
            val attachmentsByAppointment = attachments.groupBy { it.appointmentId }
            appointments.map { appointment ->
                appointmentToItem(appointment, attachmentsByAppointment[appointment.id].orEmpty())
            }
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

        val dayStartMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEndMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val importedFlow = icsImportRepository.observeVisibleEvents(dayStartMillis, dayEndMillis).map { events ->
            events.map(::importedEventToItem)
        }

        val reminderFlow = calendarRepository.observeRemindersTriggeringInRange(dayStartMillis, dayEndMillis).map { rows ->
            rows.map(::reminderToItem)
        }

        combine(shiftFlow, appointmentFlow, holidayFlow, birthdayFlow, taskFlow) { a, b, c, d, e -> a + b + c + d + e }
            .combine(importedFlow) { rest, imported -> rest + imported }
            .combine(reminderFlow) { rest, reminders -> rest + reminders }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Hakt eine Aufgabe ab oder macht das rückgängig (Checkbox in [CalendarDayScreen]). */
    fun toggleTaskCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch { calendarRepository.setTaskCompleted(taskId, completed) }
    }

    /** Papierkorb auf einer aufgeklappten Termin-Kachel. */
    fun deleteAppointment(appointmentId: Long) {
        viewModelScope.launch {
            calendarRepository.getAppointment(appointmentId)?.let { calendarRepository.deleteAppointment(it) }
        }
    }

    /** Papierkorb auf einer aufgeklappten Aufgaben-Kachel. */
    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            calendarRepository.getTask(taskId)?.let { calendarRepository.deleteTask(it) }
        }
    }

    /** Papierkorb auf einer aufgeklappten Erinnerungs-Kachel — löscht nur diese eine Erinnerung, nicht den Termin. */
    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch { calendarRepository.removeAppointmentReminderById(reminderId) }
    }

    /**
     * Papierkorb auf einer aufgeklappten Schicht-Kachel — löscht nur diesen einen Tageseintrag,
     * nicht die wiederverwendbare Schicht-Vorlage selbst.
     */
    fun deleteShiftOccurrence(ref: ShiftOccurrenceRef) {
        viewModelScope.launch {
            when (ref) {
                is ShiftOccurrenceRef.Rotation -> calendarRepository.deleteCalendarEntryById(ref.entryId)
                is ShiftOccurrenceRef.Manual -> calendarRepository.deleteManualShiftEntryById(ref.entryId)
            }
        }
    }

    private fun shiftToItem(shift: ShiftEntity, jobName: String, jobId: Long, ref: ShiftOccurrenceRef): DayAgendaItem {
        val start = shift.startHour * 60 + shift.startMinute
        val refKey = when (ref) {
            is ShiftOccurrenceRef.Rotation -> "rotation:${ref.entryId}"
            is ShiftOccurrenceRef.Manual -> "manual:${ref.entryId}"
        }
        return TimedAgendaItem(
            title = shift.name,
            subtitle = jobName.ifBlank { null },
            startMinuteOfDay = start,
            endMinuteOfDay = start + shift.durationMinutes,
            colorArgb = shift.colorArgb,
            agendaKey = "shift:$refKey",
            kind = AgendaItemKind.SHIFT,
            jobId = jobId,
            shiftId = shift.id,
            occurrenceRef = ref
        )
    }

    private fun appointmentToItem(appointment: AppointmentEntity, attachments: List<AppointmentAttachmentEntity>): DayAgendaItem {
        val attachmentRefs = attachments.map { AttachmentRef(it.uri, it.fileName) }
        val highPriority = appointment.priority == AppointmentPriority.HIGH.name
        if (appointment.allDay) {
            return AllDayAgendaItem(
                title = appointment.title,
                subtitle = appointment.location.ifBlank { null },
                colorArgb = appointment.colorArgb,
                agendaKey = "appt:${appointment.id}",
                kind = AgendaItemKind.APPOINTMENT,
                appointmentId = appointment.id,
                highPriority = highPriority,
                attachments = attachmentRefs
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
            colorArgb = appointment.colorArgb,
            agendaKey = "appt:${appointment.id}",
            kind = AgendaItemKind.APPOINTMENT,
            appointmentId = appointment.id,
            highPriority = highPriority,
            attachments = attachmentRefs
        )
    }

    private fun birthdayToItem(birthday: ContactBirthday): DayAgendaItem {
        val age = birthday.year?.let { date.year - it }?.takeIf { it > 0 }
        return BirthdayAgendaItem(
            title = birthday.displayName,
            subtitle = if (age != null) "Geburtstag · wird $age" else "Geburtstag",
            photoUri = birthday.photoUri,
            colorArgb = BIRTHDAY_COLOR_ARGB,
            agendaKey = "birthday:${birthday.contactId}"
        )
    }

    private fun taskToItem(task: TaskEntity): DayAgendaItem = TaskAgendaItem(
        taskId = task.id,
        title = task.title,
        subtitle = task.notes.ifBlank { null },
        completed = task.completed,
        colorArgb = task.colorArgb
    )

    private fun reminderToItem(row: ReminderAgendaRow): DayAgendaItem {
        val zone = ZoneId.systemDefault()
        val triggerTime = Instant.ofEpochMilli(row.triggerEpochMillis).atZone(zone).toLocalTime()
        return ReminderAgendaItem(
            reminderId = row.reminderId,
            appointmentId = row.appointmentId,
            title = row.appointmentTitle,
            subtitle = describeReminderOffset(row.minutesBefore),
            triggerMinuteOfDay = triggerTime.hour * 60 + triggerTime.minute,
            colorArgb = row.appointmentColorArgb
        )
    }

    private fun holidayToItem(holiday: HolidayEntity): DayAgendaItem {
        val isSchool = holiday.type == HolidayType.SCHOOL.name
        return AllDayAgendaItem(
            title = holiday.name,
            subtitle = if (isSchool) "Ferien" else "Feiertag",
            colorArgb = if (isSchool) SCHOOL_HOLIDAY_COLOR_ARGB else PUBLIC_HOLIDAY_COLOR_ARGB,
            agendaKey = "holiday:${holiday.id}",
            kind = AgendaItemKind.HOLIDAY
        )
    }

    private fun importedEventToItem(event: ImportedEventRow): DayAgendaItem {
        val key = "imported:${event.title}:${event.startEpochMillis}:${event.endEpochMillis}"
        if (event.allDay) {
            return AllDayAgendaItem(
                title = event.title,
                subtitle = event.location.ifBlank { null },
                colorArgb = event.colorArgb,
                agendaKey = key,
                kind = AgendaItemKind.IMPORTED
            )
        }
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).toLocalTime()
        return TimedAgendaItem(
            title = event.title,
            subtitle = event.location.ifBlank { null },
            startMinuteOfDay = start.hour * 60 + start.minute,
            endMinuteOfDay = end.hour * 60 + end.minute,
            colorArgb = event.colorArgb,
            agendaKey = key,
            kind = AgendaItemKind.IMPORTED
        )
    }
}

private fun describeReminderOffset(minutesBefore: Int): String = when {
    minutesBefore % (24 * 60) == 0 && minutesBefore >= 24 * 60 -> {
        val days = minutesBefore / (24 * 60)
        if (days == 1) "1 Tag vorher" else "$days Tage vorher"
    }
    minutesBefore % 60 == 0 && minutesBefore >= 60 -> {
        val hours = minutesBefore / 60
        if (hours == 1) "1 Stunde vorher" else "$hours Stunden vorher"
    }
    else -> "$minutesBefore Min. vorher"
}
