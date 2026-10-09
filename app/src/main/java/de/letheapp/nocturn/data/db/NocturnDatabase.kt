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
        HolidayEntity::class
    ],
    version = 7,
    exportSchema = false
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
}
