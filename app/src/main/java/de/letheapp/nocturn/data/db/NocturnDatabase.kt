package de.letheapp.nocturn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlarmEntity::class,
        JobEntity::class,
        ShiftEntity::class,
        RotationEntity::class,
        RotationDayEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NocturnDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun jobDao(): JobDao
    abstract fun shiftDao(): ShiftDao
    abstract fun rotationDao(): RotationDao
}
