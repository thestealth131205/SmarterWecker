package de.letheapp.nocturn.data.repository

import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentPriority
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.CalendarEntryEntity
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.ManualShiftEntryEntity
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.RotationDayEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.ShiftEntity
import de.letheapp.nocturn.calendar.AppointmentReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Verwaltet die Kalender-Vorlagen (Jobs, Schichten, Rotationen) und schreibt eine
 * gespeicherte Rotation automatisch für [FORECAST_YEARS] Jahre im Voraus als
 * [CalendarEntryEntity]s fort, damit der Wecker jederzeit weiß, welche Schicht an
 * welchem Tag gilt, ohne dass der Nutzer manuell nachpflegen muss.
 */
class CalendarRepository @Inject constructor(
    private val jobDao: JobDao,
    private val shiftDao: ShiftDao,
    private val rotationDao: RotationDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val lessonDao: LessonDao,
    private val appointmentDao: AppointmentDao,
    private val manualShiftEntryDao: ManualShiftEntryDao,
    private val appointmentReminderScheduler: AppointmentReminderScheduler
) {
    companion object {
        const val FORECAST_YEARS = 5

        /** Standardfarbe für frei angelegte Termine (Orange), unabhängig vom Theme. */
        const val DEFAULT_APPOINTMENT_COLOR_ARGB = 0xFFFF7043.toInt()

        /** Zeitfenster, in dem das Kalender-Widget nach anstehenden Einträgen sucht. */
        private const val AGENDA_LOOKAHEAD_DAYS = 14L
    }

    // Jobs

    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(jobId: Long): Flow<JobEntity?> = jobDao.observeById(jobId)

    suspend fun getJob(jobId: Long): JobEntity? = jobDao.getById(jobId)

    suspend fun addJob(name: String, mode: JobMode = JobMode.SHIFT): Long =
        jobDao.insert(JobEntity(name = name, mode = mode.name))

    suspend fun setJobMode(jobId: Long, mode: JobMode) {
        val job = jobDao.getById(jobId) ?: return
        jobDao.update(job.copy(mode = mode.name))
    }

    suspend fun deleteJob(job: JobEntity) = jobDao.delete(job)

    // Stundenplan (Schüler/Student-Modus)

    fun observeLessonsForJob(jobId: Long): Flow<List<LessonEntity>> = lessonDao.observeForJob(jobId)

    suspend fun saveLesson(lesson: LessonEntity): Long =
        if (lesson.id == 0L) lessonDao.insert(lesson) else lessonDao.update(lesson).let { lesson.id }

    suspend fun deleteLesson(lesson: LessonEntity) = lessonDao.delete(lesson)

    /** Zeitlich erste Unterrichtsstunde an [weekday] (0=Mo..6=So) — einzig relevant für die Wecker-Ableitung. */
    suspend fun getFirstLessonForWeekday(jobId: Long, weekday: Int): LessonEntity? =
        lessonDao.getFirstForJobAndWeekday(jobId, weekday)

    // Schichten

    fun observeShiftsForJob(jobId: Long): Flow<List<ShiftEntity>> = shiftDao.observeForJob(jobId)

    fun observeAllShifts(): Flow<List<ShiftEntity>> = shiftDao.observeAll()

    suspend fun getShift(shiftId: Long): ShiftEntity? = shiftDao.getById(shiftId)

    suspend fun saveShift(shift: ShiftEntity): Long =
        if (shift.id == 0L) shiftDao.insert(shift) else shiftDao.update(shift).let { shift.id }

    suspend fun deleteShift(shift: ShiftEntity) = shiftDao.delete(shift)

    // Rotationen

    fun observeRotationsForJob(jobId: Long): Flow<List<RotationEntity>> = rotationDao.observeForJob(jobId)

    fun observeAllRotations(): Flow<List<RotationEntity>> = rotationDao.observeAll()

    fun observeRotationDays(rotationId: Long): Flow<List<RotationDayEntity>> = rotationDao.observeDays(rotationId)

    suspend fun getRotation(rotationId: Long): RotationEntity? = rotationDao.getById(rotationId)

    suspend fun getRotationDaysOnce(rotationId: Long): List<RotationDayEntity> = rotationDao.getDaysOnce(rotationId)

    suspend fun deleteRotation(rotation: RotationEntity) = rotationDao.delete(rotation)

    /**
     * Speichert eine Rotation inkl. ihres Tag-für-Tag-Musters (Zykluslänge = Anzahl der
     * übergebenen Tage) und schreibt den Kalender daraus sofort für mehrere Jahre neu fort.
     */
    suspend fun saveRotation(
        jobId: Long,
        rotationId: Long?,
        name: String,
        startDateEpochDay: Long,
        dayShiftIds: List<Long?>
    ) {
        val savedId = if (rotationId == null) {
            rotationDao.insert(RotationEntity(jobId = jobId, name = name, startDateEpochDay = startDateEpochDay))
        } else {
            rotationDao.update(RotationEntity(id = rotationId, jobId = jobId, name = name, startDateEpochDay = startDateEpochDay))
            rotationId
        }

        rotationDao.deleteDays(savedId)
        val days = dayShiftIds.mapIndexed { index, shiftId ->
            RotationDayEntity(rotationId = savedId, dayIndex = index, shiftId = shiftId)
        }
        rotationDao.insertDays(days)

        regenerateCalendarEntries(
            jobId = jobId,
            rotationId = savedId,
            startDateEpochDay = startDateEpochDay,
            days = days
        )
    }

    private suspend fun regenerateCalendarEntries(
        jobId: Long,
        rotationId: Long,
        startDateEpochDay: Long,
        days: List<RotationDayEntity>
    ) {
        calendarEntryDao.deleteForRotation(rotationId)
        val cycleLength = days.size
        if (cycleLength == 0) return

        val sortedDays = days.sortedBy { it.dayIndex }
        val totalDays = 365 * FORECAST_YEARS
        val entries = (0 until totalDays).map { offset ->
            CalendarEntryEntity(
                jobId = jobId,
                rotationId = rotationId,
                dateEpochDay = startDateEpochDay + offset,
                shiftId = sortedDays[offset % cycleLength].shiftId
            )
        }
        calendarEntryDao.insertAll(entries)
    }

    /**
     * Wendet eine bereits bestehende Rotation ab [startEpochDay] für [totalDays] Tage erneut an
     * (Seitenleiste in der Kalender-Monatsansicht: Rotation wählen → Starttag antippen → Dauer
     * im schwebenden Fenster festlegen). Ersetzt nur die Einträge dieser Rotation im gewählten
     * Zeitraum, lässt ihr beim Anlegen gespeichertes Standard-Fortschreibefenster unangetastet.
     */
    suspend fun applyRotationRange(rotationId: Long, startEpochDay: Long, totalDays: Int) {
        val rotation = rotationDao.getById(rotationId) ?: return
        val days = rotationDao.getDaysOnce(rotationId).sortedBy { it.dayIndex }
        val cycleLength = days.size
        if (cycleLength == 0 || totalDays <= 0) return

        val endEpochDay = startEpochDay + totalDays - 1
        calendarEntryDao.deleteForRotationInRange(rotationId, startEpochDay, endEpochDay)
        val entries = (0 until totalDays).map { offset ->
            CalendarEntryEntity(
                jobId = rotation.jobId,
                rotationId = rotationId,
                dateEpochDay = startEpochDay + offset,
                shiftId = days[offset % cycleLength].shiftId
            )
        }
        calendarEntryDao.insertAll(entries)
    }

    fun observeCalendarEntries(fromEpochDay: Long, toEpochDay: Long): Flow<List<CalendarEntryEntity>> =
        calendarEntryDao.observeRange(fromEpochDay, toEpochDay)

    suspend fun getCalendarEntryForDate(epochDay: Long): CalendarEntryEntity? = calendarEntryDao.getForDate(epochDay)

    /**
     * Nächster Kalendereintrag einer Rotation ab [fromEpochDay] (inklusive), an dem laut
     * Rotationsmuster tatsächlich eine Schicht ansteht (freie Tage werden übersprungen).
     * Grundlage für Alarme, die an eine Rotation statt an eine feste Uhrzeit gekoppelt sind.
     */
    suspend fun getNextCalendarEntryWithShift(rotationId: Long, fromEpochDay: Long): CalendarEntryEntity? =
        calendarEntryDao.getNextWithShift(rotationId, fromEpochDay)

    // Termine

    fun observeAppointments(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppointmentEntity>> =
        appointmentDao.observeRange(fromEpochDay, toEpochDay)

    /**
     * Legt einen Termin an, u. a. Ziel von [de.letheapp.nocturn.calendar.AddAppointmentActivity],
     * wenn sich NocTurn im System-"Zum Kalender hinzufügen"-Menü einer Fremd-App anbietet.
     */
    suspend fun addAppointment(
        title: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        priority: AppointmentPriority = AppointmentPriority.NORMAL,
        reminderMinutesBefore: Int? = null,
        colorArgb: Int = DEFAULT_APPOINTMENT_COLOR_ARGB
    ): Long {
        val zone = java.time.ZoneId.systemDefault()
        val startDay = java.time.Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val endDay = java.time.Instant.ofEpochMilli(endEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val appointment = AppointmentEntity(
            title = title,
            location = location,
            description = description,
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            startEpochDay = startDay,
            endEpochDay = maxOf(startDay, endDay),
            allDay = allDay,
            colorArgb = colorArgb,
            priority = priority.name,
            reminderMinutesBefore = reminderMinutesBefore
        )
        val id = appointmentDao.insert(appointment)
        appointmentReminderScheduler.schedule(appointment.copy(id = id))
        return id
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity) {
        appointmentReminderScheduler.cancel(appointment.id)
        appointmentDao.delete(appointment)
    }

    // Manuell eingetragene Einzel-Schichten (siehe CalendarMonthScreen-FAB, Option "Schicht")

    fun observeManualShifts(fromEpochDay: Long, toEpochDay: Long): Flow<List<ManualShiftEntryEntity>> =
        manualShiftEntryDao.observeRange(fromEpochDay, toEpochDay)

    suspend fun saveManualShiftEntry(jobId: Long, shiftId: Long, dateEpochDay: Long): Long =
        manualShiftEntryDao.insert(ManualShiftEntryEntity(jobId = jobId, shiftId = shiftId, dateEpochDay = dateEpochDay))

    suspend fun deleteManualShiftEntry(entry: ManualShiftEntryEntity) = manualShiftEntryDao.delete(entry)

    /**
     * Für das Kalender-Homescreen-Widget: die nächsten [maxItems] anstehenden Einträge
     * (Schichten aus Rotationen + freie Termine) ab heute, über die nächsten
     * [AGENDA_LOOKAHEAD_DAYS] Tage, chronologisch sortiert.
     */
    suspend fun getUpcomingAgenda(maxItems: Int): List<AgendaEntry> = buildAgenda().take(maxItems)

    /**
     * Für das Uhr-Homescreen-Widget: die nächsten [maxShown] anstehenden Einträge schmal
     * zur Anzeige unter der Uhrzeit, plus die Anzahl weiterer Einträge, die am selben Tag wie
     * der zuletzt gezeigte Eintrag noch anstehen (als "+N weitere" statt sie alle aufzulisten).
     */
    suspend fun getUpcomingAgendaForWidget(maxShown: Int): WidgetAgenda {
        val all = buildAgenda()
        val shown = all.take(maxShown)
        val extraCount = if (shown.size == maxShown) {
            val lastShownDay = shown.last().epochDay
            all.count { it.epochDay == lastShownDay } - shown.count { it.epochDay == lastShownDay }
        } else {
            0
        }
        return WidgetAgenda(entries = shown, extraCountForLastDay = extraCount)
    }

    private suspend fun buildAgenda(): List<AgendaEntry> {
        val today = LocalDate.now().toEpochDay()
        val toDay = today + AGENDA_LOOKAHEAD_DAYS

        val entries = calendarEntryDao.observeRange(today, toDay).first()
        val shiftsById = shiftDao.observeAll().first().associateBy { it.id }
        val appointments = appointmentDao.observeRange(today, toDay).first()
        val zone = ZoneId.systemDefault()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val shiftAgenda = entries.mapNotNull { entry ->
            val shiftId = entry.shiftId ?: return@mapNotNull null
            val shift = shiftsById[shiftId] ?: return@mapNotNull null
            AgendaEntry(
                epochDay = entry.dateEpochDay,
                label = shift.name,
                colorArgb = shift.colorArgb,
                timeLabel = "%02d:%02d".format(shift.startHour, shift.startMinute)
            )
        }

        val appointmentAgenda = appointments.map { appointment ->
            AgendaEntry(
                epochDay = appointment.startEpochDay,
                label = appointment.title,
                colorArgb = appointment.colorArgb,
                timeLabel = if (appointment.allDay) {
                    null
                } else {
                    Instant.ofEpochMilli(appointment.startEpochMillis).atZone(zone).toLocalTime().format(timeFormatter)
                }
            )
        }

        return (shiftAgenda + appointmentAgenda).sortedBy { it.epochDay }
    }
}

/** Ein Eintrag im Kalender-Widget: entweder eine Schicht an einem Rotationstag oder ein freier Termin. */
data class AgendaEntry(
    val epochDay: Long,
    val label: String,
    val colorArgb: Int,
    val timeLabel: String?
)

/** Für das Uhr-Widget (CLAUDE.md Kernfunktion 7): die schmal angezeigten nächsten Termine. */
data class WidgetAgenda(
    val entries: List<AgendaEntry>,
    val extraCountForLastDay: Int
)
