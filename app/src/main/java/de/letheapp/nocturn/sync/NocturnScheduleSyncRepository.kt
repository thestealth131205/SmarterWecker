package de.letheapp.nocturn.sync

import de.letheapp.nocturn.data.db.CalDavAccountDao
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.CalendarEntryEntity
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.JobEntity
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.ManualShiftEntryEntity
import de.letheapp.nocturn.data.db.NocturnSyncLocalType
import de.letheapp.nocturn.data.db.NocturnSyncMappingDao
import de.letheapp.nocturn.data.db.NocturnSyncMappingEntity
import de.letheapp.nocturn.data.db.RotationDao
import de.letheapp.nocturn.data.db.RotationDayEntity
import de.letheapp.nocturn.data.db.RotationEntity
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.ShiftEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirektionale Synchronisierung von Jobs/Schichten/Rotationen/Tageseinträgen zwischen NocTurn
 * und der NocTurn-Schedule-REST-API der Nextcloud-Calendar-App (siehe
 * [NocturnScheduleApiClient] und `Nocturn-Kalender/lib/Controller/NocturnScheduleController.php`)
 * — damit zeigt die geforkte Nextcloud-Calendar-App (eigene Vue-Seitenleiste
 * `NocturnScheduleList`) denselben Schichtplan wie NocTurn, inkl. Rückschreiben von Änderungen,
 * die dort vorgenommen werden.
 *
 * Reicht per [NextcloudSyncRepository.syncNow] zusätzlich zum bestehenden CalDAV-Termin-/
 * Schicht-Export angestoßen (gleiches Nextcloud-Konto, kein zweiter Login), aber bewusst nur bei
 * aktiviertem [CalDavAccountEntity.syncNocturnSchedule] (Opt-in, Default aus).
 *
 * Nutzt [NocturnSyncMappingEntity] als lokal<->Server-uid-Zuordnung mit Last-Write-Wins anhand des
 * Server-`updatedAt`-Zeitstempels (siehe Doc-Kommentar dort) und pusht jeden Datensatz nur, wenn
 * sich sein Inhalts-Hash seit dem letzten Lauf geändert hat.
 *
 * **Bekannte Einschränkungen** (Reihenfolge nach Relevanz):
 * - Die Server-API kennt pro Rotationstag nur **eine** Schicht (`dayShiftUids: List<String?>`).
 *   NocTurns neuere Fähigkeit, mehrere Schichten an einem Tag zu hinterlegen (z. B. Frühschicht +
 *   "Kinder-Wochenende"), wird beim Push auf die erste Schicht je Tag reduziert — eine echte
 *   Mehrfachbelegung ließe sich nur durch eine Erweiterung des Server-Schemas lösen.
 * - [ShiftEntity.latestWakeupHour]/[ShiftEntity.latestWakeupMinute] (automatischer "Nächste
 *   Schicht"-Wecker) haben server-seitig kein Gegenstück und werden nicht mitsynchronisiert.
 * - Tageseinträge ([CalendarEntryEntity]/[ManualShiftEntryEntity]) werden nur in einem rollenden
 *   Zeitfenster abgeglichen ([OCCURRENCE_PAST_DAYS]/[OCCURRENCE_FUTURE_DAYS]), nicht über die
 *   gesamte, von NocTurn ggf. über Jahre fortgeschriebene Historie — ein anderer Nextcloud-Nutzer
 *   kann die volle Rotation aber jederzeit selbst über die Vue-Seitenleiste
 *   ("Rotation anwenden") für einen beliebigen Zeitraum anwenden, sobald Job/Schichten/Rotation
 *   einmal synchronisiert sind.
 */
@Singleton
class NocturnScheduleSyncRepository @Inject constructor(
    private val apiClient: NocturnScheduleApiClient,
    private val mappingDao: NocturnSyncMappingDao,
    private val jobDao: JobDao,
    private val shiftDao: ShiftDao,
    private val rotationDao: RotationDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val manualShiftEntryDao: ManualShiftEntryDao,
    private val accountDao: CalDavAccountDao
) {
    suspend fun runSync(account: CalDavAccountEntity, password: String) {
        pushJobs(account, password)
        pushShifts(account, password)
        pushRotations(account, password)
        pushOccurrences(account, password)
        pull(account, password)
    }

    // ---------------------------------------------------------------- Push

    private suspend fun pushJobs(account: CalDavAccountEntity, password: String) {
        val jobs = jobDao.getAllOnce()
        val existingIds = jobs.map { it.id }.toHashSet()
        for (mapping in mappingDao.getAllForType(NocturnSyncLocalType.JOB.name)) {
            if (mapping.localId !in existingIds) {
                runCatching { apiClient.deleteJob(account.serverUrl, account.username, password, mapping.remoteUid) }
                mappingDao.delete(mapping)
            }
        }
        for (job in jobs) {
            val hash = jobHash(job.name, job.mode)
            val mapping = mappingDao.getByLocal(NocturnSyncLocalType.JOB.name, job.id)
            if (mapping != null && mapping.contentHash == hash) continue
            val remote = apiClient.upsertJob(
                account.serverUrl, account.username, password,
                uid = mapping?.remoteUid, name = job.name, mode = job.mode, updatedAt = System.currentTimeMillis()
            )
            mappingDao.upsert(
                NocturnSyncMappingEntity(
                    id = mapping?.id ?: 0,
                    localType = NocturnSyncLocalType.JOB.name,
                    localId = job.id,
                    remoteUid = remote.uid,
                    remoteUpdatedAt = remote.updatedAt,
                    contentHash = hash
                )
            )
        }
    }

    private suspend fun pushShifts(account: CalDavAccountEntity, password: String) {
        val shifts = shiftDao.getAllOnce()
        val existingIds = shifts.map { it.id }.toHashSet()
        for (mapping in mappingDao.getAllForType(NocturnSyncLocalType.SHIFT.name)) {
            if (mapping.localId !in existingIds) {
                runCatching { apiClient.deleteShift(account.serverUrl, account.username, password, mapping.remoteUid) }
                mappingDao.delete(mapping)
            }
        }
        for (shift in shifts) {
            val jobUid = remoteUidFor(NocturnSyncLocalType.JOB, shift.jobId) ?: continue
            val hash = shiftHash(
                shift.name, shift.colorArgb, shift.startHour, shift.startMinute,
                shift.durationMinutes, shift.earliestWakeOffsetMinutes, shift.latestWakeupOffsetMinutes
            )
            val mapping = mappingDao.getByLocal(NocturnSyncLocalType.SHIFT.name, shift.id)
            if (mapping != null && mapping.contentHash == hash) continue
            val remote = apiClient.upsertShift(
                account.serverUrl, account.username, password,
                uid = mapping?.remoteUid, jobUid = jobUid, name = shift.name, color = colorToHex(shift.colorArgb),
                startHour = shift.startHour, startMinute = shift.startMinute, durationMinutes = shift.durationMinutes,
                earliestWakeOffsetMinutes = shift.earliestWakeOffsetMinutes,
                latestWakeupOffsetMinutes = shift.latestWakeupOffsetMinutes,
                updatedAt = System.currentTimeMillis()
            )
            mappingDao.upsert(
                NocturnSyncMappingEntity(
                    id = mapping?.id ?: 0,
                    localType = NocturnSyncLocalType.SHIFT.name,
                    localId = shift.id,
                    remoteUid = remote.uid,
                    remoteUpdatedAt = remote.updatedAt,
                    contentHash = hash
                )
            )
        }
    }

    private suspend fun pushRotations(account: CalDavAccountEntity, password: String) {
        val rotations = rotationDao.getAllOnce()
        val existingIds = rotations.map { it.id }.toHashSet()
        for (mapping in mappingDao.getAllForType(NocturnSyncLocalType.ROTATION.name)) {
            if (mapping.localId !in existingIds) {
                runCatching { apiClient.deleteRotation(account.serverUrl, account.username, password, mapping.remoteUid) }
                mappingDao.delete(mapping)
            }
        }
        for (rotation in rotations) {
            val jobUid = remoteUidFor(NocturnSyncLocalType.JOB, rotation.jobId) ?: continue
            val days = rotationDao.getDaysOnce(rotation.id)
            val cycleLength = (days.maxOfOrNull { it.dayIndex } ?: -1) + 1
            if (cycleLength <= 0) continue
            // Pro Tag nur die erste Schicht - siehe Klassen-Doc-Kommentar "Bekannte Einschränkungen".
            val daysByIndex = days.groupBy({ it.dayIndex }, { it.shiftId })
            val pattern = (0 until cycleLength).map { index ->
                daysByIndex[index]?.firstOrNull { it != null }?.let { shiftId -> remoteUidFor(NocturnSyncLocalType.SHIFT, shiftId) }
            }
            val hash = rotationHash(rotation.name, pattern)
            val mapping = mappingDao.getByLocal(NocturnSyncLocalType.ROTATION.name, rotation.id)
            if (mapping != null && mapping.contentHash == hash) continue
            val remote = apiClient.upsertRotation(
                account.serverUrl, account.username, password,
                uid = mapping?.remoteUid, jobUid = jobUid, name = rotation.name, dayShiftUids = pattern,
                updatedAt = System.currentTimeMillis()
            )
            mappingDao.upsert(
                NocturnSyncMappingEntity(
                    id = mapping?.id ?: 0,
                    localType = NocturnSyncLocalType.ROTATION.name,
                    localId = rotation.id,
                    remoteUid = remote.uid,
                    remoteUpdatedAt = remote.updatedAt,
                    contentHash = hash
                )
            )
        }
    }

    private suspend fun pushOccurrences(account: CalDavAccountEntity, password: String) {
        val today = LocalDate.now().toEpochDay()
        val from = today - OCCURRENCE_PAST_DAYS
        val to = today + OCCURRENCE_FUTURE_DAYS

        val calendarEntries = calendarEntryDao.getRangeWithShiftOnce(from, to)
        for (mapping in mappingDao.getAllForType(NocturnSyncLocalType.CALENDAR_ENTRY.name)) {
            if (calendarEntryDao.getById(mapping.localId) == null) {
                runCatching { apiClient.deleteOccurrence(account.serverUrl, account.username, password, mapping.remoteUid) }
                mappingDao.delete(mapping)
            }
        }
        for (entry in calendarEntries) {
            val shiftId = entry.shiftId ?: continue
            val jobUid = remoteUidFor(NocturnSyncLocalType.JOB, entry.jobId) ?: continue
            val shiftUid = remoteUidFor(NocturnSyncLocalType.SHIFT, shiftId) ?: continue
            val hash = occurrenceHash(jobUid, entry.dateEpochDay, shiftUid)
            val mapping = mappingDao.getByLocal(NocturnSyncLocalType.CALENDAR_ENTRY.name, entry.id)
            if (mapping != null && mapping.contentHash == hash) continue
            val remote = apiClient.upsertOccurrence(
                account.serverUrl, account.username, password,
                uid = mapping?.remoteUid, jobUid = jobUid, epochDay = entry.dateEpochDay, shiftUid = shiftUid,
                updatedAt = System.currentTimeMillis()
            )
            mappingDao.upsert(
                NocturnSyncMappingEntity(
                    id = mapping?.id ?: 0,
                    localType = NocturnSyncLocalType.CALENDAR_ENTRY.name,
                    localId = entry.id,
                    remoteUid = remote.uid,
                    remoteUpdatedAt = remote.updatedAt,
                    contentHash = hash
                )
            )
        }

        val manualEntries = manualShiftEntryDao.getRangeOnce(from, to)
        for (mapping in mappingDao.getAllForType(NocturnSyncLocalType.MANUAL_ENTRY.name)) {
            if (manualShiftEntryDao.getById(mapping.localId) == null) {
                runCatching { apiClient.deleteOccurrence(account.serverUrl, account.username, password, mapping.remoteUid) }
                mappingDao.delete(mapping)
            }
        }
        for (entry in manualEntries) {
            val jobUid = remoteUidFor(NocturnSyncLocalType.JOB, entry.jobId) ?: continue
            val shiftUid = remoteUidFor(NocturnSyncLocalType.SHIFT, entry.shiftId) ?: continue
            val hash = occurrenceHash(jobUid, entry.dateEpochDay, shiftUid)
            val mapping = mappingDao.getByLocal(NocturnSyncLocalType.MANUAL_ENTRY.name, entry.id)
            if (mapping != null && mapping.contentHash == hash) continue
            val remote = apiClient.upsertOccurrence(
                account.serverUrl, account.username, password,
                uid = mapping?.remoteUid, jobUid = jobUid, epochDay = entry.dateEpochDay, shiftUid = shiftUid,
                updatedAt = System.currentTimeMillis()
            )
            mappingDao.upsert(
                NocturnSyncMappingEntity(
                    id = mapping?.id ?: 0,
                    localType = NocturnSyncLocalType.MANUAL_ENTRY.name,
                    localId = entry.id,
                    remoteUid = remote.uid,
                    remoteUpdatedAt = remote.updatedAt,
                    contentHash = hash
                )
            )
        }
    }

    // ---------------------------------------------------------------- Pull

    private suspend fun pull(account: CalDavAccountEntity, password: String) {
        val changes = apiClient.sync(account.serverUrl, account.username, password, account.nocturnScheduleSyncToken)

        for (remote in changes.jobs) applyRemoteJob(remote)
        for (remote in changes.shifts) applyRemoteShift(remote)
        for (remote in changes.rotations) applyRemoteRotation(remote)
        rebuildRotationDays(changes.rotationDays)
        for (remote in changes.occurrences) applyRemoteOccurrence(remote)

        accountDao.update(account.copy(nocturnScheduleSyncToken = changes.serverTime))
    }

    private suspend fun applyRemoteJob(remote: RemoteJob) {
        val mapping = mappingDao.getByRemoteUid(remote.uid)
        if (remote.deletedAt != null) {
            if (mapping != null) {
                jobDao.getById(mapping.localId)?.let { jobDao.delete(it) }
                mappingDao.delete(mapping)
            }
            return
        }
        if (mapping != null && mapping.remoteUpdatedAt >= remote.updatedAt) return
        val localId = if (mapping != null) {
            jobDao.getById(mapping.localId)?.let { jobDao.update(it.copy(name = remote.name, mode = remote.mode)) }
            mapping.localId
        } else {
            jobDao.insert(JobEntity(name = remote.name, mode = remote.mode))
        }
        mappingDao.upsert(
            NocturnSyncMappingEntity(
                id = mapping?.id ?: 0,
                localType = NocturnSyncLocalType.JOB.name,
                localId = localId,
                remoteUid = remote.uid,
                remoteUpdatedAt = remote.updatedAt,
                contentHash = jobHash(remote.name, remote.mode)
            )
        )
    }

    private suspend fun applyRemoteShift(remote: RemoteShift) {
        val mapping = mappingDao.getByRemoteUid(remote.uid)
        if (remote.deletedAt != null) {
            if (mapping != null) {
                shiftDao.getById(mapping.localId)?.let { shiftDao.delete(it) }
                mappingDao.delete(mapping)
            }
            return
        }
        if (mapping != null && mapping.remoteUpdatedAt >= remote.updatedAt) return
        val jobId = remoteUidFor(NocturnSyncLocalType.JOB, remote.jobUid, byRemoteUid = true) ?: return
        val colorArgb = hexToColorArgb(remote.color)
        val localId = if (mapping != null) {
            shiftDao.getById(mapping.localId)?.let {
                shiftDao.update(
                    it.copy(
                        jobId = jobId, name = remote.name, colorArgb = colorArgb,
                        startHour = remote.startHour, startMinute = remote.startMinute,
                        durationMinutes = remote.durationMinutes,
                        earliestWakeOffsetMinutes = remote.earliestWakeOffsetMinutes,
                        latestWakeupOffsetMinutes = remote.latestWakeupOffsetMinutes
                    )
                )
            }
            mapping.localId
        } else {
            shiftDao.insert(
                ShiftEntity(
                    jobId = jobId, name = remote.name, colorArgb = colorArgb,
                    startHour = remote.startHour, startMinute = remote.startMinute,
                    durationMinutes = remote.durationMinutes,
                    earliestWakeOffsetMinutes = remote.earliestWakeOffsetMinutes,
                    latestWakeupOffsetMinutes = remote.latestWakeupOffsetMinutes
                )
            )
        }
        mappingDao.upsert(
            NocturnSyncMappingEntity(
                id = mapping?.id ?: 0,
                localType = NocturnSyncLocalType.SHIFT.name,
                localId = localId,
                remoteUid = remote.uid,
                remoteUpdatedAt = remote.updatedAt,
                contentHash = shiftHash(
                    remote.name, colorArgb, remote.startHour, remote.startMinute,
                    remote.durationMinutes, remote.earliestWakeOffsetMinutes, remote.latestWakeupOffsetMinutes
                )
            )
        )
    }

    private suspend fun applyRemoteRotation(remote: RemoteRotation) {
        val mapping = mappingDao.getByRemoteUid(remote.uid)
        if (remote.deletedAt != null) {
            if (mapping != null) {
                rotationDao.getById(mapping.localId)?.let { rotationDao.delete(it) }
                mappingDao.delete(mapping)
            }
            return
        }
        if (mapping != null && mapping.remoteUpdatedAt >= remote.updatedAt) return
        val jobId = remoteUidFor(NocturnSyncLocalType.JOB, remote.jobUid, byRemoteUid = true) ?: return
        val localId = if (mapping != null) {
            rotationDao.getById(mapping.localId)?.let { rotationDao.update(it.copy(jobId = jobId, name = remote.name)) }
            mapping.localId
        } else {
            rotationDao.insert(RotationEntity(jobId = jobId, name = remote.name))
        }
        // contentHash wird gleich darauf in rebuildRotationDays() anhand des tatsächlichen
        // Tagesmusters neu gesetzt - hier erstmal 0, damit ein noch fehlender Eintrag entsteht.
        mappingDao.upsert(
            NocturnSyncMappingEntity(
                id = mapping?.id ?: 0,
                localType = NocturnSyncLocalType.ROTATION.name,
                localId = localId,
                remoteUid = remote.uid,
                remoteUpdatedAt = remote.updatedAt,
                contentHash = mapping?.contentHash ?: 0
            )
        )
    }

    /**
     * Server speichert ein Rotationsmuster immer als Ganzes neu (siehe `NocturnScheduleService::
     * upsertRotation` - alle Tage derselben Transaktion teilen sich denselben `updatedAt`), ein
     * inkrementeller Sync-Batch enthält deshalb bei jeder Musteränderung immer *alle* aktuellen
     * Tage der betroffenen Rotation zusammen. Deshalb hier bewusst kein Einzel-Tag-Merge, sondern
     * ein kompletter Neuaufbau pro betroffener Rotation - exakt wie der lokale Rotations-Editor
     * selbst sein Muster speichert.
     */
    private suspend fun rebuildRotationDays(rotationDays: List<RemoteRotationDay>) {
        val byRotationUid = rotationDays.filter { it.deletedAt == null }.groupBy { it.rotationUid }
        for ((rotationUid, days) in byRotationUid) {
            val mapping = mappingDao.getByRemoteUid(rotationUid) ?: continue
            val rotation = rotationDao.getById(mapping.localId) ?: continue
            val sorted = days.sortedBy { it.dayIndex }
            val entities = sorted.map { day ->
                val shiftId = day.shiftUid?.let { remoteUidFor(NocturnSyncLocalType.SHIFT, it, byRemoteUid = true) }
                RotationDayEntity(rotationId = mapping.localId, dayIndex = day.dayIndex, shiftId = shiftId)
            }
            rotationDao.deleteDays(mapping.localId)
            rotationDao.insertDays(entities)
            val pattern = sorted.map { it.shiftUid }
            mappingDao.upsert(mapping.copy(contentHash = rotationHash(rotation.name, pattern)))
        }
    }

    private suspend fun applyRemoteOccurrence(remote: RemoteOccurrence) {
        val mapping = mappingDao.getByRemoteUid(remote.uid)
        val localType = if (remote.source == "rotation") NocturnSyncLocalType.CALENDAR_ENTRY else NocturnSyncLocalType.MANUAL_ENTRY

        if (remote.deletedAt != null) {
            if (mapping != null) {
                if (localType == NocturnSyncLocalType.CALENDAR_ENTRY) {
                    calendarEntryDao.getById(mapping.localId)?.let { calendarEntryDao.delete(it) }
                } else {
                    manualShiftEntryDao.getById(mapping.localId)?.let { manualShiftEntryDao.delete(it) }
                }
                mappingDao.delete(mapping)
            }
            return
        }
        if (mapping != null && mapping.remoteUpdatedAt >= remote.updatedAt) return

        val jobId = remoteUidFor(NocturnSyncLocalType.JOB, remote.jobUid, byRemoteUid = true) ?: return
        val shiftId = remote.shiftUid?.let { remoteUidFor(NocturnSyncLocalType.SHIFT, it, byRemoteUid = true) }
        val hash = occurrenceHash(remote.jobUid, remote.epochDay, remote.shiftUid.orEmpty())

        val localId: Long
        if (localType == NocturnSyncLocalType.CALENDAR_ENTRY) {
            val rotationId = remote.rotationUid?.let { remoteUidFor(NocturnSyncLocalType.ROTATION, it, byRemoteUid = true) } ?: return
            localId = if (mapping != null) {
                calendarEntryDao.getById(mapping.localId)?.let {
                    calendarEntryDao.update(it.copy(jobId = jobId, rotationId = rotationId, dateEpochDay = remote.epochDay, shiftId = shiftId))
                }
                mapping.localId
            } else {
                calendarEntryDao.insert(CalendarEntryEntity(jobId = jobId, rotationId = rotationId, dateEpochDay = remote.epochDay, shiftId = shiftId))
            }
        } else {
            if (shiftId == null) return
            localId = if (mapping != null) {
                manualShiftEntryDao.getById(mapping.localId)?.let {
                    manualShiftEntryDao.update(it.copy(jobId = jobId, shiftId = shiftId, dateEpochDay = remote.epochDay))
                }
                mapping.localId
            } else {
                manualShiftEntryDao.insert(ManualShiftEntryEntity(jobId = jobId, shiftId = shiftId, dateEpochDay = remote.epochDay))
            }
        }
        mappingDao.upsert(
            NocturnSyncMappingEntity(
                id = mapping?.id ?: 0,
                localType = localType.name,
                localId = localId,
                remoteUid = remote.uid,
                remoteUpdatedAt = remote.updatedAt,
                contentHash = hash
            )
        )
    }

    // -------------------------------------------------------------- Helfer

    private suspend fun remoteUidFor(type: NocturnSyncLocalType, localId: Long): String? =
        mappingDao.getByLocal(type.name, localId)?.remoteUid

    /** [byRemoteUid]=true kehrt die Richtung um: liefert die lokale ID zu einem Server-uid (remoteUid ist global eindeutig, [type] dient hier nur der Lesbarkeit am Call-Standort). */
    private suspend fun remoteUidFor(type: NocturnSyncLocalType, remoteUid: String, byRemoteUid: Boolean): Long? =
        mappingDao.getByRemoteUid(remoteUid)?.localId

    private fun jobHash(name: String, mode: String): Int = "$name|$mode".hashCode()

    private fun shiftHash(
        name: String, colorArgb: Int, startHour: Int, startMinute: Int,
        durationMinutes: Int, earliestWakeOffsetMinutes: Int, latestWakeupOffsetMinutes: Int
    ): Int = "$name|$colorArgb|$startHour|$startMinute|$durationMinutes|$earliestWakeOffsetMinutes|$latestWakeupOffsetMinutes".hashCode()

    private fun rotationHash(name: String, pattern: List<String?>): Int = "$name|${pattern.joinToString(",")}".hashCode()

    private fun occurrenceHash(jobUid: String, epochDay: Long, shiftUid: String): Int = "$jobUid|$epochDay|$shiftUid".hashCode()

    private fun colorToHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    private fun hexToColorArgb(hex: String): Int = runCatching {
        (0xFF shl 24) or (hex.removePrefix("#").toLong(16).toInt() and 0xFFFFFF)
    }.getOrDefault(0xFFFF9800.toInt())

    private companion object {
        const val OCCURRENCE_PAST_DAYS = 14L
        const val OCCURRENCE_FUTURE_DAYS = 180L
    }
}
