package de.letheapp.nocturn.wear

import de.letheapp.nocturn.calendar.BIRTHDAY_COLOR_ARGB
import de.letheapp.nocturn.calendar.PUBLIC_HOLIDAY_COLOR_ARGB
import de.letheapp.nocturn.calendar.SCHOOL_HOLIDAY_COLOR_ARGB
import de.letheapp.nocturn.contacts.ContactBirthdayRepository
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.db.WearSourceType
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baut die Liste der an die Smartwatch zu übertragenden Kalendereinträge der nächsten
 * [LOOKAHEAD_DAYS] Tage — gefiltert nach den in der WearOS-Verwaltung aktivierten Quellen
 * ([WearVisibleSourceRepository]), unabhängig von den Handy-seitigen Anzeige-Einstellungen
 * (z. B. kann eine Rotation auf dem Handy sichtbar, auf der Uhr aber bewusst ausgeblendet sein).
 */
@Singleton
class WearAgendaBuilder @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val contactBirthdayRepository: ContactBirthdayRepository,
    private val visibleSourceRepository: WearVisibleSourceRepository
) {
    companion object {
        private const val LOOKAHEAD_DAYS = 14L
    }

    suspend fun build(): List<WearAgendaEntry> {
        val today = LocalDate.now().toEpochDay()
        val toDay = today + LOOKAHEAD_DAYS

        val enabledRotations = visibleSourceRepository.getEnabledKeysOnce(WearSourceType.ROTATION)
        val enabledJobs = visibleSourceRepository.getEnabledKeysOnce(WearSourceType.JOB)
        val shiftsById = calendarRepository.observeAllShifts().first().associateBy { it.id }

        val rotationShiftEntries = if (enabledRotations.isEmpty()) {
            emptyList()
        } else {
            calendarRepository.observeCalendarEntries(today, toDay).first()
                .filter { it.rotationId in enabledRotations }
                .mapNotNull { entry ->
                    val shiftId = entry.shiftId ?: return@mapNotNull null
                    val shift = shiftsById[shiftId] ?: return@mapNotNull null
                    WearAgendaEntry(
                        epochDay = entry.dateEpochDay,
                        label = shift.name,
                        colorArgb = shift.colorArgb,
                        timeLabel = "%02d:%02d".format(shift.startHour, shift.startMinute),
                        type = WearAgendaEntryType.SHIFT
                    )
                }
        }

        val manualShiftEntries = if (enabledJobs.isEmpty()) {
            emptyList()
        } else {
            calendarRepository.observeManualShifts(today, toDay).first()
                .filter { it.jobId in enabledJobs }
                .mapNotNull { entry ->
                    val shift = shiftsById[entry.shiftId] ?: return@mapNotNull null
                    WearAgendaEntry(
                        epochDay = entry.dateEpochDay,
                        label = shift.name,
                        colorArgb = shift.colorArgb,
                        timeLabel = "%02d:%02d".format(shift.startHour, shift.startMinute),
                        type = WearAgendaEntryType.SHIFT
                    )
                }
        }

        val birthdayEntries = if (visibleSourceRepository.isSingletonEnabledOnce(WearSourceType.BIRTHDAYS)) {
            withContext(Dispatchers.IO) { contactBirthdayRepository.birthdaysInRange(today, toDay) }.map { (date, birthday) ->
                WearAgendaEntry(
                    epochDay = date.toEpochDay(),
                    label = "🎂 ${birthday.displayName}",
                    colorArgb = BIRTHDAY_COLOR_ARGB,
                    timeLabel = null,
                    type = WearAgendaEntryType.BIRTHDAY
                )
            }
        } else {
            emptyList()
        }

        val wantsPublicHolidays = visibleSourceRepository.isSingletonEnabledOnce(WearSourceType.PUBLIC_HOLIDAYS)
        val wantsSchoolHolidays = visibleSourceRepository.isSingletonEnabledOnce(WearSourceType.SCHOOL_HOLIDAYS)
        val holidaySettings = holidayRepository.observeSettings().first()

        val holidayEntries = if ((wantsPublicHolidays || wantsSchoolHolidays) && holidaySettings != null) {
            holidayRepository.observeHolidays(holidaySettings.subdivisionCode, today, toDay).first()
                .filter {
                    (it.type == HolidayType.PUBLIC.name && wantsPublicHolidays) ||
                        (it.type == HolidayType.SCHOOL.name && wantsSchoolHolidays)
                }
                .flatMap { holiday ->
                    (maxOf(holiday.startEpochDay, today)..minOf(holiday.endEpochDay, toDay)).map { epochDay ->
                        val isSchool = holiday.type == HolidayType.SCHOOL.name
                        WearAgendaEntry(
                            epochDay = epochDay,
                            label = holiday.name,
                            colorArgb = if (isSchool) SCHOOL_HOLIDAY_COLOR_ARGB else PUBLIC_HOLIDAY_COLOR_ARGB,
                            timeLabel = null,
                            type = if (isSchool) WearAgendaEntryType.SCHOOL_HOLIDAY else WearAgendaEntryType.PUBLIC_HOLIDAY
                        )
                    }
                }
        } else {
            emptyList()
        }

        return (rotationShiftEntries + manualShiftEntries + birthdayEntries + holidayEntries)
            .sortedBy { it.epochDay }
    }
}
