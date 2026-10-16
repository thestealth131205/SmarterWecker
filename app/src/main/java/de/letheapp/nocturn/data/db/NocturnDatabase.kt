package de.letheapp.nocturn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlarmEntity::class,
        JobEntity::class,
        ShiftEntity::class,
        RotationEntity::class,
        RotationDayEntity::class,
        CalendarEntryEntity::class,
        LessonEntity::class,
        AppointmentEntity::class,
        CalDavAccountEntity::class,
        SyncedEventEntity::class,
        ManualShiftEntryEntity::class,
        HolidaySettingsEntity::class,
        HolidayEntity::class,
        IoBrokerAccountEntity::class,
        TaskEntity::class,
        WearVisibleSourceEntity::class,
        NocturnSyncMappingEntity::class,
        ImportedCalendarEntity::class,
        ImportedCalendarEventEntity::class
    ],
    version = 18,
    // Es existieren bereits reale Nutzdaten (Alarme, Jobs, Schichten, Rotationen, Kalender-
    // einträge) auf installierten Geräten — Schema-Export ist jetzt an, damit künftige
    // Versionsbumps per echter Migration (androidx.room.migration.Migration) statt eines
    // destruktiven Neuaufbaus erfolgen können. Siehe DatabaseModule.kt.
    exportSchema = true
)
abstract class NocturnDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun jobDao(): JobDao
    abstract fun shiftDao(): ShiftDao
    abstract fun rotationDao(): RotationDao
    abstract fun calendarEntryDao(): CalendarEntryDao
    abstract fun lessonDao(): LessonDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun calDavAccountDao(): CalDavAccountDao
    abstract fun syncedEventDao(): SyncedEventDao
    abstract fun manualShiftEntryDao(): ManualShiftEntryDao
    abstract fun holidaySettingsDao(): HolidaySettingsDao
    abstract fun holidayDao(): HolidayDao
    abstract fun ioBrokerAccountDao(): IoBrokerAccountDao
    abstract fun taskDao(): TaskDao
    abstract fun wearVisibleSourceDao(): WearVisibleSourceDao
    abstract fun nocturnSyncMappingDao(): NocturnSyncMappingDao
    abstract fun importedCalendarDao(): ImportedCalendarDao
}
