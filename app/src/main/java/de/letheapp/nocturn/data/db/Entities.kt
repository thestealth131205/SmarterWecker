package de.letheapp.nocturn.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    val repeatDays: Int = 0,
    val soundUri: String? = null,
    val rotationId: Long? = null
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "shifts",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val colorArgb: Int,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int
)

@Entity(
    tableName = "rotations",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class RotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val name: String,
    val startDateEpochDay: Long
)

@Entity(
    tableName = "rotation_days",
    foreignKeys = [
        ForeignKey(
            entity = RotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("rotationId"), Index("shiftId")]
)
data class RotationDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rotationId: Long,
    val dayIndex: Int,
    val shiftId: Long?
)

/**
 * Vom Wecker fortgeschriebener Kalendereintrag: welche Schicht an welchem Tag laut
 * Rotation gilt. Wird beim Speichern einer Rotation für mehrere Jahre im Voraus
 * neu berechnet (siehe CalendarRepository.regenerateCalendarEntries).
 */
@Entity(
    tableName = "calendar_entries",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["rotationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("jobId"), Index("rotationId"), Index("shiftId"), Index("dateEpochDay")]
)
data class CalendarEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val rotationId: Long,
    val dateEpochDay: Long,
    val shiftId: Long?
)
