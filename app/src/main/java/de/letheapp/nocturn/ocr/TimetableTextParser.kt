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

    fun parse(rawText: String): List<ParsedScheduleRow> {
        var nextId = 0L
        return rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
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
}
