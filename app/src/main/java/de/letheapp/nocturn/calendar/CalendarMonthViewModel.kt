package de.letheapp.nocturn.calendar

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.contacts.ContactBirthdayRepository
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
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

/** Geburtstag eines Geräte-Kontakts (CLAUDE.md Kernfunktion 6), jährlich wiederkehrend. */
data class BirthdayDayEntry(
    val contactName: String,
    val photoUri: String?,
    val age: Int?,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = "🎂 $contactName"
}

/** Eine Aufgabe (Task) — abhakbarer To-Do-Eintrag statt eines zeitgebundenen Termins. */
data class TaskDayEntry(
    val taskId: Long,
    val title: String,
    val completed: Boolean,
    override val colorArgb: Int
) : CalendarDayEntry {
    override val label: String get() = if (completed) "✓ $title" else title
}

const val PUBLIC_HOLIDAY_COLOR_ARGB = 0xFFE53935.toInt()
const val SCHOOL_HOLIDAY_COLOR_ARGB = 0xFF43A047.toInt()
const val BIRTHDAY_COLOR_ARGB = 0xFF8E24AA.toInt()

/** Eine Rotation zur Anzeige in der Seitenleiste, mit dem Namen ihres Jobs zur Unterscheidung. */
data class RotationOption(val id: Long, val name: String, val jobName: String)

