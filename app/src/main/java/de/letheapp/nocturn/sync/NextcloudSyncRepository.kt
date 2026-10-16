package de.letheapp.nocturn.sync

import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.CalDavAccountDao
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import de.letheapp.nocturn.data.db.SyncedEventDao
import de.letheapp.nocturn.data.db.SyncedEventEntity
import de.letheapp.nocturn.data.db.SyncedLocalType
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestriert die Nextcloud-CalDAV-Synchronisierung (siehe CLAUDE.md "Kalendersync").
 *
 * - Freie Termine ([AppointmentEntity]): bidirektional (Push lokaler Änderungen, Pull entfernter
 *   Änderungen/Neuanlagen/Löschungen) über reines CalDAV.
 * - Schichten/Jobs/Rotationen: ausschließlich über die strukturierte NocTurn-Schedule-REST-API der
 *   NocTurn-Kalender-App ([NocturnScheduleSyncRepository]/[NocturnScheduleApiClient]) — NICHT mehr
 *   zusätzlich als einfache CalDAV-VEVENTs in den normalen Kalender exportiert. Der frühere
 *   Doppelweg (generischer CalDAV-Export der Schicht-Vorkommen UND strukturierter Schedule-Sync)
 *   führte dazu, dass dieselbe Schicht dem Nutzer doppelt angezeigt wurde, sobald beide Wege
 *   gleichzeitig aktiv waren. Ist die NocTurn-Kalender-App auf dem Zielserver nicht installiert,
 *   wird das klar gemeldet (siehe [checkNocturnKalenderAvailable]) statt stillschweigend auf den
 *   generischen CalDAV-Weg auszuweichen.
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
    private val scheduleSyncRepository: NocturnScheduleSyncRepository,
    private val scheduleApiClient: NocturnScheduleApiClient
) {
    fun observeAccount(): Flow<CalDavAccountEntity?> = accountDao.observeAccount()

    /**
     * Prüft, ob die NocTurn-Kalender-App auf diesem Server verfügbar ist — Voraussetzung für den
     * Schichtplan-Sync. Wird vor dem Verbinden und vor jedem Umschalten des entsprechenden
     * Schalters im UI aufgerufen, damit NocTurn klar "App fehlt" meldet statt den Sync einfach
     * fehlschlagen zu lassen.
     */
    suspend fun checkNocturnKalenderAvailable(serverUrl: String, username: String, password: String): Boolean =
        scheduleApiClient.isNocturnKalenderInstalled(serverUrl, username, password)

    /** Wie [checkNocturnKalenderAvailable], aber für das bereits verbundene Konto (Passwort kommt
     * aus dem verschlüsselten Store statt aus einem frisch eingegebenen Formular). */
    suspend fun checkNocturnKalenderAvailableForConnectedAccount(): Boolean {
        val account = accountDao.getAccountOnce() ?: return false
        val password = credentialsStore.getPassword(account.id) ?: return false
        return scheduleApiClient.isNocturnKalenderInstalled(account.serverUrl, account.username, password)
    }

    suspend fun discoverCalendars(serverUrl: String, username: String, password: String): List<CalDavCalendarInfo> =
        calDavClient.discoverCalendars(serverUrl, username, password)

    /** Verbindet ein (einziges, bestehende Konten werden ersetzt) Nextcloud-Konto mit dem gewählten Kalender. */
    suspend fun connect(
        serverUrl: String,
        username: String,
        password: String,
        calendar: CalDavCalendarInfo,
        syncAppointments: Boolean,
        syncNocturnSchedule: Boolean = false
    ): Long {
        disconnect()
        val id = accountDao.insert(
            CalDavAccountEntity(
                serverUrl = serverUrl.trim(),
                username = username.trim(),
                calendarHref = calendar.href,
                calendarDisplayName = calendar.displayName,
                syncAppointments = syncAppointments,
                syncShifts = false,
                syncNocturnSchedule = syncNocturnSchedule
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

    suspend fun updateSyncOptions(account: CalDavAccountEntity, syncAppointments: Boolean, syncNocturnSchedule: Boolean) {
        accountDao.update(account.copy(syncAppointments = syncAppointments, syncNocturnSchedule = syncNocturnSchedule))
    }

    suspend fun syncNow(): Result<Unit> {
        val account = accountDao.getAccountOnce() ?: return Result.failure(CalDavException("Kein Nextcloud-Konto verbunden"))
        val password = credentialsStore.getPassword(account.id)
            ?: return Result.failure(CalDavException("Kein gespeichertes Nextcloud-Passwort gefunden"))
        return try {
            // Aufräumen eines früheren, inzwischen entfernten Wegs: Schicht-Vorkommen wurden
            // früher zusätzlich als einfache CalDAV-VEVENTs exportiert (führte zu doppelt
            // angezeigten Schichten neben dem strukturierten Schedule-Sync). Bereits existierende
            // Alt-Einträge werden hier einmalig entfernt, danach ist diese Schleife ein No-Op.
            removeLegacyShiftCaldavEvents(account, password)
            if (account.syncAppointments) syncAppointments(account, password)
            if (account.syncNocturnSchedule) {
                if (scheduleApiClient.isNocturnKalenderInstalled(account.serverUrl, account.username, password)) {
                    scheduleSyncRepository.runSync(account, password)
                } else {
                    throw NocturnScheduleApiException(
                        "Die NocTurn-Kalender-App ist auf diesem Nextcloud-Server nicht installiert. " +
                            "Installiere sie zuerst, um Schichten/Jobs/Rotationen zu synchronisieren."
                    )
                }
            }
            // scheduleSyncRepository.runSync() aktualisiert ggf. selbst schon nocturnScheduleSyncToken -
            // Konto frisch nachladen, damit dieses abschließende Update diesen neuen Cursor nicht wieder
            // mit dem alten, hier noch zwischengespeicherten Stand überschreibt.
            val latest = accountDao.getAccountOnce() ?: account
            accountDao.update(latest.copy(lastSyncEpochMillis = System.currentTimeMillis(), lastSyncError = null))
            Result.success(Unit)
        } catch (e: Exception) {
            val latest = accountDao.getAccountOnce() ?: account
            accountDao.update(latest.copy(lastSyncError = e.message ?: "Unbekannter Fehler"))
            Result.failure(e)
        }
    }

    private suspend fun removeLegacyShiftCaldavEvents(account: CalDavAccountEntity, password: String) {
        val legacyEntries = syncedEventDao.getForAccountAndType(account.id, SyncedLocalType.SHIFT.name)
        for (entry in legacyEntries) {
            runCatching { calDavClient.deleteEvent(entry.href, account.username, password) }
            syncedEventDao.delete(entry)
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
                    showAsBusy = parsed.showAsBusy,
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
                allDay = appointment.allDay,
                showAsBusy = appointment.showAsBusy
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
            showAsBusy = parsed.showAsBusy,
            dirty = false
        )

    private fun epochDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
