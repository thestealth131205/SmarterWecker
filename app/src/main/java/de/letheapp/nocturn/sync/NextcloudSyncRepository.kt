package de.letheapp.nocturn.sync

import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.CalDavAccountDao
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.JobDao
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.db.SyncedEventDao
import de.letheapp.nocturn.data.db.SyncedEventEntity
import de.letheapp.nocturn.data.db.SyncedLocalType
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestriert die Nextcloud-CalDAV-Synchronisierung (siehe CLAUDE.md "Kalendersync").
 *
 * Richtung bewusst unterschiedlich je Datentyp, um das in CLAUDE.md offene "TBD ob Lesen reicht"
 * aufzulösen:
 * - Freie Termine ([AppointmentEntity]): bidirektional (Push lokaler Änderungen, Pull entfernter
 *   Änderungen/Neuanlagen/Löschungen).
 * - Schicht-Kalendereinträge (aus Rotationen): nur Push (NocTurn -> Nextcloud) in einem rollenden
 *   Zeitfenster von [SHIFT_SYNC_WINDOW_DAYS] Tagen, da diese Einträge von der Rotation abgeleitet
 *   sind und nicht sinnvoll aus Nextcloud zurückgelesen werden können.
 *
 * Bekannte Vereinfachung: lokal gelöschte Termine werden aktuell nicht automatisch auf Nextcloud
 * entfernt (kein Tombstone-Tracking) — Löschen auf Nextcloud-Seite entfernt den Termin dagegen
 * korrekt auch lokal wieder.
 */
