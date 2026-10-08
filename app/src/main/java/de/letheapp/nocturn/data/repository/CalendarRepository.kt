package de.letheapp.nocturn.data.repository

import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.CalendarEntryEntity
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.JobMode
import de.letheapp.nocturn.data.db.LessonDao
import de.letheapp.nocturn.data.db.LessonEntity
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.RotationDayEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.ShiftEntity
import kotlinx.coroutines.flow.Flow
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
    private val appointmentDao: AppointmentDao
) {
    companion object {
        const val FORECAST_YEARS = 5

        /** Standardfarbe für frei angelegte Termine (Orange), unabhängig vom Theme. */
        const val DEFAULT_APPOINTMENT_COLOR_ARGB = 0xFFFF7043.toInt()
    }

    // Jobs

    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(jobId: Long): Flow<JobEntity?> = jobDao.observeById(jobId)

    suspend fun getJob(jobId: Long): JobEntity? = jobDao.getById(jobId)

    suspend fun addJob(name: String): Long = jobDao.insert(JobEntity(name = name))

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
        colorArgb: Int = DEFAULT_APPOINTMENT_COLOR_ARGB
    ): Long {
        val zone = java.time.ZoneId.systemDefault()
        val startDay = java.time.Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        val endDay = java.time.Instant.ofEpochMilli(endEpochMillis).atZone(zone).toLocalDate().toEpochDay()
        return appointmentDao.insert(
            AppointmentEntity(
                title = title,
                location = location,
                description = description,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                startEpochDay = startDay,
                endEpochDay = maxOf(startDay, endDay),
                allDay = allDay,
                colorArgb = colorArgb
            )
        )
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity) = appointmentDao.delete(appointment)
}
