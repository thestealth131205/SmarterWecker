package de.letheapp.nocturn.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY name")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: Long): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): JobEntity?

    @Insert
    suspend fun insert(job: JobEntity): Long

    @Update
    suspend fun update(job: JobEntity)

    @Delete
    suspend fun delete(job: JobEntity)
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE jobId = :jobId ORDER BY weekday, startHour, startMinute")
    fun observeForJob(jobId: Long): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE jobId = :jobId AND weekday = :weekday ORDER BY startHour, startMinute")
    suspend fun getForJobAndWeekday(jobId: Long, weekday: Int): List<LessonEntity>

    @Query(
        "SELECT * FROM lessons WHERE jobId = :jobId AND weekday = :weekday ORDER BY startHour, startMinute LIMIT 1"
    )
    suspend fun getFirstForJobAndWeekday(jobId: Long, weekday: Int): LessonEntity?

    @Insert
    suspend fun insert(lesson: LessonEntity): Long

    @Update
    suspend fun update(lesson: LessonEntity)

    @Delete
    suspend fun delete(lesson: LessonEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE jobId = :jobId ORDER BY startHour, startMinute")
    fun observeForJob(jobId: Long): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    @Insert
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Delete
    suspend fun delete(shift: ShiftEntity)
}

@Dao
interface RotationDao {
    @Query("SELECT * FROM rotations WHERE jobId = :jobId ORDER BY name")
    fun observeForJob(jobId: Long): Flow<List<RotationEntity>>

    @Query("SELECT * FROM rotations ORDER BY name")
    fun observeAll(): Flow<List<RotationEntity>>

    @Query("SELECT * FROM rotations WHERE id = :id")
    suspend fun getById(id: Long): RotationEntity?

    @Insert
    suspend fun insert(rotation: RotationEntity): Long

    @Update
    suspend fun update(rotation: RotationEntity)

    @Delete
    suspend fun delete(rotation: RotationEntity)

    @Query("SELECT * FROM rotation_days WHERE rotationId = :rotationId ORDER BY dayIndex")
    fun observeDays(rotationId: Long): Flow<List<RotationDayEntity>>

    @Query("SELECT * FROM rotation_days WHERE rotationId = :rotationId ORDER BY dayIndex")
    suspend fun getDaysOnce(rotationId: Long): List<RotationDayEntity>

    @Query("DELETE FROM rotation_days WHERE rotationId = :rotationId")
    suspend fun deleteDays(rotationId: Long)

    @Insert
    suspend fun insertDays(days: List<RotationDayEntity>)
}

@Dao
interface CalendarEntryDao {
    @Query("DELETE FROM calendar_entries WHERE rotationId = :rotationId")
    suspend fun deleteForRotation(rotationId: Long)

    @Insert
    suspend fun insertAll(entries: List<CalendarEntryEntity>)

    @Query("SELECT * FROM calendar_entries WHERE dateEpochDay = :epochDay LIMIT 1")
    suspend fun getForDate(epochDay: Long): CalendarEntryEntity?

    @Query("SELECT * FROM calendar_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<CalendarEntryEntity>>

    @Query(
        "SELECT * FROM calendar_entries WHERE rotationId = :rotationId AND dateEpochDay >= :fromEpochDay " +
            "AND shiftId IS NOT NULL ORDER BY dateEpochDay LIMIT 1"
    )
    suspend fun getNextWithShift(rotationId: Long, fromEpochDay: Long): CalendarEntryEntity?
}
