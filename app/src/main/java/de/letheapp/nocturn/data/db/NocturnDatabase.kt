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
        SyncedEventEntity::class
    ],
    version = 5,
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
}
