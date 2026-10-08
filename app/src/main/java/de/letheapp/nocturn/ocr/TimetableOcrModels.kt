package de.letheapp.nocturn.ocr

/**
 * Eine per OCR aus einem Foto erkannte Zeile mit Zeitangabe, bevor der Nutzer sie vor der
 * Übernahme noch korrigieren kann (siehe CLAUDE.md Kernfunktion 5, Foto-Import).
 */
data class ParsedScheduleRow(
    val id: Long,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val colorArgb: Int,
    /** 0 = Montag .. 6 = Sonntag. Nur im Schüler/Student-Modus relevant. */
    val weekday: Int = 0,
    val included: Boolean = true
) {
    val durationMinutes: Int
        get() {
            val raw = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
            return if (raw <= 0) raw + 24 * 60 else raw
        }
}
