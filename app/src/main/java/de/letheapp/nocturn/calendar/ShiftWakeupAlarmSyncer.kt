package de.letheapp.nocturn.calendar

import de.letheapp.nocturn.alarm.AlarmScheduler
import de.letheapp.nocturn.data.db.AlarmEntity
import de.letheapp.nocturn.data.db.CalendarEntryDao
import de.letheapp.nocturn.data.db.ManualShiftEntryDao
import de.letheapp.nocturn.data.db.ShiftDao
import de.letheapp.nocturn.data.repository.AlarmRepository
import de.letheapp.nocturn.wear.WearSyncManager
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private data class ShiftOccurrenceCandidate(val shiftId: Long, val dateEpochDay: Long)

/**
 * Hält genau einen automatisch verwalteten Alarm ([AlarmEntity.autoShiftWakeupEpochDay]) aktuell,
 * der immer die Weckzeit der nächsten anstehenden Schicht zeigt, deren Vorlage ein optionales
 * [de.letheapp.nocturn.data.db.ShiftEntity.latestWakeupHour] gesetzt hat (siehe "Schicht
 * anlegen"-Screen, CLAUDE.md Kernfunktion 5) — ganz ohne dass der Nutzer dafür manuell einen Alarm
 * anlegen/verknüpfen muss (anders als die [AlarmEntity.rotationId]-Verknüpfung). Muss nach jeder
 * Kalenderänderung, die Schichten betrifft, erneut aufgerufen werden (Rotation zugewiesen, Schicht
 * gespeichert/gelöscht, Einzel-Schicht eingetragen), sowie beim App-Start/Boot und beim Öffnen des
 * Wecker-Screens, damit der angezeigte Alarm nie veraltet.
 *
 * Bewusst nicht in [de.letheapp.nocturn.data.repository.CalendarRepository] selbst verdrahtet:
 * [AlarmScheduler] hängt über [de.letheapp.nocturn.alarm.WakeWindowResolver] transitiv von
 * CalendarRepository ab — eine Injection hier hinein würde einen Dagger-Abhängigkeitszyklus
 * erzeugen. Aufrufer sind daher die ViewModels, die die kalenderverändernden Repository-Methoden
 * ohnehin schon aufrufen.
 *
 * Dient aus demselben Grund auch als zentraler Anstoßpunkt für [WearSyncManager]: jede
 * Kalenderänderung, die hier einen Lauf auslöst, muss auch der gekoppelten WearOS-Smartwatch
 * nachgereicht werden — [WearSyncManager] hängt nur von [de.letheapp.nocturn.data.repository.CalendarRepository]
 * (keinem der hier riskanten Typen) ab, eine Injection ist also zyklusfrei möglich.
 */
