package de.letheapp.nocturn.ocr

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ParsedAppointmentResult(
    val title: String,
    val location: String,
    val description: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val textRecognized: Boolean
)

/**
 * Deterministische, LLM-freie Termin-Extraktion aus fotografierten Briefen/Terminzetteln
 * (CLAUDE.md Kernfunktion 6, "Brief-/Terminzettel-Foto-Import"): Regex-/Heuristik-Regelwerk für
 * deutsche Datums-/Uhrzeitformate, Betreff-/Ort-Erkennung sowie ein einfaches Scoring, falls
 * mehrere Datumsangaben im Text vorkommen (z. B. Ausstellungsdatum des Briefes vs. der
 * eigentliche Termin). Das Ergebnis ist immer nur ein Vorschlag — der Nutzer bestätigt/korrigiert
 * ihn danach in der normalen Termin-Kontrollmaske ([de.letheapp.nocturn.calendar.AddAppointmentScreen]).
 */
object AppointmentTextParser {

    private val MONTHS_LONG = mapOf(
        "januar" to 1, "februar" to 2, "märz" to 3, "april" to 4, "mai" to 5, "juni" to 6,
        "juli" to 7, "august" to 8, "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12
    )
    private val MONTHS_SHORT = mapOf(
        "jan" to 1, "feb" to 2, "mär" to 3, "mrz" to 3, "apr" to 4, "mai" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "okt" to 10, "nov" to 11, "dez" to 12
    )
    private val WEEKDAYS = mapOf(
        "montag" to DayOfWeek.MONDAY, "mo" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY, "di" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY, "mi" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY, "do" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY, "fr" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY, "sa" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY, "so" to DayOfWeek.SUNDAY
    )

