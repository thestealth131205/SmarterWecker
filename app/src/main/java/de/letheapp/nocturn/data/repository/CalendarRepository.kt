package de.letheapp.nocturn.data.repository

import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.CalendarEntryEntity
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.JobEntity
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
    private val calendarEntryDao: CalendarEntryDao
) {
    companion object {
        const val FORECAST_YEARS = 5
    }

    // Jobs

    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    suspend fun getJob(jobId: Long): JobEntity? = jobDao.getById(jobId)

    suspend fun addJob(name: String): Long = jobDao.insert(JobEntity(name = name))

    suspend fun deleteJob(job: JobEntity) = jobDao.delete(job)

    // Schichten

    fun observeShiftsForJob(jobId: Long): Flow<List<ShiftEntity>> = shiftDao.observeForJob(jobId)

    suspend fun saveShift(shift: ShiftEntity): Long =
        if (shift.id == 0L) shiftDao.insert(shift) else shiftDao.update(shift).let { shift.id }

    suspend fun deleteShift(shift: ShiftEntity) = shiftDao.delete(shift)

    // Rotationen

    fun observeRotationsForJob(jobId: Long): Flow<List<RotationEntity>> = rotationDao.observeForJob(jobId)

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
}
