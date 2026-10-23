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
        val showAsBusy: Boolean = true,
        /** Aus VALARM-Blöcken gelesene Erinnerungs-Vorlaufzeiten (Minuten vor DTSTART). */
        val reminderMinutesBefore: List<Int> = emptyList(),
        /** Aus ATTACH-Properties gelesene, Base64-kodierte Anhänge. */
        val attachments: List<ParsedAttachment> = emptyList()
    )

    /** Ein Anhang, wie er als Base64-Rohdaten in einer ATTACH-Property steht (siehe RFC 5545 3.8.1.1). */
    data class ParsedAttachment(val fileName: String, val mimeType: String?, val base64Data: String)

    /** Eingabe für [buildEvent], um einen Anhang als eingebettete ATTACH-Property zu schreiben. */
    data class IcsAttachment(val fileName: String, val mimeType: String?, val base64Data: String)

    fun buildEvent(
        uid: String,
        summary: String,
        location: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        allDay: Boolean,
        showAsBusy: Boolean = true,
        reminderMinutesBefore: List<Int> = emptyList(),
        attachments: List<IcsAttachment> = emptyList()
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
        for (minutes in reminderMinutesBefore) {
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${escape(summary)}"
            lines += "TRIGGER:-PT${minutes}M"
            lines += "END:VALARM"
        }
        for (attachment in attachments) {
            val fmtType = attachment.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            lines += foldLine(
                "ATTACH;ENCODING=BASE64;VALUE=BINARY;FMTTYPE=$fmtType;X-FILENAME=${escape(attachment.fileName)}:${attachment.base64Data}"
            )
        }
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
        val reminders = mutableListOf<Int>()
        val attachments = mutableListOf<ParsedAttachment>()
        var insideAlarm = false
        var alarmTriggerMinutes: Int? = null

        for (line in unfold(ics)) {
            when {
                line == "BEGIN:VALARM" -> {
                    insideAlarm = true
                    alarmTriggerMinutes = null
                }
                line == "END:VALARM" -> {
                    insideAlarm = false
                    alarmTriggerMinutes?.let { reminders += it }
                }
                insideAlarm -> {
                    if (line.startsWith("TRIGGER")) {
                        val value = line.substringAfter(':', "")
                        alarmTriggerMinutes = parseTriggerMinutesBefore(value)
                    }
                }
                line.isBlank() || !line.contains(':') -> Unit
                else -> {
                    val colonIndex = line.indexOf(':')
                    val namePart = line.substring(0, colonIndex)
                    val value = line.substring(colonIndex + 1)
                    val semiIndex = namePart.indexOf(';')
                    val name = if (semiIndex >= 0) namePart.substring(0, semiIndex) else namePart
                    val params = if (semiIndex >= 0) namePart.substring(semiIndex + 1) else ""
                    if (name == "ATTACH") {
                        attachments += ParsedAttachment(
                            fileName = extractParam(params, "X-FILENAME")?.let(::unescape) ?: "Anhang",
                            mimeType = extractParam(params, "FMTTYPE"),
                            base64Data = value
                        )
                    } else if (name == "UID" || name == "SUMMARY" || name == "LOCATION" || name == "DESCRIPTION" ||
                        name == "DTSTART" || name == "DTEND" || name == "TRANSP"
                    ) {
                        properties[name] = params to value
                    }
                }
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
            showAsBusy = properties["TRANSP"]?.second != "TRANSPARENT",
            reminderMinutesBefore = reminders,
            attachments = attachments
        )
    }

    /** Liest nur Vorher-Trigger (`-PT15M`, `-P1D`, `-PT1H30M`, ...) — ein Nachher-Trigger passt nicht
     *  zum NocTurn-Konzept "Minuten vor dem Termin" und wird ignoriert. */
    private fun parseTriggerMinutesBefore(raw: String): Int? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("-P")) return null
        val duration = trimmed.removePrefix("-")
        val match = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").matchEntire(duration) ?: return null
        val days = match.groupValues[1].toIntOrNull() ?: 0
        val hours = match.groupValues[2].toIntOrNull() ?: 0
        val minutes = match.groupValues[3].toIntOrNull() ?: 0
        val total = days * 24 * 60 + hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    private fun extractParam(params: String, key: String): String? {
        for (part in params.split(";")) {
            val eq = part.indexOf('=')
            if (eq < 0) continue
            val k = part.substring(0, eq)
            if (!k.equals(key, ignoreCase = true)) continue
            return part.substring(eq + 1).trim('"')
        }
        return null
    }

    /** Faltet eine Zeile nach RFC 5545 (max. 75 Octets, Fortsetzung mit einem führenden Leerzeichen). */
    private fun foldLine(line: String): String {
        if (line.length <= 75) return line
        val sb = StringBuilder()
        var index = 0
        var first = true
        while (index < line.length) {
            val width = if (first) 75 else 74
            val end = minOf(index + width, line.length)
            if (!first) sb.append("\r\n ")
            sb.append(line, index, end)
            index = end
            first = false
        }
        return sb.toString()
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
