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

    @Query(
        "DELETE FROM calendar_entries WHERE rotationId = :rotationId " +
            "AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay"
    )
    suspend fun deleteForRotationInRange(rotationId: Long, fromEpochDay: Long, toEpochDay: Long)

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

    /** Alle tatsächlich besetzten Schicht-Tage (keine freien Tage) im Sync-Fenster, für den Nextcloud-Export. */
    @Query(
        "SELECT * FROM calendar_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "AND shiftId IS NOT NULL"
    )
    suspend fun getRangeWithShiftOnce(fromEpochDay: Long, toEpochDay: Long): List<CalendarEntryEntity>
}

@Dao
interface AppointmentDao {
    @Query(
        "SELECT * FROM appointments WHERE startEpochDay <= :toEpochDay AND endEpochDay >= :fromEpochDay " +
            "ORDER BY startEpochMillis"
    )
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Long): AppointmentEntity?

    @Query("SELECT * FROM appointments WHERE dirty = 1")
    suspend fun getDirty(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE reminderMinutesBefore IS NOT NULL")
    suspend fun getAllWithReminder(): List<AppointmentEntity>

    @Insert
    suspend fun insert(appointment: AppointmentEntity): Long

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Delete
    suspend fun delete(appointment: AppointmentEntity)
}

@Dao
interface ManualShiftEntryDao {
    @Query("SELECT * FROM manual_shift_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<ManualShiftEntryEntity>>

    @Insert
    suspend fun insert(entry: ManualShiftEntryEntity): Long

    @Delete
    suspend fun delete(entry: ManualShiftEntryEntity)
}

@Dao
interface HolidaySettingsDao {
    @Query("SELECT * FROM holiday_settings LIMIT 1")
    fun observe(): Flow<HolidaySettingsEntity?>

    @Query("SELECT * FROM holiday_settings LIMIT 1")
    suspend fun getOnce(): HolidaySettingsEntity?

    @Insert
    suspend fun insert(entity: HolidaySettingsEntity): Long

    @Update
    suspend fun update(entity: HolidaySettingsEntity)
}

@Dao
interface HolidayDao {
    @Insert
    suspend fun insertAll(holidays: List<HolidayEntity>)

    @Query(
        "SELECT * FROM holidays WHERE subdivisionCode = :subdivisionCode " +
            "AND startEpochDay <= :toEpochDay AND endEpochDay >= :fromEpochDay"
    )
    fun observeRange(subdivisionCode: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<HolidayEntity>>

    @Query(
        "SELECT COUNT(*) FROM holidays WHERE subdivisionCode = :subdivisionCode AND type = :type " +
            "AND startEpochDay <= :yearEndEpochDay AND endEpochDay >= :yearStartEpochDay"
    )
    suspend fun countForYear(subdivisionCode: String, type: String, yearStartEpochDay: Long, yearEndEpochDay: Long): Int
}

@Dao
interface CalDavAccountDao {
    @Query("SELECT * FROM caldav_accounts LIMIT 1")
    fun observeAccount(): Flow<CalDavAccountEntity?>

    @Query("SELECT * FROM caldav_accounts LIMIT 1")
    suspend fun getAccountOnce(): CalDavAccountEntity?

    @Query("SELECT * FROM caldav_accounts WHERE id = :id")
    suspend fun getById(id: Long): CalDavAccountEntity?

    @Insert
    suspend fun insert(account: CalDavAccountEntity): Long

    @Update
    suspend fun update(account: CalDavAccountEntity)

    @Delete
    suspend fun delete(account: CalDavAccountEntity)
}

@Dao
interface SyncedEventDao {
    @Query("SELECT * FROM synced_events WHERE accountId = :accountId AND localType = :localType")
    suspend fun getForAccountAndType(accountId: Long, localType: String): List<SyncedEventEntity>

    @Query("SELECT * FROM synced_events WHERE accountId = :accountId AND localType = :localType AND localKey = :localKey LIMIT 1")
    suspend fun getByLocalKey(accountId: Long, localType: String, localKey: String): SyncedEventEntity?

    @Insert
    suspend fun insert(entry: SyncedEventEntity): Long

    @Update
    suspend fun update(entry: SyncedEventEntity)

    @Delete
    suspend fun delete(entry: SyncedEventEntity)

    @Query("DELETE FROM synced_events WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
}

@Dao
interface IoBrokerAccountDao {
    @Query("SELECT * FROM iobroker_accounts LIMIT 1")
    fun observeAccount(): Flow<IoBrokerAccountEntity?>

    @Query("SELECT * FROM iobroker_accounts LIMIT 1")
    suspend fun getAccountOnce(): IoBrokerAccountEntity?

    @Insert
    suspend fun insert(account: IoBrokerAccountEntity): Long

    @Update
    suspend fun update(account: IoBrokerAccountEntity)

    @Delete
    suspend fun delete(account: IoBrokerAccountEntity)
}
