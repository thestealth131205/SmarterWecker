package de.letheapp.nocturn.alarm

import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.repository.CalendarRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Obergrenze für die Tage-Suche nach dem nächsten Schicht-/Unterrichtstermin (Sicherheitsnetz). */
private const val MAX_LOOKAHEAD_DAYS = 400

/**
 * Ein konkreter Schicht- oder Unterrichtsstunden-Termin inkl. der dafür konfigurierten
 * Weckzeit-Abstände ("frühestes Wecken" / "spätestens aufstehen", siehe CLAUDE.md
 * Kernfunktion 5). Ein Offset von 0 Minuten entspricht dem alten Verhalten: Wecken bzw.
 * Aufstehen-müssen genau zum Schicht-/Unterrichtsbeginn.
 */
data class WakeOccurrence(
    val start: LocalDateTime,
    val earliestWakeOffsetMinutes: Int,
    val latestWakeupOffsetMinutes: Int
) {
    val wakeTime: LocalDateTime get() = start.minusMinutes(earliestWakeOffsetMinutes.toLong())
    val deadline: LocalDateTime get() = start.minusMinutes(latestWakeupOffsetMinutes.toLong())
}

/**
 * Leitet aus einer Rotation (Schichtarbeit) oder einem Wochenstundenplan (Schüler/Student-Modus)
 * konkrete Weck- bzw. Aufsteh-Deadline-Zeitpunkte ab. Wird sowohl von [AlarmScheduler] (nächster
 * Klingel-Zeitpunkt) als auch von der Stop-Weiterüberwachung (Aufsteh-Deadline des gerade
 * ausgelösten Alarms) verwendet, damit beide Seiten dieselbe Logik teilen.
 */
@Singleton
class WakeWindowResolver @Inject constructor(
    private val calendarRepository: CalendarRepository
) {

    /** Nächster zukünftiger Termin ab [from] (exklusiv bzgl. Weckzeit), falls der Alarm verknüpft ist. */
    suspend fun nextOccurrence(alarm: AlarmEntity, from: LocalDateTime): WakeOccurrence? {
        val rotationId = alarm.rotationId
        val timetableJobId = alarm.timetableJobId
        return when {
            rotationId != null -> nextRotationOccurrence(rotationId, from)
            timetableJobId != null -> nextTimetableOccurrence(timetableJobId, from)
            else -> null
        }
    }

    /**
     * Termin, der zur zuletzt ausgelösten Klingel-Phase gehört (heute oder gestern, falls der
     * Alarm über Mitternacht reicht) — Grundlage für die Aufsteh-Deadline nach "Stop".
     */
    suspend fun currentDeadline(alarm: AlarmEntity, now: LocalDateTime): LocalDateTime? {
        val rotationId = alarm.rotationId
        val timetableJobId = alarm.timetableJobId
        if (rotationId == null && timetableJobId == null) return null

        for (dayOffset in 0 downTo -1) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val occurrence = if (rotationId != null) {
                occurrenceFromRotationDate(rotationId, date)
            } else {
                occurrenceFromTimetableDate(timetableJobId!!, date)
            }
            if (occurrence != null) return occurrence.deadline
        }
        return null
    }

    private suspend fun occurrenceFromRotationDate(rotationId: Long, date: LocalDate): WakeOccurrence? {
        val entry = calendarRepository.getCalendarEntryForDate(date.toEpochDay())
            ?.takeIf { it.rotationId == rotationId }
            ?: return null
        val shift = entry.shiftId?.let { calendarRepository.getShift(it) } ?: return null
        return WakeOccurrence(
            start = date.atTime(shift.startHour, shift.startMinute),
            earliestWakeOffsetMinutes = shift.earliestWakeOffsetMinutes,
            latestWakeupOffsetMinutes = shift.latestWakeupOffsetMinutes
        )
    }

    private suspend fun occurrenceFromTimetableDate(jobId: Long, date: LocalDate): WakeOccurrence? {
        val weekday = date.dayOfWeek.value - 1
        val lesson = calendarRepository.getFirstLessonForWeekday(jobId, weekday) ?: return null
        return WakeOccurrence(
            start = date.atTime(lesson.startHour, lesson.startMinute),
            earliestWakeOffsetMinutes = lesson.earliestWakeOffsetMinutes,
            latestWakeupOffsetMinutes = lesson.latestWakeupOffsetMinutes
        )
    }

    private suspend fun nextRotationOccurrence(rotationId: Long, from: LocalDateTime): WakeOccurrence? {
        var fromEpochDay = from.toLocalDate().toEpochDay()
        repeat(MAX_LOOKAHEAD_DAYS) {
            val entry = calendarRepository.getNextCalendarEntryWithShift(rotationId, fromEpochDay) ?: return null
            val shift = entry.shiftId?.let { calendarRepository.getShift(it) }
            if (shift == null) {
                fromEpochDay = entry.dateEpochDay + 1
                return@repeat
            }
            val occurrence = WakeOccurrence(
                start = LocalDate.ofEpochDay(entry.dateEpochDay).atTime(shift.startHour, shift.startMinute),
                earliestWakeOffsetMinutes = shift.earliestWakeOffsetMinutes,
                latestWakeupOffsetMinutes = shift.latestWakeupOffsetMinutes
            )
            if (occurrence.wakeTime.isAfter(from)) return occurrence
            fromEpochDay = entry.dateEpochDay + 1
        }
        return null
    }

    private suspend fun nextTimetableOccurrence(jobId: Long, from: LocalDateTime): WakeOccurrence? {
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            val occurrence = occurrenceFromTimetableDate(jobId, date) ?: continue
            if (occurrence.wakeTime.isAfter(from)) return occurrence
        }
        return null
    }
}
