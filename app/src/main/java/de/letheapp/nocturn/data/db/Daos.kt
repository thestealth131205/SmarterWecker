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

    @Insert
    suspend fun insert(job: JobEntity): Long

    @Delete
    suspend fun delete(job: JobEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE jobId = :jobId ORDER BY startHour, startMinute")
    fun observeForJob(jobId: Long): Flow<List<ShiftEntity>>

    @Insert
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Delete
    suspend fun delete(shift: ShiftEntity)
}

@Dao
interface RotationDao {
    @Query("SELECT * FROM rotations WHERE jobId = :jobId")
    fun observeForJob(jobId: Long): Flow<List<RotationEntity>>

    @Insert
    suspend fun insert(rotation: RotationEntity): Long

    @Delete
    suspend fun delete(rotation: RotationEntity)

    @Query("SELECT * FROM rotation_days WHERE rotationId = :rotationId ORDER BY dayIndex")
    fun observeDays(rotationId: Long): Flow<List<RotationDayEntity>>

    @Insert
    suspend fun insertDays(days: List<RotationDayEntity>)
}
