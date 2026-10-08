package de.letheapp.nocturn.holidays

import de.letheapp.nocturn.data.db.HolidayDao
import de.letheapp.nocturn.data.db.HolidayEntity
import de.letheapp.nocturn.data.db.HolidaySettingsDao
import de.letheapp.nocturn.data.db.HolidaySettingsEntity
import de.letheapp.nocturn.data.db.HolidayType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet die Bundesland-Auswahl für Ferien/Feiertage sowie den lokalen Cache, der per
 * [HolidaysApi] nachgeladen wird. Pro Bundesland/Jahr wird höchstens einmal nachgefragt
 * ([ensureYearLoaded]) — die Monatsansicht stößt das beim Blättern/bei Einstellungsänderung an.
 */
@Singleton
class HolidayRepository @Inject constructor(
    private val api: HolidaysApi,
    private val holidayDao: HolidayDao,
    private val settingsDao: HolidaySettingsDao
) {
    fun observeSettings(): Flow<HolidaySettingsEntity?> = settingsDao.observe()

    suspend fun saveSettings(
        subdivisionCode: String,
        subdivisionLabel: String,
        showPublicHolidays: Boolean,
        showSchoolHolidays: Boolean
    ) {
        val current = settingsDao.getOnce()
        val entity = HolidaySettingsEntity(
            id = current?.id ?: 0,
            subdivisionCode = subdivisionCode,
            subdivisionLabel = subdivisionLabel,
            showPublicHolidays = showPublicHolidays,
            showSchoolHolidays = showSchoolHolidays
        )
        if (current == null) settingsDao.insert(entity) else settingsDao.update(entity)
    }

    fun observeHolidays(subdivisionCode: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<HolidayEntity>> =
        holidayDao.observeRange(subdivisionCode, fromEpochDay, toEpochDay)

    /** Lädt Feiertage/Ferien für [year] nach, falls für [subdivisionCode] noch nichts gecacht ist. */
    suspend fun ensureYearLoaded(subdivisionCode: String, year: Int) {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)

        val missingTypes = HolidayType.entries.filter { type ->
            holidayDao.countForYear(subdivisionCode, type.name, yearStart.toEpochDay(), yearEnd.toEpochDay()) == 0
        }
        if (missingTypes.isEmpty()) return

        val fetched = missingTypes.flatMap { type ->
            runCatching { api.fetchHolidays(subdivisionCode, yearStart, yearEnd, type) }.getOrDefault(emptyList())
        }
        if (fetched.isNotEmpty()) holidayDao.insertAll(fetched)
    }
}
