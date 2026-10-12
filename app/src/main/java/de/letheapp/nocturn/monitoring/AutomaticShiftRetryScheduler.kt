package de.letheapp.nocturn.monitoring

import de.letheapp.nocturn.alarm.AlarmScheduler
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.repository.AlarmRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Mindestabstand zum Wieder-Klingeln, damit nicht quasi sofort erneut geklingelt wird. */
private const val MIN_RETRY_GAP_SECONDS = 90L

/**
 * Für automatische Schicht-Wecker (siehe [de.letheapp.nocturn.calendar.ShiftWakeupAlarmSyncer]):
 * wird das Notfall-Puzzle gelöst, OHNE dass echte Bewegung erkannt wurde, ist das kein
 * Freifahrtschein — der Wecker klingelt innerhalb des an der Schicht konfigurierten Fensters
 * ("Frühestes Wecken" bis "Spätestens aufstehen", siehe [de.letheapp.nocturn.data.db.ShiftEntity])
 * zu einem zufälligen späteren Zeitpunkt noch einmal, als wäre es das erste Mal (siehe
 * [AutoShiftRetryService]). Ist das Fenster bereits vorbei (spätestens der Schichtbeginn selbst),
 * wird nicht mehr automatisch erneut geklingelt — das gelöste Puzzle beendet den Alarm dann wie
 * gewohnt endgültig.
 */
@Singleton
class AutomaticShiftRetryScheduler @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val shiftDao: ShiftDao,
    private val alarmScheduler: AlarmScheduler
) {
    suspend fun scheduleRetryIfApplicable(alarmId: Long) {
        val alarm = alarmRepository.getById(alarmId) ?: return
        val epochDay = alarm.autoShiftWakeupEpochDay ?: return
        val shiftId = alarm.autoShiftWakeupShiftId ?: return
        val shift = shiftDao.getById(shiftId) ?: return

        val zone = ZoneId.systemDefault()
        val shiftStart = LocalDate.ofEpochDay(epochDay).atTime(shift.startHour, shift.startMinute)
        val deadline = shiftStart.minusMinutes(shift.latestWakeupOffsetMinutes.toLong())
        val earliest = shiftStart.minusMinutes(shift.earliestWakeOffsetMinutes.toLong())
        // Schichtbeginn selbst als harte Obergrenze gegen inkonsistente Offset-Konfiguration.
        val hardEnd = minOf(deadline, shiftStart)

        val now = LocalDateTime.now(zone)
        if (!hardEnd.isAfter(now)) return

        val lowerBound = maxOf(now.plusSeconds(MIN_RETRY_GAP_SECONDS), earliest)
        val retryAt = if (!hardEnd.isAfter(lowerBound)) {
            lowerBound
        } else {
            val lowerMillis = lowerBound.atZone(zone).toInstant().toEpochMilli()
            val upperMillis = hardEnd.atZone(zone).toInstant().toEpochMilli()
            LocalDateTime.ofInstant(Instant.ofEpochMilli(Random.nextLong(lowerMillis, upperMillis + 1)), zone)
        }

        alarmScheduler.scheduleAutomaticRetry(alarmId, retryAt.atZone(zone).toInstant().toEpochMilli())
    }
}
