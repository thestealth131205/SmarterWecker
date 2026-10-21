package de.letheapp.nocturn.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    /** Der automatisch verwaltete "Nächste Schicht"-Alarm, siehe [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]. */
    @Query("SELECT * FROM alarms WHERE autoShiftWakeupEpochDay IS NOT NULL LIMIT 1")
    suspend fun getAutoShiftWakeupAlarm(): AlarmEntity?

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

    @Query("SELECT * FROM jobs ORDER BY name")
    suspend fun getAllOnce(): List<JobEntity>

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

    @Query("SELECT * FROM lessons WHERE jobId = :jobId ORDER BY weekday, startHour, startMinute")
    suspend fun getAllForJob(jobId: Long): List<LessonEntity>

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

    @Query("SELECT * FROM shifts")
    suspend fun getAllOnce(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    /** Schichten mit gesetzter automatischer Weck-Uhrzeit, siehe [ShiftEntity.latestWakeupHour]. */
    @Query("SELECT * FROM shifts WHERE latestWakeupHour IS NOT NULL")
    suspend fun getAllWithWakeupConfigured(): List<ShiftEntity>

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

    @Query("SELECT * FROM rotations ORDER BY name")
    suspend fun getAllOnce(): List<RotationEntity>

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
    @Query("SELECT * FROM calendar_entries WHERE id = :id")
    suspend fun getById(id: Long): CalendarEntryEntity?

    @Insert
    suspend fun insert(entry: CalendarEntryEntity): Long

    @Update
    suspend fun update(entry: CalendarEntryEntity)

    @Delete
    suspend fun delete(entry: CalendarEntryEntity)

    @Query("DELETE FROM calendar_entries WHERE rotationId = :rotationId")
    suspend fun deleteForRotation(rotationId: Long)

    /** Für den Bearbeiten-Modus der Monatsansicht (Tag(e) lang gedrückt halten → löschen). */
    @Query("DELETE FROM calendar_entries WHERE dateEpochDay = :epochDay")
    suspend fun deleteForDate(epochDay: Long)

    @Query(
        "DELETE FROM calendar_entries WHERE rotationId = :rotationId " +
            "AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay"
    )
    suspend fun deleteForRotationInRange(rotationId: Long, fromEpochDay: Long, toEpochDay: Long)

    @Insert
    suspend fun insertAll(entries: List<CalendarEntryEntity>)

    /**
     * Alle Einträge einer Rotation an einem Tag — an einem Tag können mehrere Zeilen
     * existieren, wenn an diesem Tag laut Muster mehrere Schichten gleichzeitig gelten
     * (siehe [de.letheapp.nocturn.calendar.RotationEditorViewModel]).
     */
    @Query("SELECT * FROM calendar_entries WHERE dateEpochDay = :epochDay AND rotationId = :rotationId")
    suspend fun getForDate(epochDay: Long, rotationId: Long): List<CalendarEntryEntity>

    @Query("SELECT * FROM calendar_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<CalendarEntryEntity>>

    /**
     * Nächste Einträge mit tatsächlicher Schicht ab [fromEpochDay] (chronologisch), begrenzt auf
     * eine großzügige Obergrenze statt eines einzelnen Treffers — an einem Tag können mehrere
     * Zeilen (mehrere gleichzeitige Schichten) existieren, der Aufrufer wählt daraus die mit dem
     * frühesten Schichtbeginn (siehe [de.letheapp.nocturn.data.repository.CalendarRepository.getNextCalendarEntryWithShift]).
     */
    @Query(
        "SELECT * FROM calendar_entries WHERE rotationId = :rotationId AND dateEpochDay >= :fromEpochDay " +
            "AND shiftId IS NOT NULL ORDER BY dateEpochDay LIMIT 60"
    )
    suspend fun getNextWithShift(rotationId: Long, fromEpochDay: Long): List<CalendarEntryEntity>

    /** Alle tatsächlich besetzten Schicht-Tage (keine freien Tage) im Sync-Fenster, für den Nextcloud-Export. */
    @Query(
        "SELECT * FROM calendar_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "AND shiftId IS NOT NULL"
    )
    suspend fun getRangeWithShiftOnce(fromEpochDay: Long, toEpochDay: Long): List<CalendarEntryEntity>

    /** Nächste Vorkommen einer der [shiftIds] ab [fromEpochDay], für [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]. */
    @Query(
        "SELECT * FROM calendar_entries WHERE shiftId IN (:shiftIds) AND dateEpochDay >= :fromEpochDay " +
            "ORDER BY dateEpochDay LIMIT 50"
    )
    suspend fun getUpcomingForShiftIds(shiftIds: List<Long>, fromEpochDay: Long): List<CalendarEntryEntity>
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

    /** Alle Termine, die einen Tag überdecken — für den Bearbeiten-Modus der Monatsansicht. */
    @Query("SELECT * FROM appointments WHERE startEpochDay <= :epochDay AND endEpochDay >= :epochDay")
    suspend fun getForDate(epochDay: Long): List<AppointmentEntity>

    @Insert
    suspend fun insert(appointment: AppointmentEntity): Long

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointment_attachments WHERE appointmentId = :appointmentId")
    fun observeAttachments(appointmentId: Long): Flow<List<AppointmentAttachmentEntity>>

    /** Für die Tagesansicht: Anhänge aller Termine auf einmal statt einer Einzelabfrage pro Termin. */
    @Query("SELECT * FROM appointment_attachments")
    fun observeAllAttachments(): Flow<List<AppointmentAttachmentEntity>>

    @Insert
    suspend fun insertAttachment(attachment: AppointmentAttachmentEntity): Long

    @Delete
    suspend fun deleteAttachment(attachment: AppointmentAttachmentEntity)

    @Query("SELECT * FROM appointment_reminders WHERE appointmentId = :appointmentId")
    fun observeReminders(appointmentId: Long): Flow<List<AppointmentReminderEntity>>

    @Query("SELECT * FROM appointment_reminders WHERE appointmentId = :appointmentId")
    suspend fun getRemindersForAppointment(appointmentId: Long): List<AppointmentReminderEntity>

    @Query("SELECT * FROM appointment_reminders")
    suspend fun getAllReminders(): List<AppointmentReminderEntity>

    @Query("SELECT * FROM appointment_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): AppointmentReminderEntity?

    @Insert
    suspend fun insertReminder(reminder: AppointmentReminderEntity): Long

    @Delete
    suspend fun deleteReminder(reminder: AppointmentReminderEntity)

    @Delete
    suspend fun delete(appointment: AppointmentEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE dueEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY completed, dueEpochMillis")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** Für den Bearbeiten-Modus der Monatsansicht (Tag(e) lang gedrückt halten → löschen). */
    @Query("DELETE FROM tasks WHERE dueEpochDay = :epochDay")
    suspend fun deleteForDate(epochDay: Long)

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface ManualShiftEntryDao {
    @Query("SELECT * FROM manual_shift_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<ManualShiftEntryEntity>>

    /** Für den Nextcloud-Sync (siehe [de.letheapp.nocturn.sync.NocturnScheduleSyncRepository]), einmalig statt reaktiv. */
    @Query("SELECT * FROM manual_shift_entries WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun getRangeOnce(fromEpochDay: Long, toEpochDay: Long): List<ManualShiftEntryEntity>

    @Query("SELECT * FROM manual_shift_entries WHERE id = :id")
    suspend fun getById(id: Long): ManualShiftEntryEntity?

    /** Nächste Vorkommen einer der [shiftIds] ab [fromEpochDay], für [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]. */
    @Query(
        "SELECT * FROM manual_shift_entries WHERE shiftId IN (:shiftIds) AND dateEpochDay >= :fromEpochDay " +
            "ORDER BY dateEpochDay LIMIT 50"
    )
    suspend fun getUpcomingForShiftIds(shiftIds: List<Long>, fromEpochDay: Long): List<ManualShiftEntryEntity>

    @Insert
    suspend fun insert(entry: ManualShiftEntryEntity): Long

    @Update
    suspend fun update(entry: ManualShiftEntryEntity)

    @Delete
    suspend fun delete(entry: ManualShiftEntryEntity)

    /** Für den Bearbeiten-Modus der Monatsansicht (Tag(e) lang gedrückt halten → löschen). */
    @Query("DELETE FROM manual_shift_entries WHERE dateEpochDay = :epochDay")
    suspend fun deleteForDate(epochDay: Long)
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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
interface NocturnSyncMappingDao {
    @Query("SELECT * FROM nocturn_sync_mappings WHERE localType = :type")
    suspend fun getAllForType(type: String): List<NocturnSyncMappingEntity>

    @Query("SELECT * FROM nocturn_sync_mappings WHERE localType = :type AND localId = :localId LIMIT 1")
    suspend fun getByLocal(type: String, localId: Long): NocturnSyncMappingEntity?

    @Query("SELECT * FROM nocturn_sync_mappings WHERE remoteUid = :uid LIMIT 1")
    suspend fun getByRemoteUid(uid: String): NocturnSyncMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: NocturnSyncMappingEntity)

    @Delete
    suspend fun delete(mapping: NocturnSyncMappingEntity)
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

@Dao
interface WearVisibleSourceDao {
    @Query("SELECT * FROM wear_visible_sources")
    fun observeAll(): Flow<List<WearVisibleSourceEntity>>

    @Query("SELECT * FROM wear_visible_sources")
    suspend fun getAllOnce(): List<WearVisibleSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WearVisibleSourceEntity)

    @Query("DELETE FROM wear_visible_sources WHERE sourceType = :type AND sourceKey = :key")
    suspend fun delete(type: String, key: Long)
}

/** Projektion für [ImportedCalendarDao.observeVisibleEvents] — Event + Farbe seines Kalenders. */
data class ImportedEventRow(
    val title: String,
    val location: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val colorArgb: Int
)

@Dao
interface ImportedCalendarDao {
    @Query("SELECT * FROM imported_calendars ORDER BY importedAtEpochMillis")
    fun observeCalendars(): Flow<List<ImportedCalendarEntity>>

    @Insert
    suspend fun insertCalendar(calendar: ImportedCalendarEntity): Long

    @Insert
    suspend fun insertEvents(events: List<ImportedCalendarEventEntity>)

    @Query("UPDATE imported_calendars SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: Long, visible: Boolean)

    @Query("DELETE FROM imported_calendars WHERE id = :id")
    suspend fun deleteCalendar(id: Long)

    @Query(
        """
        SELECT e.title as title, e.location as location, e.startEpochMillis as startEpochMillis,
               e.endEpochMillis as endEpochMillis, e.allDay as allDay, c.colorArgb as colorArgb
        FROM imported_calendar_events e
        JOIN imported_calendars c ON c.id = e.calendarId
        WHERE c.visible = 1 AND e.startEpochMillis <= :toMillis AND e.endEpochMillis >= :fromMillis
        """
    )
    fun observeVisibleEvents(fromMillis: Long, toMillis: Long): Flow<List<ImportedEventRow>>
}
