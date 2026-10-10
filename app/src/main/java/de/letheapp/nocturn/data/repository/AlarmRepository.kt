package de.letheapp.nocturn.data.repository

import de.letheapp.nocturn.data.db.AlarmDao
import de.letheapp.nocturn.data.db.AlarmEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao
) {
    fun observeAll(): Flow<List<AlarmEntity>> = alarmDao.observeAll()

    suspend fun getById(id: Long): AlarmEntity? = alarmDao.getById(id)

    suspend fun getAutoShiftWakeupAlarm(): AlarmEntity? = alarmDao.getAutoShiftWakeupAlarm()

    suspend fun save(alarm: AlarmEntity): Long =
        if (alarm.id == 0L) alarmDao.insert(alarm) else alarmDao.update(alarm).let { alarm.id }

    suspend fun delete(alarm: AlarmEntity) = alarmDao.delete(alarm)
}
