package de.letheapp.nocturn.sync

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.AppointmentAttachmentEntity
import de.letheapp.nocturn.data.db.AppointmentDao
import de.letheapp.nocturn.data.db.AppointmentEntity
import de.letheapp.nocturn.data.db.AppointmentReminderEntity
import de.letheapp.nocturn.data.db.CalDavAccountDao
import de.letheapp.nocturn.data.db.CalDavAccountEntity
import de.letheapp.nocturn.data.db.SyncedEventDao
import de.letheapp.nocturn.data.db.SyncedEventEntity
import de.letheapp.nocturn.data.db.SyncedLocalType
import de.letheapp.nocturn.data.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
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
 *
 * Erinnerungen ([AppointmentReminderEntity]) und Anhänge ([AppointmentAttachmentEntity]) werden
 * als VALARM- bzw. eingebettete Base64-ATTACH-Properties in dieselbe ICS mitgeschrieben/-gelesen
 * (siehe [IcsFormat], [applyParsedRemindersAndAttachments]) — Anhänge über [MAX_ATTACHMENT_BYTES]
 * werden dabei übersprungen, und der Erinnerungs-Kanal (Benachrichtigung/E-Mail) geht beim
 * Zurücklesen verloren, siehe Doc-Kommentar dort.
 */
@Singleton
class NextcloudSyncRepository @Inject constructor(
    private val calDavClient: CalDavClient,
    private val credentialsStore: NextcloudCredentialsStore,
    private val accountDao: CalDavAccountDao,
    private val syncedEventDao: SyncedEventDao,
    private val appointmentDao: AppointmentDao,
    private val scheduleSyncRepository: NocturnScheduleSyncRepository,
    private val scheduleApiClient: NocturnScheduleApiClient,
    @ApplicationContext private val context: Context
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
                applyParsedRemindersAndAttachments(appointmentId, parsed)
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
            applyParsedRemindersAndAttachments(newId, parsed)
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
            val reminderMinutes = appointmentDao.getRemindersForAppointment(appointment.id).map { it.minutesBefore }
            val attachments = appointmentDao.getAttachmentsOnce(appointment.id).mapNotNull(::toIcsAttachment)
            val ics = IcsFormat.buildEvent(
                uid = uid,
                summary = appointment.title,
                location = appointment.location,
                description = appointment.description,
                startEpochMillis = appointment.startEpochMillis,
                endEpochMillis = appointment.endEpochMillis,
                allDay = appointment.allDay,
                showAsBusy = appointment.showAsBusy,
                reminderMinutesBefore = reminderMinutes,
                attachments = attachments
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

    /**
     * Ersetzt Erinnerungen/Anhänge eines Termins durch die aus dessen frisch gelesenen VALARM-/
     * ATTACH-Properties — ein kompletter Neuaufbau statt eines Merges, damit nichts doppelt
     * anwächst, wenn derselbe Termin mehrfach synchronisiert wird (siehe Doc-Kommentar der Klasse).
     * **Bekannte Einschränkung**: Der Erinnerungs-Kanal (Benachrichtigung/E-Mail) und eine
     * E-Mail-Adresse lassen sich aus einem reinen VALARM-Block nicht zurückgewinnen — nach einem
     * Pull laufen alle wiederhergestellten Erinnerungen als normale Benachrichtigung.
     */
    private suspend fun applyParsedRemindersAndAttachments(appointmentId: Long, parsed: IcsFormat.ParsedEvent) {
        appointmentDao.deleteAllRemindersForAppointment(appointmentId)
        for (minutes in parsed.reminderMinutesBefore) {
            appointmentDao.insertReminder(AppointmentReminderEntity(appointmentId = appointmentId, minutesBefore = minutes))
        }

        appointmentDao.deleteAllAttachmentsForAppointment(appointmentId)
        for (attachment in parsed.attachments) {
            val uri = saveDecodedAttachment(appointmentId, attachment) ?: continue
            appointmentDao.insertAttachment(
                AppointmentAttachmentEntity(
                    appointmentId = appointmentId,
                    uri = uri.toString(),
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType
                )
            )
        }
    }

    /** Liest die Bytes eines lokalen Anhangs über dessen `content://`-URI und kodiert sie als
     *  Base64 für eine eingebettete ATTACH-Property. `null` bei nicht mehr auflösbarer URI oder
     *  wenn die Datei [MAX_ATTACHMENT_BYTES] überschreitet (sonst würde ein einzelner Termin-Push
     *  unverhältnismäßig groß). */
    private fun toIcsAttachment(attachment: AppointmentAttachmentEntity): IcsFormat.IcsAttachment? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.size > MAX_ATTACHMENT_BYTES) return null
        return IcsFormat.IcsAttachment(
            fileName = attachment.fileName,
            mimeType = attachment.mimeType,
            base64Data = Base64.getEncoder().encodeToString(bytes)
        )
    }

    /** Dekodiert einen aus Nextcloud gepullten ATTACH-Anhang und legt ihn im App-privaten
     *  Speicher ab (anders als bei selbst ausgewählten Anhängen gibt es hier keine referenzierbare
     *  Quell-URI — die Bytes kommen eingebettet aus der ICS-Datei), erreichbar über den bereits
     *  registrierten FileProvider wie jeder andere Anhang. */
    private fun saveDecodedAttachment(appointmentId: Long, attachment: IcsFormat.ParsedAttachment): Uri? = runCatching {
        val bytes = Base64.getMimeDecoder().decode(attachment.base64Data)
        val dir = File(context.filesDir, "synced_attachments").apply { mkdirs() }
        val safeName = attachment.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "appt${appointmentId}_${System.currentTimeMillis()}_$safeName")
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    private fun epochDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private companion object {
        /** ~4 MB Rohdatei (~5,3 MB nach Base64) als Obergrenze für eingebettete ICS-Anhänge. */
        const val MAX_ATTACHMENT_BYTES = 4 * 1024 * 1024
    }
}