@Singleton
class ShiftWakeupAlarmSyncer @Inject constructor(
    private val shiftDao: ShiftDao,
    private val calendarEntryDao: CalendarEntryDao,
    private val manualShiftEntryDao: ManualShiftEntryDao,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val wearSyncManager: WearSyncManager
) {
    suspend fun sync() {
        wearSyncManager.syncNow()
        val configuredShifts = shiftDao.getAllWithWakeupConfigured()
        val existingAlarm = alarmRepository.getAutoShiftWakeupAlarm()

        if (configuredShifts.isEmpty()) {
            removeExisting(existingAlarm)
            return
        }

        val now = LocalDateTime.now()
        val todayEpochDay = now.toLocalDate().toEpochDay()
        val shiftsById = configuredShifts.associateBy { it.id }
        val shiftIds = shiftsById.keys.toList()

        val candidates = calendarEntryDao.getUpcomingForShiftIds(shiftIds, todayEpochDay)
            .mapNotNull { entry -> entry.shiftId?.let { ShiftOccurrenceCandidate(it, entry.dateEpochDay) } } +
            manualShiftEntryDao.getUpcomingForShiftIds(shiftIds, todayEpochDay)
                .map { ShiftOccurrenceCandidate(it.shiftId, it.dateEpochDay) }

        val next = candidates
            .mapNotNull { candidate ->
                val shift = shiftsById[candidate.shiftId] ?: return@mapNotNull null
                val hour = shift.latestWakeupHour ?: return@mapNotNull null
                val minute = shift.latestWakeupMinute ?: 0
                val wakeAt = LocalDate.ofEpochDay(candidate.dateEpochDay).atTime(hour, minute)
                if (wakeAt.isAfter(now)) Triple(wakeAt, shift, candidate.dateEpochDay) else null
            }
            .minByOrNull { it.first }

        if (next == null) {
            removeExisting(existingAlarm)
            return
        }

        val (wakeAt, shift, dateEpochDay) = next
        val alarm = (existingAlarm ?: AlarmEntity(hour = wakeAt.hour, minute = wakeAt.minute)).copy(
            hour = wakeAt.hour,
            minute = wakeAt.minute,
            enabled = true,
            repeatDays = 0,
            label = "Schicht: ${shift.name}",
            rotationId = null,
            timetableJobId = null,
            autoShiftWakeupEpochDay = dateEpochDay,
            autoShiftWakeupShiftId = shift.id,
            // Dieselben bis zu drei ioBroker-Aktionen wie an der Schicht hinterlegt übernehmen
            // (z. B. bei Nachtschicht einen anderen/helleren Weck-Ablauf als bei Spätschicht,
            // siehe ShiftEntity-Doc-Kommentar) — ein automatischer Schicht-Wecker hat sonst keinen
            // eigenen Bearbeiten-Screen, in dem sich das nachträglich setzen ließe.
            ioBrokerAction1DataPointId = shift.ioBrokerAction1DataPointId,
            ioBrokerAction1DataPointLabel = shift.ioBrokerAction1DataPointLabel,
            ioBrokerAction1Mode = shift.ioBrokerAction1Mode,
            ioBrokerAction1TurnOn = shift.ioBrokerAction1TurnOn,
            ioBrokerAction1RampStep = shift.ioBrokerAction1RampStep,
            ioBrokerAction1RampTarget = shift.ioBrokerAction1RampTarget,
            ioBrokerAction1AutoTurnOff = shift.ioBrokerAction1AutoTurnOff,
            ioBrokerAction2DataPointId = shift.ioBrokerAction2DataPointId,
            ioBrokerAction2DataPointLabel = shift.ioBrokerAction2DataPointLabel,
            ioBrokerAction2Mode = shift.ioBrokerAction2Mode,
            ioBrokerAction2TurnOn = shift.ioBrokerAction2TurnOn,
            ioBrokerAction2RampStep = shift.ioBrokerAction2RampStep,
            ioBrokerAction2RampTarget = shift.ioBrokerAction2RampTarget,
            ioBrokerAction2AutoTurnOff = shift.ioBrokerAction2AutoTurnOff,
            ioBrokerScriptDataPointId = shift.ioBrokerScriptDataPointId,
            ioBrokerScriptDataPointLabel = shift.ioBrokerScriptDataPointLabel,
            ioBrokerScriptMode = shift.ioBrokerScriptMode,
            ioBrokerScriptTurnOn = shift.ioBrokerScriptTurnOn,
            ioBrokerScriptRampStep = shift.ioBrokerScriptRampStep,
            ioBrokerScriptRampTarget = shift.ioBrokerScriptRampTarget,
            ioBrokerScriptAutoTurnOff = shift.ioBrokerScriptAutoTurnOff
        )
        val id = alarmRepository.save(alarm)
        alarmScheduler.schedule(alarm.copy(id = id))
    }

    private suspend fun removeExisting(existingAlarm: AlarmEntity?) {
        if (existingAlarm == null) return
        alarmScheduler.cancel(existingAlarm.id)
        alarmRepository.delete(existingAlarm)
    }
}
