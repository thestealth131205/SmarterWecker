package de.letheapp.nocturn.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein aus den Geräte-Kontakten gelesener Geburtstag. [year] ist null, wenn der Kontakt zwar
 * Tag/Monat, aber kein Geburtsjahr hinterlegt hat (bei Android-Kontakten üblich im Format
 * "--MM-dd").
 */
data class ContactBirthday(
    val contactId: Long,
    val displayName: String,
    val photoUri: String?,
    /** 1..12 */
    val month: Int,
    /** 1..31 */
    val day: Int,
    val year: Int?
)

/**
 * Liest Geburtstage aus den Geräte-Kontakten (CLAUDE.md Kernfunktion 6: Geburtstage von Kontakten
 * mit Namen und — falls vorhanden — Bild automatisch im Kalender). Braucht die Laufzeit-
 * Berechtigung [Manifest.permission.READ_CONTACTS]; ohne sie liefern alle Lesefunktionen eine
 * leere Liste, statt zu werfen. Geburtstage wiederholen sich jährlich, daher wird für die Anzeige
 * das Tag/Monat auf das jeweils dargestellte Jahr abgebildet.
 */
@Singleton
class ContactBirthdayRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun loadBirthdays(): List<ContactBirthday> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.PHOTO_URI,
            Event.START_DATE
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?"
        val selectionArgs = arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())

        val result = mutableListOf<ContactBirthday>()
        // Nur ein Geburtstag pro Kontakt übernehmen (manche Kontakte haben mehrere Event-Rows).
        val seenContacts = HashSet<Long>()

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME)
            val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_URI)
            val dateIdx = cursor.getColumnIndexOrThrow(Event.START_DATE)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                if (!seenContacts.add(contactId)) continue
                val name = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } ?: continue
                val raw = cursor.getString(dateIdx) ?: continue
                val parsed = parseBirthday(raw) ?: continue
                result += ContactBirthday(
                    contactId = contactId,
                    displayName = name,
                    photoUri = cursor.getString(photoIdx),
                    month = parsed.month,
                    day = parsed.day,
                    year = parsed.year
                )
            }
        }
        return result
    }

    /**
     * Alle Geburtstage, die (jährlich wiederkehrend) in den Zeitraum [fromEpochDay]..[toEpochDay]
     * fallen, als konkretes Datum des jeweiligen Jahres.
     */
    fun birthdaysInRange(fromEpochDay: Long, toEpochDay: Long): List<Pair<LocalDate, ContactBirthday>> {
        val birthdays = loadBirthdays()
        if (birthdays.isEmpty()) return emptyList()

        val from = LocalDate.ofEpochDay(fromEpochDay)
        val to = LocalDate.ofEpochDay(toEpochDay)
        val out = mutableListOf<Pair<LocalDate, ContactBirthday>>()
        for (year in from.year..to.year) {
            for (birthday in birthdays) {
                val date = dateInYear(birthday, year) ?: continue
                if (!date.isBefore(from) && !date.isAfter(to)) out += date to birthday
            }
        }
        return out
    }

    private data class ParsedBirthday(val month: Int, val day: Int, val year: Int?)

    /**
     * Android speichert Geburtstage als [Event.START_DATE] entweder mit Jahr ("yyyy-MM-dd") oder
     * ohne ("--MM-dd"). Beide Formen werden unterstützt; alles andere wird ignoriert.
     */
    private fun parseBirthday(raw: String): ParsedBirthday? {
        val value = raw.trim()
        return runCatching {
            if (value.startsWith("--")) {
                // --MM-dd
                val parts = value.removePrefix("--").split("-")
                if (parts.size < 2) return null
                ParsedBirthday(month = parts[0].toInt(), day = parts[1].toInt(), year = null)
            } else {
                val parts = value.split("-")
                if (parts.size < 3) return null
                ParsedBirthday(year = parts[0].toInt(), month = parts[1].toInt(), day = parts[2].toInt())
            }
        }.getOrNull()?.takeIf { it.month in 1..12 && it.day in 1..31 }
    }

    /** Das Geburtstagsdatum im Jahr [year]; der 29.02. fällt in Nicht-Schaltjahren auf den 28.02. */
    private fun dateInYear(birthday: ContactBirthday, year: Int): LocalDate? = runCatching {
        if (birthday.month == 2 && birthday.day == 29 && !java.time.Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            LocalDate.of(year, birthday.month, birthday.day)
        }
    }.getOrNull()
}
