package de.letheapp.nocturn.export

import de.letheapp.nocturn.calendar.PUBLIC_HOLIDAY_COLOR_ARGB
import de.letheapp.nocturn.calendar.SCHOOL_HOLIDAY_COLOR_ARGB
import de.letheapp.nocturn.data.db.HolidayType
import de.letheapp.nocturn.data.repository.CalendarRepository
import de.letheapp.nocturn.holidays.HolidayRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Ein einzelner Eintrag an einem Tag für den Kalender-Export (siehe [CalendarExportScreen]). */
data class ExportDayEntry(
    val label: String,
    val colorArgb: Int,
    val isPublicHoliday: Boolean = false
)

/**
 * Einmalige (nicht reaktive) Zusammenfassung aller Kalendereinträge für einen beliebigen
 * Zeitraum, als Grundlage für den druckbaren Export (PDF/JPG/PNG). Spiegelt dieselbe
 * Zusammenführung (Schichten aus Rotationen + manuell + freie Termine + Feiertage/Ferien)
 * wie [de.letheapp.nocturn.calendar.CalendarMonthViewModel], aber als einmalige Abfrage
 * über einen frei wählbaren Zeitraum statt eines einzelnen Monats.
 */
class CalendarExportRepository @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository
) {
    suspend fun getEntriesForRange(fromEpochDay: Long, toEpochDay: Long): Map<LocalDate, List<ExportDayEntry>> {
        val entries = calendarRepository.observeCalendarEntries(fromEpochDay, toEpochDay).first()
        val shifts = calendarRepository.observeAllShifts().first()
        val jobs = calendarRepository.observeJobs().first()
        val appointments = calendarRepository.observeAppointments(fromEpochDay, toEpochDay).first()
        val manualShifts = calendarRepository.observeManualShifts(fromEpochDay, toEpochDay).first()

        val shiftsById = shifts.associateBy { it.id }
        val jobsById = jobs.associateBy { it.id }

        val result = mutableMapOf<LocalDate, MutableList<ExportDayEntry>>()
        fun add(epochDay: Long, entry: ExportDayEntry) {
            if (epochDay !in fromEpochDay..toEpochDay) return
            result.getOrPut(LocalDate.ofEpochDay(epochDay)) { mutableListOf() }.add(entry)
        }

        entries.forEach { entry ->
            val shiftId = entry.shiftId ?: return@forEach
            val shift = shiftsById[shiftId] ?: return@forEach
            val jobName = jobsById[entry.jobId]?.name.orEmpty()
            val label = if (jobName.isNotBlank()) "${shift.name} ($jobName)" else shift.name
            add(entry.dateEpochDay, ExportDayEntry(label = label, colorArgb = shift.colorArgb))
        }

        manualShifts.forEach { entry ->
            val shift = shiftsById[entry.shiftId] ?: return@forEach
            val jobName = jobsById[entry.jobId]?.name.orEmpty()
            val label = if (jobName.isNotBlank()) "${shift.name} ($jobName)" else shift.name
            add(entry.dateEpochDay, ExportDayEntry(label = label, colorArgb = shift.colorArgb))
        }

        appointments.forEach { appointment ->
            (appointment.startEpochDay..appointment.endEpochDay).forEach { epochDay ->
                add(epochDay, ExportDayEntry(label = appointment.title, colorArgb = appointment.colorArgb))
            }
        }

        val settings = holidayRepository.observeSettings().first()
        if (settings != null && (settings.showPublicHolidays || settings.showSchoolHolidays)) {
            val fromYear = LocalDate.ofEpochDay(fromEpochDay).year
            val toYear = LocalDate.ofEpochDay(toEpochDay).year
            (fromYear..toYear).forEach { year -> holidayRepository.ensureYearLoaded(settings.subdivisionCode, year) }

            val holidays = holidayRepository.observeHolidays(settings.subdivisionCode, fromEpochDay, toEpochDay).first()
            holidays.forEach { holiday ->
                val isSchool = holiday.type == HolidayType.SCHOOL.name
                if (isSchool && !settings.showSchoolHolidays) return@forEach
                if (!isSchool && !settings.showPublicHolidays) return@forEach
                (maxOf(holiday.startEpochDay, fromEpochDay)..minOf(holiday.endEpochDay, toEpochDay)).forEach { epochDay ->
                    add(
                        epochDay,
                        ExportDayEntry(
                            label = holiday.name,
                            colorArgb = if (isSchool) SCHOOL_HOLIDAY_COLOR_ARGB else PUBLIC_HOLIDAY_COLOR_ARGB,
                            isPublicHoliday = !isSchool
                        )
                    )
                }
            }
        }

        return result
    }
}
