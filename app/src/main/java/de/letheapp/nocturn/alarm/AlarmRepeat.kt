package de.letheapp.nocturn.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wiederholungsmuster als Bitmaske, Bit-Index = DayOfWeek.value - 1 (Montag = Bit 0).
 */
object AlarmRepeat {
    private val ORDERED_DAYS = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    private val SHORT_LABELS = mapOf(
        DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do", DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )

    fun bitFor(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isSet(repeatDays: Int, day: DayOfWeek): Boolean = (repeatDays and bitFor(day)) != 0

    fun toggle(repeatDays: Int, day: DayOfWeek): Int = repeatDays xor bitFor(day)

    fun activeDays(repeatDays: Int): List<DayOfWeek> = ORDERED_DAYS.filter { isSet(repeatDays, it) }

    fun shortLabel(day: DayOfWeek): String = SHORT_LABELS[day] ?: day.name

    /** Kurzbeschreibung für die Alarm-Karte, z.B. "Jeden Tag", "Mo, Mi, Fr" oder "Einmalig". */
    fun describe(repeatDays: Int): String {
        if (repeatDays == 0) return "Einmalig"
        if (repeatDays == 0b1111111) return "Jeden Tag"
        val weekdays = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4)
        val weekend = (1 shl 5) or (1 shl 6)
        if (repeatDays == weekdays) return "Werktags"
        if (repeatDays == weekend) return "Wochenende"
        return activeDays(repeatDays).joinToString(", ") { shortLabel(it) }
    }

    /**
     * Nächster Zeitpunkt (ab [from]) für die gegebene Uhrzeit, unter Berücksichtigung von
     * [repeatDays]. Bei repeatDays == 0 ist das der nächste Zeitpunkt heute oder morgen.
     */
    fun nextTrigger(
        hour: Int,
        minute: Int,
        repeatDays: Int,
        from: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
    ): LocalDateTime {
        val today = from.toLocalDate()
        val candidateToday = today.atTime(hour, minute)

        if (repeatDays == 0) {
            return if (candidateToday.isAfter(from)) candidateToday else candidateToday.plusDays(1)
        }

        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            val candidate = date.atTime(hour, minute)
            if (isSet(repeatDays, date.dayOfWeek) && candidate.isAfter(from)) {
                return candidate
            }
        }
        // Sollte wegen der 7-Tage-Schleife nie erreicht werden, falls repeatDays != 0.
        return candidateToday.plusDays(7)
    }
}
