package de.letheapp.nocturn.sync

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimaler, selbst gebauter ICS-Reader/-Writer (RFC 5545, Subset: VEVENT mit UID/DTSTART/DTEND/
 * SUMMARY/LOCATION/DESCRIPTION). Bewusst ohne Zusatz-Library (z. B. biweekly/ical4j), da NocTurn
 * nur eigene, einfach strukturierte Events erzeugt und liest — passend zur FOSS-Minimalismus-
 * Leitlinie (keine zusätzliche Abhängigkeit für eine kleine, klar begrenzte Teilmenge des Formats).
 */
object IcsFormat {

    data class ParsedEvent(
        val uid: String,
        val summary: String,
        val location: String,
        val description: String,
        val startEpochMillis: Long,
        val endEpochMillis: Long,
        val allDay: Boolean,
        val showAsBusy: Boolean = true
    )

    fun buildEvent(
        uid: String,
        summary: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        showAsBusy: Boolean = true
    ): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//letheapp.de//NocTurn//DE",
            "BEGIN:VEVENT",
            "UID:${escape(uid)}",
            "DTSTAMP:${formatUtcDateTime(System.currentTimeMillis())}"
        )
        if (allDay) {
            val startDate = Instant.ofEpochMilli(startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = Instant.ofEpochMilli(endEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
            lines += "DTSTART;VALUE=DATE:${DATE_FORMAT.format(startDate)}"
            lines += "DTEND;VALUE=DATE:${DATE_FORMAT.format(endDate)}"
        } else {
            lines += "DTSTART:${formatUtcDateTime(startEpochMillis)}"
            lines += "DTEND:${formatUtcDateTime(endEpochMillis)}"
        }
        lines += "SUMMARY:${escape(summary)}"
        if (location.isNotBlank()) lines += "LOCATION:${escape(location)}"
        if (description.isNotBlank()) lines += "DESCRIPTION:${escape(description)}"
        lines += "TRANSP:${if (showAsBusy) "OPAQUE" else "TRANSPARENT"}"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** Liest alle VEVENT-Blöcke einer kompletten .ics-Datei (z. B. ein externer ICS-Kalender-Import,
     *  siehe [de.letheapp.nocturn.calendar.IcsImportRepository]) statt nur des einen Events, das
     *  [parseEvent] erwartet. Wiederholungsregeln (RRULE) werden bewusst nicht expandiert — importiert
     *  wird nur das jeweils erste/einzelne Vorkommen jedes VEVENT-Blocks (FOSS-Minimalismus: kein
     *  eigener RRULE-Expander für einen Import-Nebenpfad). */
    fun parseEvents(ics: String): List<ParsedEvent> {
        val blocks = mutableListOf<String>()
        var current: MutableList<String>? = null
        for (line in unfold(ics)) {
            when {
                line == "BEGIN:VEVENT" -> current = mutableListOf(line)
                line == "END:VEVENT" -> {
                    current?.let {
                        it.add(line)
                        blocks += it.joinToString("\r\n")
                    }
                    current = null
                }
                current != null -> current.add(line)
            }
        }
        return blocks.mapNotNull { parseEvent(it) }
    }

    fun parseEvent(ics: String): ParsedEvent? {
        val properties = linkedMapOf<String, Pair<String, String>>() // Name -> (Parameter, Wert)
        for (line in unfold(ics)) {
            if (line.isBlank() || !line.contains(':')) continue
            val colonIndex = line.indexOf(':')
            val namePart = line.substring(0, colonIndex)
            val value = line.substring(colonIndex + 1)
            val semiIndex = namePart.indexOf(';')
            val name = if (semiIndex >= 0) namePart.substring(0, semiIndex) else namePart
            val params = if (semiIndex >= 0) namePart.substring(semiIndex + 1) else ""
            if (name == "UID" || name == "SUMMARY" || name == "LOCATION" || name == "DESCRIPTION" ||
                name == "DTSTART" || name == "DTEND" || name == "TRANSP"
            ) {
                properties[name] = params to value
            }
        }

        val uid = properties["UID"]?.second ?: return null
        val dtStart = properties["DTSTART"] ?: return null
        val dtEnd = properties["DTEND"]
        val allDay = dtStart.first.contains("VALUE=DATE") && !dtStart.first.contains("VALUE=DATE-TIME")

        val startMillis: Long
        val endMillis: Long
        if (allDay) {
            val startDate = LocalDate.parse(dtStart.second, DATE_FORMAT)
            val endDate = dtEnd?.second?.let { LocalDate.parse(it, DATE_FORMAT) } ?: startDate.plusDays(1)
            startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            endMillis = endDate.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            startMillis = parseDateTime(dtStart.second)
            endMillis = dtEnd?.second?.let { parseDateTime(it) } ?: startMillis
        }

        return ParsedEvent(
            uid = uid,
            summary = unescape(properties["SUMMARY"]?.second.orEmpty()),
            location = unescape(properties["LOCATION"]?.second.orEmpty()),
            description = unescape(properties["DESCRIPTION"]?.second.orEmpty()),
            startEpochMillis = startMillis,
            endEpochMillis = endMillis,
            allDay = allDay,
            showAsBusy = properties["TRANSP"]?.second != "TRANSPARENT"
        )
    }

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun formatUtcDateTime(epochMillis: Long): String =
        LOCAL_DATE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC)) + "Z"

    private fun parseDateTime(value: String): Long =
        if (value.endsWith("Z")) {
            LocalDateTime.parse(value.dropLast(1), LOCAL_DATE_TIME_FORMAT).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            LocalDateTime.parse(value, LOCAL_DATE_TIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

    /** Faltet nach RFC 5545 umgebrochene Zeilen (Fortsetzung beginnt mit Leerzeichen/Tab) wieder zusammen. */
    private fun unfold(ics: String): List<String> {
        val result = mutableListOf<String>()
        for (rawLine in ics.split("\r\n", "\n")) {
            if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + rawLine.substring(1)
            } else {
                result += rawLine
            }
        }
        return result
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
