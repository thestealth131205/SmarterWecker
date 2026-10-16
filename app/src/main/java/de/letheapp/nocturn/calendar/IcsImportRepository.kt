package de.letheapp.nocturn.calendar

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.letheapp.nocturn.data.db.ImportedCalendarDao
import de.letheapp.nocturn.data.db.ImportedCalendarEntity
import de.letheapp.nocturn.data.db.ImportedCalendarEventEntity
import de.letheapp.nocturn.data.db.ImportedEventRow
import de.letheapp.nocturn.sync.IcsFormat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet per Datei importierte externe ICS-Kalender (3-Punkte-Menü der Kalender-Monatsansicht
 * → "Externe Kalender (ICS)"), damit z. B. ein aus einer anderen Kalender-App exportierter
 * Kalender in NocTurn angezeigt werden kann — rein lesend, einmalig beim Import, kein periodischer
 * Re-Fetch (anders als [de.letheapp.nocturn.holidays.HolidayRepository], da es sich um eine lokal
 * gewählte Datei statt einer Online-Quelle handelt). Sichtbarkeit pro Kalender wird per Kästchen in
 * der Seitenleiste umgeschaltet ([ImportedCalendarEntity.visible]), ohne die importierten Events zu
 * löschen.
 */
@Singleton
class IcsImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ImportedCalendarDao
) {
    fun observeCalendars(): Flow<List<ImportedCalendarEntity>> = dao.observeCalendars()

    fun observeVisibleEvents(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ImportedEventRow>> =
        dao.observeVisibleEvents(fromEpochMillis, toEpochMillis)

    suspend fun setVisible(calendarId: Long, visible: Boolean) = dao.setVisible(calendarId, visible)

    suspend fun deleteCalendar(calendarId: Long) = dao.deleteCalendar(calendarId)

    /** Liest die gewählte .ics-Datei komplett ein und legt einen neuen [ImportedCalendarEntity] mit
     *  allen darin enthaltenen VEVENTs an. Gibt die Anzahl der importierten Events zurück (0, falls
     *  die Datei keine lesbaren Events enthielt). */
    suspend fun importFromUri(uri: Uri, name: String, colorArgb: Int): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return 0
        val events = IcsFormat.parseEvents(text)
        if (events.isEmpty()) return 0

        val calendarId = dao.insertCalendar(
            ImportedCalendarEntity(
                name = name.ifBlank { "Importierter Kalender" },
                colorArgb = colorArgb,
                importedAtEpochMillis = System.currentTimeMillis()
            )
        )
        dao.insertEvents(
            events.map { event ->
                ImportedCalendarEventEntity(
                    calendarId = calendarId,
                    title = event.summary.ifBlank { "Termin" },
                    location = event.location,
                    startEpochMillis = event.startEpochMillis,
                    endEpochMillis = event.endEpochMillis,
                    allDay = event.allDay
                )
            }
        )
        return events.size
    }
}
