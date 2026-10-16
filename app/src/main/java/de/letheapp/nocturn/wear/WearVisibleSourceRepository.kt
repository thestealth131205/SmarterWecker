package de.letheapp.nocturn.wear

import de.letheapp.nocturn.data.db.WearSourceType
import de.letheapp.nocturn.data.db.WearVisibleSourceDao
import de.letheapp.nocturn.data.db.WearVisibleSourceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Sentinel-[WearVisibleSourceEntity.sourceKey] für die drei Singleton-Quellen (siehe [WearSourceType]). */
private const val SINGLETON_KEY = 0L

/**
 * Verwaltet, welche Kalenderquellen an eine gekoppelte WearOS-Smartwatch übertragen werden (siehe
 * [WearSettingsScreen]). Fehlender Eintrag für eine Quelle bedeutet "nicht aktiviert".
 */
@Singleton
class WearVisibleSourceRepository @Inject constructor(
    private val dao: WearVisibleSourceDao
) {
    fun observeEnabledKeys(type: WearSourceType): Flow<Set<Long>> =
        dao.observeAll().map { all ->
            all.filter { it.sourceType == type.name && it.enabled }.map { it.sourceKey }.toSet()
        }

    fun observeSingletonEnabled(type: WearSourceType): Flow<Boolean> =
        dao.observeAll().map { all ->
            all.any { it.sourceType == type.name && it.sourceKey == SINGLETON_KEY && it.enabled }
        }

    suspend fun getEnabledKeysOnce(type: WearSourceType): Set<Long> =
        dao.getAllOnce().filter { it.sourceType == type.name && it.enabled }.map { it.sourceKey }.toSet()

    suspend fun isSingletonEnabledOnce(type: WearSourceType): Boolean =
        dao.getAllOnce().any { it.sourceType == type.name && it.sourceKey == SINGLETON_KEY && it.enabled }

    suspend fun setJobOrRotationEnabled(type: WearSourceType, key: Long, enabled: Boolean) {
        dao.upsert(WearVisibleSourceEntity(sourceType = type.name, sourceKey = key, enabled = enabled))
    }

    suspend fun setSingletonEnabled(type: WearSourceType, enabled: Boolean) {
        dao.upsert(WearVisibleSourceEntity(sourceType = type.name, sourceKey = SINGLETON_KEY, enabled = enabled))
    }
}
