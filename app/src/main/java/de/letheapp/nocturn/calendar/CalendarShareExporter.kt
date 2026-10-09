package de.letheapp.nocturn.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Baut aus den in der Monatsansicht bereits geladenen Einträgen (Schichten, Termine,
 * Feiertage/Ferien) eine einzelne ICS-Datei mit einem All-Day-VEVENT pro Eintrag. Grundlage für
 * den "Teilen"-Button in der Kalender-Topbar: Teilen läuft bewusst über Androids eigenes
 * Teilen-Menü (Bluetooth, Messenger, E-Mail, ...) statt über einen eigenen Server — passend zur
 * in CLAUDE.md festgelegten Anforderung, dass "Kalender teilen" serverunabhängig direkt von
 * App-Nutzer zu App-Nutzer funktionieren muss.
 */
object CalendarShareExporter {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun buildIcs(month: YearMonth, entriesByDay: Map<LocalDate, List<CalendarDayEntry>>): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//letheapp.de//NocTurn//DE"
        )
        val stamp = STAMP_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC))
        var index = 0

        entriesByDay.toSortedMap().forEach { (date, entries) ->
            entries.forEach { entry ->
                index++
                lines += "BEGIN:VEVENT"
                lines += "UID:nocturn-share-$month-$index@letheapp.de"
                lines += "DTSTAMP:$stamp"
                lines += "DTSTART;VALUE=DATE:${DATE_FORMAT.format(date)}"
                lines += "DTEND;VALUE=DATE:${DATE_FORMAT.format(date.plusDays(1))}"
                lines += "SUMMARY:${escape(entry.label)}"
                lines += "END:VEVENT"
            }
        }

        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