@HiltViewModel
class CalendarMonthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val contactBirthdayRepository: ContactBirthdayRepository,
    private val shiftWakeupAlarmSyncer: ShiftWakeupAlarmSyncer
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
                val fromEpochDay = monthGridStart(month).toEpochDay()
                val toEpochDay = fromEpochDay + 7 * monthGridWeeks(month) - 1

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

                val birthdayFlow = flow {
                    emit(contactBirthdayRepository.birthdaysInRange(fromEpochDay, toEpochDay))
                }.flowOn(Dispatchers.IO).map { birthdays ->
                    birthdays.map { (date, birthday) ->
                        date to (BirthdayDayEntry(
                            contactName = birthday.displayName,
                            photoUri = birthday.photoUri,
                            age = birthday.year?.let { date.year - it }?.takeIf { it > 0 },
                            colorArgb = BIRTHDAY_COLOR_ARGB
                        ) as CalendarDayEntry)
                    }
                }

                val taskFlow = calendarRepository.observeTasks(fromEpochDay, toEpochDay).map { tasks ->
                    tasks.map { task ->
                        LocalDate.ofEpochDay(task.dueEpochDay) to
                            (TaskDayEntry(
                                taskId = task.id,
                                title = task.title,
                                completed = task.completed,
                                colorArgb = task.colorArgb
                            ) as CalendarDayEntry)
                    }
                }

                combine(shiftAndAppointmentFlow, manualShiftFlow, holidayFlow, birthdayFlow, taskFlow) { a, b, c, d, e ->
                    (a + b + c + d + e).groupBy({ it.first }, { it.second })
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

    /** Für den "+"-Dialog: bestehende Jobs zur Auswahl, wenn eine Rotation/ein Stundenplan
     *  direkt aus dem Kalender heraus angelegt werden soll, ohne über die Kalender-Vorlagen
     *  navigieren zu müssen. */
    val jobs: StateFlow<List<JobEntity>> = calendarRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Legt einen neuen Job direkt aus dem "+"-Dialog an und liefert dessen ID an [onCreated],
     *  damit der Screen anschließend direkt zum passenden Editor (Rotation oder Stundenplan)
     *  weiternavigieren kann. */
    fun createJob(name: String, mode: JobMode, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = calendarRepository.addJob(name.trim(), mode)
            onCreated(id)
        }
    }

    /** Für die ausklappbare Seitenleiste: alle vorhandenen Rotationen jobübergreifend zur Auswahl. */
    val rotations: StateFlow<List<RotationOption>> = combine(
        calendarRepository.observeAllRotations(),
        calendarRepository.observeJobs()
    ) { rotations, jobs ->
        val jobsById = jobs.associateBy { it.id }
        rotations.map { rotation -> RotationOption(rotation.id, rotation.name, jobsById[rotation.jobId]?.name.orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Wendet eine über die Seitenleiste gewählte Rotation ab [startEpochDay] für [totalDays] Tage an. */
    fun applyRotation(rotationId: Long, startEpochDay: Long, totalDays: Int, phaseStartIndex: Int = 0) {
        viewModelScope.launch {
            calendarRepository.applyRotationRange(rotationId, startEpochDay, totalDays, phaseStartIndex)
            shiftWakeupAlarmSyncer.sync()
        }
    }

    /** Entfernt alle im Kalender eingetragenen Vorkommen dieser Rotation wieder (Vorlage bleibt
     *  erhalten, siehe [CalendarRepository.removeRotationEntries]) und zieht den automatischen
     *  "Nächste Schicht"-Wecker nach, damit er nicht auf eine inzwischen entfernte Schicht zeigt. */
    fun removeRotationEntries(rotationId: Long) {
        viewModelScope.launch {
            calendarRepository.removeRotationEntries(rotationId)
            shiftWakeupAlarmSyncer.sync()
        }
    }

    /** Siehe [CalendarRepository.rotationPhaseCandidates]. */
    suspend fun rotationPhaseCandidates(rotationId: Long, startEpochDay: Long): List<Int> =
        calendarRepository.rotationPhaseCandidates(rotationId, startEpochDay)

    /** Siehe [CalendarRepository.getRotationShiftOptions]. */
    suspend fun getRotationShiftOptions(rotationId: Long): List<ShiftEntity?> =
        calendarRepository.getRotationShiftOptions(rotationId)

    /** Siehe [CalendarRepository.resolveRotationPhase]. */
    suspend fun resolveRotationPhase(rotationId: Long, startEpochDay: Long, todayShiftId: Long?, nextFridayShiftId: Long?): Int =
        calendarRepository.resolveRotationPhase(rotationId, startEpochDay, todayShiftId, nextFridayShiftId)

    /** Siehe [CalendarRepository.candidatesMatchingTodayShift]. */
    suspend fun candidatesMatchingTodayShift(rotationId: Long, startEpochDay: Long, todayShiftId: Long?): List<Int> =
        calendarRepository.candidatesMatchingTodayShift(rotationId, startEpochDay, todayShiftId)

    /** Siehe [CalendarRepository.needsNextOccurrenceDateQuery]. */
    suspend fun needsNextOccurrenceDateQuery(rotationId: Long, startEpochDay: Long, todayShiftId: Long?): Boolean =
        calendarRepository.needsNextOccurrenceDateQuery(rotationId, startEpochDay, todayShiftId)

    /** Siehe [CalendarRepository.resolveRotationPhaseByNextOccurrence]. */
    suspend fun resolveRotationPhaseByNextOccurrence(rotationId: Long, startEpochDay: Long, todayShiftId: Long?, nextOccurrenceEpochDay: Long): Int =
        calendarRepository.resolveRotationPhaseByNextOccurrence(rotationId, startEpochDay, todayShiftId, nextOccurrenceEpochDay)

    private val _shareFileUri = MutableStateFlow<Uri?>(null)

    /** Einmaliger Wert für den "Teilen"-Button in der Topbar — eine erzeugte ICS-Datei, die der
     *  Screen dann über Androids Teilen-Menü an eine andere App übergibt (siehe
     *  [CalendarShareExporter] und CLAUDE.md "Kalender teilen": serverunabhängig, läuft direkt
     *  von App-Nutzer zu App-Nutzer über einen beliebigen Übertragungsweg). */
    val shareFileUri: StateFlow<Uri?> = _shareFileUri

    fun shareMonth() {
        val currentMonth = _month.value
        val currentEntries = entriesByDay.value
        viewModelScope.launch {
            val ics = CalendarShareExporter.buildIcs(currentMonth, currentEntries)
            val dir = File(context.cacheDir, "calendar_share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "NocTurn_Kalender_$currentMonth.ics")
            file.writeText(ics)
            _shareFileUri.value = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    fun consumeShareFile() {
        _shareFileUri.value = null
    }
}