@Singleton
class NextcloudSyncRepository @Inject constructor(
    private val calDavClient: CalDavClient,
    private val credentialsStore: NextcloudCredentialsStore,
    private val accountDao: CalDavAccountDao,
    private val syncedEventDao: SyncedEventDao,
    private val appointmentDao: AppointmentDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val shiftDao: ShiftDao,
    private val jobDao: JobDao
) {
    fun observeAccount(): Flow<CalDavAccountEntity?> = accountDao.observeAccount()

    suspend fun discoverCalendars(serverUrl: String, username: String, password: String): List<CalDavCalendarInfo> =
        calDavClient.discoverCalendars(serverUrl, username, password)

    /** Verbindet ein (einziges, bestehende Konten werden ersetzt) Nextcloud-Konto mit dem gewählten Kalender. */
    suspend fun connect(
        serverUrl: String,
        username: String,
        password: String,
        calendar: CalDavCalendarInfo,
        syncAppointments: Boolean,
        syncShifts: Boolean
    ): Long {
        disconnect()
        val id = accountDao.insert(
            CalDavAccountEntity(
                serverUrl = serverUrl.trim(),
                username = username.trim(),
                calendarHref = calendar.href,
                calendarDisplayName = calendar.displayName,
                syncAppointments = syncAppointments,
                syncShifts = syncShifts
            )
        )
        credentialsStore.savePassword(id, password)
        return id
    }

    suspend fun disconnect() {
        accountDao.getAccountOnce()?.let { account ->
            credentialsStore.clearPassword(account.id)
            syncedEventDao.deleteAllForAccount(account.id)
            accountDao.delete(account)
        }
    }

    suspend fun updateSyncOptions(account: CalDavAccountEntity, syncAppointments: Boolean, syncShifts: Boolean) {
        accountDao.update(account.copy(syncAppointments = syncAppointments, syncShifts = syncShifts))
    }

    suspend fun syncNow(): Result<Unit> {
        val account = accountDao.getAccountOnce() ?: return Result.failure(CalDavException("Kein Nextcloud-Konto verbunden"))
        val password = credentialsStore.getPassword(account.id)
            ?: return Result.failure(CalDavException("Kein gespeichertes Nextcloud-Passwort gefunden"))
        return try {
            if (account.syncAppointments) syncAppointments(account, password)
            if (account.syncShifts) pushShiftOccurrences(account, password)
            accountDao.update(account.copy(lastSyncEpochMillis = System.currentTimeMillis(), lastSyncError = null))
            Result.success(Unit)
        } catch (e: Exception) {
            accountDao.update(account.copy(lastSyncError = e.message ?: "Unbekannter Fehler"))
            Result.failure(e)
        }
    }

    private suspend fun syncAppointments(account: CalDavAccountEntity, password: String) {
        val remoteEvents = calDavClient.fetchEvents(account.calendarHref, account.username, password)
        val remoteByHref = remoteEvents.associateBy { it.href }
        val known = syncedEventDao.getForAccountAndType(account.id, SyncedLocalType.APPOINTMENT.name)
        val knownHrefs = known.map { it.href }.toHashSet()

        for (entry in known) {
            val remote = remoteByHref[entry.href]
            if (remote == null) {
                entry.localKey.toLongOrNull()?.let { id -> appointmentDao.getById(id)?.let { appointmentDao.delete(it) } }
                syncedEventDao.delete(entry)
            } else if (remote.etag != entry.etag) {
                val parsed = IcsFormat.parseEvent(remote.icsData) ?: continue
                val appointmentId = entry.localKey.toLongOrNull() ?: continue
                val existing = appointmentDao.getById(appointmentId) ?: continue
                appointmentDao.update(applyParsedToAppointment(existing, parsed))
                syncedEventDao.update(entry.copy(etag = remote.etag))
            }
        }

        for (remote in remoteEvents.filterNot { it.href in knownHrefs }) {
            val parsed = IcsFormat.parseEvent(remote.icsData) ?: continue
            val newId = appointmentDao.insert(
                AppointmentEntity(
                    title = parsed.summary,
                    location = parsed.location,
                    description = parsed.description,
                    startEpochMillis = parsed.startEpochMillis,
                    endEpochMillis = parsed.endEpochMillis,
                    startEpochDay = epochDay(parsed.startEpochMillis),
                    endEpochDay = epochDay(parsed.endEpochMillis),
                    allDay = parsed.allDay,
                    colorArgb = CalendarRepository.DEFAULT_APPOINTMENT_COLOR_ARGB,
                    dirty = false
                )
            )
            syncedEventDao.insert(
                SyncedEventEntity(
                    accountId = account.id,
                    localType = SyncedLocalType.APPOINTMENT.name,
                    localKey = newId.toString(),
                    href = remote.href,
                    etag = remote.etag
                )
            )
        }

        for (appointment in appointmentDao.getDirty()) {
            val localKey = appointment.id.toString()
            val existingSync = syncedEventDao.getByLocalKey(account.id, SyncedLocalType.APPOINTMENT.name, localKey)
            val uid = "nocturn-appointment-${appointment.id}@letheapp.de"
            val href = existingSync?.href ?: "${account.calendarHref}$uid.ics"
            val ics = IcsFormat.buildEvent(
                uid = uid,
                summary = appointment.title,
                location = appointment.location,
                description = appointment.description,
                startEpochMillis = appointment.startEpochMillis,
                endEpochMillis = appointment.endEpochMillis,
                allDay = appointment.allDay
            )
            val newEtag = calDavClient.putEvent(href, account.username, password, ics, existingSync?.etag).orEmpty()
            if (existingSync == null) {
                syncedEventDao.insert(
                    SyncedEventEntity(
                        accountId = account.id,
                        localType = SyncedLocalType.APPOINTMENT.name,
                        localKey = localKey,
                        href = href,
                        etag = newEtag
                    )
                )
            } else {
                syncedEventDao.update(existingSync.copy(etag = newEtag))
            }
            appointmentDao.update(appointment.copy(dirty = false))
        }
    }

    private suspend fun pushShiftOccurrences(account: CalDavAccountEntity, password: String) {
        val today = LocalDate.now().toEpochDay()
        val windowEnd = today + SHIFT_SYNC_WINDOW_DAYS
        val entries = calendarEntryDao.getRangeWithShiftOnce(today, windowEnd)
        val known = syncedEventDao.getForAccountAndType(account.id, SyncedLocalType.SHIFT.name)
        val knownByKey = known.associateBy { it.localKey }
        val currentKeys = HashSet<String>()

        for (entry in entries) {
            val shiftId = entry.shiftId ?: continue
            val shift = shiftDao.getById(shiftId) ?: continue
            val job = jobDao.getById(entry.jobId)
            // shiftId mit im Schlüssel, da an einem Tag mehrere Schichten derselben Rotation
            // gleichzeitig gelten können (siehe CLAUDE.md "Rotationen") und sonst kollidieren würden.
            val key = "${entry.rotationId}:${entry.dateEpochDay}:$shiftId"
            currentKeys += key

            val date = LocalDate.ofEpochDay(entry.dateEpochDay)
            val startMillis = date.atTime(shift.startHour, shift.startMinute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = startMillis + shift.durationMinutes * 60_000L
            val summary = if (job != null) "${shift.name} (${job.name})" else shift.name
            val contentHash = "$summary|$startMillis|$endMillis".hashCode()

            val existingSync = knownByKey[key]
            if (existingSync != null && existingSync.contentHash == contentHash) continue

            val uid = "nocturn-shift-${entry.rotationId}-${entry.dateEpochDay}-$shiftId@letheapp.de"
            val href = existingSync?.href ?: "${account.calendarHref}$uid.ics"
            val ics = IcsFormat.buildEvent(
                uid = uid,
                summary = summary,
                location = "",
                description = "",
                startEpochMillis = startMillis,
                endEpochMillis = endMillis,
                allDay = false
            )
            val newEtag = calDavClient.putEvent(href, account.username, password, ics, existingSync?.etag)
                ?: existingSync?.etag.orEmpty()
            if (existingSync == null) {
                syncedEventDao.insert(
                    SyncedEventEntity(
                        accountId = account.id,
                        localType = SyncedLocalType.SHIFT.name,
                        localKey = key,
                        href = href,
                        etag = newEtag,
                        contentHash = contentHash
                    )
                )
            } else {
                syncedEventDao.update(existingSync.copy(etag = newEtag, href = href, contentHash = contentHash))
            }
        }

        for (entry in known) {
            if (entry.localKey !in currentKeys) {
                runCatching { calDavClient.deleteEvent(entry.href, account.username, password) }
                syncedEventDao.delete(entry)
            }
        }
    }

    private fun applyParsedToAppointment(existing: AppointmentEntity, parsed: IcsFormat.ParsedEvent): AppointmentEntity =
        existing.copy(
            title = parsed.summary,
            location = parsed.location,
            description = parsed.description,
            startEpochMillis = parsed.startEpochMillis,
            endEpochMillis = parsed.endEpochMillis,
            startEpochDay = epochDay(parsed.startEpochMillis),
            endEpochDay = epochDay(parsed.endEpochMillis),
            allDay = parsed.allDay,
            dirty = false
        )

    private fun epochDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private companion object {
        const val SHIFT_SYNC_WINDOW_DAYS = 180L
    }
}
