package de.letheapp.nocturn.ocr

/**
 * Extrahiert Zeile für Zeile Zeiträume ("06:00-14:00", "8.15 – 9.00" o. Ä.) aus dem von
 * Tesseract erkannten Rohtext eines fotografierten Schicht-/Stundenplans. Pro erkannter Zeile
 * mit Zeitangabe entsteht eine [ParsedScheduleRow]; der Rest der Zeile (abzüglich der
 * Zeitangabe) wird als Bezeichnung übernommen. Zeilen ohne erkennbaren Zeitraum werden
 * verworfen — Tabellen-Spaltenstruktur (z. B. welcher Wochentag) kann aus reinem Fließtext
 * nicht zuverlässig rekonstruiert werden, daher korrigiert/ordnet der Nutzer das Ergebnis vor
 * der Übernahme noch per Hand (bewusste Vereinfachung, siehe PLAN.md).
 */
object TimetableTextParser {
    private val TIME_RANGE_REGEX = Regex(
        """(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})"""
    )
    private val SINGLE_TIME_REGEX = Regex("""^(\d{1,2})[:.](\d{2})$""")
    private val PERIOD_NUMBER_REGEX = Regex("""^\d{1,2}[.,]?$""")

    fun parse(rawText: String): List<ParsedScheduleRow> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        val rangeRows = parseRanges(lines)
        if (rangeRows.isNotEmpty()) return rangeRows

        // Manche Apps/Stundenpläne zeigen links nur die einzelnen Periodengrenzen
        // ("07:45", "08:30", "09:15", ...) statt einer Zeitspanne pro Zeile. Daraus lassen
        // sich zumindest Zeiträume ableiten; als Bezeichnung dient der Text zwischen zwei
        // Zeitangaben (bereinigt um reine Perioden-Nummerierungen wie "1.").
        return parseSingleTimeBoundaries(lines)
    }

    private fun parseRanges(lines: List<String>): List<ParsedScheduleRow> {
        var nextId = 0L
        return lines.mapNotNull { line ->
            val match = TIME_RANGE_REGEX.find(line) ?: return@mapNotNull null
            val (startHourRaw, startMinuteRaw, endHourRaw, endMinuteRaw) = match.destructured
            val id = nextId++
            val label = line.removeRange(match.range)
                .trim(' ', '-', '–', '—', ':', '.', ',')
                .ifBlank { "Eintrag ${id + 1}" }

            ParsedScheduleRow(
                id = id,
                label = label,
                startHour = startHourRaw.toInt().coerceIn(0, 23),
                startMinute = startMinuteRaw.toInt().coerceIn(0, 59),
                endHour = endHourRaw.toInt().coerceIn(0, 23),
                endMinute = endMinuteRaw.toInt().coerceIn(0, 59),
                colorArgb = 0
            )
        }
    }

    private fun parseSingleTimeBoundaries(lines: List<String>): List<ParsedScheduleRow> {
        data class TimeMarker(val lineIndex: Int, val hour: Int, val minute: Int)

        val markers = lines.mapIndexedNotNull { index, line ->
            SINGLE_TIME_REGEX.find(line)?.let { match ->
                val (hourRaw, minuteRaw) = match.destructured
                TimeMarker(index, hourRaw.toInt().coerceIn(0, 23), minuteRaw.toInt().coerceIn(0, 59))
            }
        }
        if (markers.size < 2) return emptyList()

        var nextId = 0L
        return markers.zipWithNext().mapNotNull { (start, end) ->
            val labelLines = lines.subList(start.lineIndex + 1, end.lineIndex)
                .filterNot { PERIOD_NUMBER_REGEX.matches(it) }
            val id = nextId++
            val label = labelLines.joinToString(" ").trim().ifBlank { return@mapNotNull null }

            ParsedScheduleRow(
                id = id,
                label = label,
                startHour = start.hour,
                startMinute = start.minute,
                endHour = end.hour,
                endMinute = end.minute,
                colorArgb = 0
            )
        }
    }
}