    private val DATE_NUMERIC = Regex("""\b(0?[1-9]|[12]\d|3[01])[.\/-](0?[1-9]|1[0-2])[.\/-](\d{4}|\d{2})\b""")
    private val DATE_LONG_MONTH = Regex(
        """\b(0?[1-9]|[12]\d|3[01])\.?\s*(Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\s*(\d{4})?\b""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_SHORT_MONTH = Regex(
        """\b(0?[1-9]|[12]\d|3[01])\.?\s*(Jan|Feb|Mär|Mrz|Apr|Mai|Jun|Jul|Aug|Sep|Sept|Okt|Nov|Dez)\.?\s*(\d{4}|\d{2})?\b""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_RELATIVE_DAY = Regex("""\b(heute|übermorgen|morgen|vorgestern)\b""", RegexOption.IGNORE_CASE)
    private val DATE_RELATIVE_WEEKDAY = Regex(
        """\b(?:am|kommenden|nächsten|diesen)\s+(?:kommenden|nächsten)?\s*(Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag|Mo|Di|Mi|Do|Fr|Sa|So)\b""",
        RegexOption.IGNORE_CASE
    )

    private val TIME_RANGE = Regex(
        """\b(?:von|zwischen)\s+([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:Uhr)?\s*(?:bis|und|-)\s*([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:Uhr)?""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_PREFIXED = Regex(
        """\b(?:um|ab|spätestens|gegen|ca\.|circa|ungefähr|pünktlich\s+um)\s+([01]?\d|2[0-3])[:.]([0-5]\d)\s*(?:Uhr)?""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_COLON = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\s*(?:Uhr|uhr|h)?\b""")
    private val TIME_DOT = Regex("""\b([01]?\d|2[0-3])\.([0-5]\d)\s*(?:Uhr|uhr|h)\b""")
    private val DURATION = Regex(
        """(?:Dauer|Zeitfenster)[:\s]*(?:ca\.\s*)?(\d+)\s*(Minuten|Min\.?|Stunden|Std\.?)""",
        RegexOption.IGNORE_CASE
    )

    private val SUBJECT_LINE = Regex("""(?i)^(?:Betreff|Betr\.?|Thema|Anlass|Grund|Gegenstand)\s*:?\s*(.+)$""")
    private val APPOINTMENT_NOUN = Regex(
        """(?i)\b(Besprechung|Kontrolltermin|Untersuchung|Beratung|Vorstellung|Termin|Wartung|Prüfung|Inspektion|Impftermin|Einladung|Gerichtsverhandlung|Sitzung|Vollversammlung|Hauptuntersuchung|Zahnreinigung|Vorsorgeuntersuchung)\b"""
    )
    private val PLZ_CITY = Regex("""\b(\d{5})\s+([A-ZÄÖÜ][a-zäöüß]+(?:[\s-][A-ZÄÖÜ][a-zäöüß]+)*)\b""")
    private val STREET_LINE = Regex(
        """(?i)\b[A-ZÄÖÜ][a-zäöüß\-]+(?:['\s][A-ZÄÖÜ][a-zäöüß\-]+)*\s*(?:str\.?|straße|weg|allee|platz|ring|ufer|gasse|damm|chaussee)\s+\d+[a-zA-Z]?(?:/\d+)?\b"""
    )
    private val LOCATION_KEYWORD = Regex("""(?i)^(?:Ort|Treffpunkt|Veranstaltungsort|Adresse|Anschrift)\s*:?\s*(.+)$""")
    private val ALL_DAY_KEYWORD = Regex("""(?i)\b(ganztägig|ganztags|ganzer\s+Tag|ohne\s+Uhrzeit)\b""")
    private val NOTES_KEYWORD = Regex("""(?i)^(?:Bitte\s+mitbringen|Mitzubringen\s+sind|Erforderliche\s+Unterlagen|Hinweis|Wichtig)\s*:?\s*(.+)$""")
    private val DATE_CONTEXT_ANCHOR = Regex("""(?i)\b(?:Termin|vereinbart|einbestellt|stattfinden|gültig\s+bis|einladung)\b""")

    private data class DateCandidate(val date: LocalDate, val index: Int, val length: Int)

    fun parse(rawTextInput: String, now: LocalDate = LocalDate.now()): ParsedAppointmentResult {
        val text = normalizeOcrArtifacts(rawTextInput)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val textRecognized = lines.isNotEmpty()

        val allDay = ALL_DAY_KEYWORD.containsMatchIn(text)
        val date = findBestDate(text, now) ?: now
        val timeInfo = if (allDay) null else findTime(text)

        val zone = ZoneId.systemDefault()
        val beginTime = timeInfo?.first ?: LocalTime.of(8, 0)
        val beginDateTime = date.atTime(beginTime)
        val endDateTime = when (val endTime = timeInfo?.second) {
            null -> beginDateTime.plusHours(1)
            else -> date.atTime(endTime)
        }

        return ParsedAppointmentResult(
            title = findTitle(lines) ?: "Termin",
            location = findLocation(lines) ?: "",
            description = findNotes(lines) ?: "",
            beginMillis = beginDateTime.atZone(zone).toInstant().toEpochMilli(),
            endMillis = endDateTime.atZone(zone).toInstant().toEpochMilli(),
            allDay = allDay,
            textRecognized = textRecognized
        )
    }

    /** Korrigiert typische OCR-Verwechslungen, bevor die eigentlichen Muster darauf laufen. */
    private fun normalizeOcrArtifacts(text: String): String {
        var result = text
        result = result.replace(Regex("""(\d{1,2})[;,](\d{2})(\s*(?:Uhr|h)\b)""", RegexOption.IGNORE_CASE)) { m ->
            "${m.groupValues[1]}:${m.groupValues[2]}${m.groupValues[3]}"
        }
        result = result.replace(Regex("""(\d{1,2}[:.])[Oo]{2}\b""")) { m -> "${m.groupValues[1]}00" }
        result = result.replace(Regex("""(\p{L}+)3e\b""")) { m -> "${m.groupValues[1]}ße" }
        return result
    }

    private fun findBestDate(text: String, now: LocalDate): LocalDate? {
        val candidates = mutableListOf<DateCandidate>()

        DATE_NUMERIC.findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = m.groupValues[2].toIntOrNull() ?: return@forEach
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) it + 2000 else it } ?: return@forEach
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let {
                candidates += DateCandidate(it, m.range.first, m.value.length)
            }
        }
        DATE_LONG_MONTH.findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = MONTHS_LONG[m.groupValues[2].lowercase()] ?: return@forEach
            addWithResolvedYear(candidates, m, day, month, m.groupValues[3], now)
        }
        DATE_SHORT_MONTH.findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@forEach
            val month = MONTHS_SHORT[m.groupValues[2].lowercase()] ?: return@forEach
            addWithResolvedYear(candidates, m, day, month, m.groupValues[3], now)
        }
        DATE_RELATIVE_DAY.findAll(text).forEach { m ->
            val date = when (m.value.lowercase()) {
                "heute" -> now
                "morgen" -> now.plusDays(1)
                "übermorgen" -> now.plusDays(2)
                "vorgestern" -> now.minusDays(2)
                else -> null
            }
            if (date != null) candidates += DateCandidate(date, m.range.first, m.value.length)
        }
        DATE_RELATIVE_WEEKDAY.findAll(text).forEach { m ->
            val weekday = WEEKDAYS[m.groupValues[1].lowercase()] ?: return@forEach
            var date = now.plusDays(1)
            while (date.dayOfWeek != weekday) date = date.plusDays(1)
            candidates += DateCandidate(date, m.range.first, m.value.length)
        }

        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { scoreCandidate(it, text, now) }?.date
    }

    private fun addWithResolvedYear(
        candidates: MutableList<DateCandidate>,
        match: MatchResult,
        day: Int,
        month: Int,
        yearRaw: String,
        now: LocalDate
    ) {
        val yearGiven = yearRaw.toIntOrNull()?.let { if (it < 100) it + 2000 else it }
        val year = yearGiven ?: now.year
        var date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return
        // Kein Jahr angegeben und das Ergebnis liegt deutlich in der Vergangenheit: der Brief
        // meint vermutlich den gleichen Tag im kommenden Jahr (z. B. Dezember-Brief für Januar-Termin).
        if (yearGiven == null && date.isBefore(now.minusMonths(3))) {
            date = date.plusYears(1)
        }
        candidates += DateCandidate(date, match.range.first, match.value.length)
    }

    private fun scoreCandidate(candidate: DateCandidate, text: String, now: LocalDate): Int {
        var score = 0
        val windowStart = (candidate.index - 30).coerceAtLeast(0)
        val anchorWindowEnd = (candidate.index + candidate.length + 30).coerceAtMost(text.length)
        if (DATE_CONTEXT_ANCHOR.containsMatchIn(text.substring(windowStart, anchorWindowEnd))) score += 50

        score += if (!candidate.date.isBefore(now)) 30 else -100

        val timeWindowEnd = (candidate.index + candidate.length + 40).coerceAtMost(text.length)
        val timeWindow = text.substring(windowStart, timeWindowEnd)
        if (TIME_COLON.containsMatchIn(timeWindow) || TIME_DOT.containsMatchIn(timeWindow) || TIME_PREFIXED.containsMatchIn(timeWindow)) {
            score += 40
        }
        return score
    }

    private fun findTime(text: String): Pair<LocalTime, LocalTime?>? {
        TIME_RANGE.find(text)?.let { m ->
            val (sh, sm, eh, em) = m.destructured
            return LocalTime.of(sh.toInt().coerceIn(0, 23), sm.toInt().coerceIn(0, 59)) to
                LocalTime.of(eh.toInt().coerceIn(0, 23), em.toInt().coerceIn(0, 59))
        }
        val single = TIME_PREFIXED.find(text) ?: TIME_COLON.find(text) ?: TIME_DOT.find(text) ?: return null
        val start = LocalTime.of(
            single.groupValues[1].toInt().coerceIn(0, 23),
            single.groupValues[2].toInt().coerceIn(0, 59)
        )
        val end = DURATION.find(text)?.let { d ->
            val amount = d.groupValues[1].toIntOrNull() ?: return@let null
            val unit = d.groupValues[2].lowercase()
            val minutes = if (unit.startsWith("stun") || unit.startsWith("std")) amount * 60 else amount
            start.plusMinutes(minutes.toLong())
        }
        return start to end
    }

    private fun findTitle(lines: List<String>): String? {
        lines.forEach { line -> SUBJECT_LINE.find(line)?.let { return it.groupValues[1].trim().take(80) } }
        lines.forEach { line -> if (APPOINTMENT_NOUN.containsMatchIn(line)) return line.trim().take(80) }
        return lines.firstOrNull()?.take(80)
    }

    private fun findLocation(lines: List<String>): String? {
        lines.forEach { line -> LOCATION_KEYWORD.find(line)?.let { return it.groupValues[1].trim().take(100) } }
        lines.forEachIndexed { index, line ->
            val plzMatch = PLZ_CITY.find(line) ?: return@forEachIndexed
            val cityPart = "${plzMatch.groupValues[1]} ${plzMatch.groupValues[2]}"
            val streetLine = lines.getOrNull(index - 1)?.takeIf { STREET_LINE.containsMatchIn(it) }
            return if (streetLine != null) "$streetLine, $cityPart" else cityPart
        }
        return null
    }

    private fun findNotes(lines: List<String>): String? {
        val notes = lines.mapNotNull { line -> NOTES_KEYWORD.find(line)?.groupValues?.get(1)?.trim() }
        return notes.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}
