package de.letheapp.nocturn.holidays

import de.letheapp.nocturn.data.db.HolidayEntity
import de.letheapp.nocturn.data.db.HolidayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimaler Client für die kostenlose, schlüssellose Schulferien-/Feiertage-API von
 * schulferiendaten.de — liefert sowohl gesetzliche Feiertage als auch Schulferien pro
 * Bundesland für die Jahre 2025–2030 (siehe CLAUDE.md Kernfunktion 6, "Ferien, gesetzliche
 * Feiertage"). Bewusst mit OkHttp + Androids eingebautem `org.json` statt Retrofit/Moshi
 * umgesetzt, passend zur FOSS-Minimalismus-Leitlinie. Die Nutzungsbedingungen verlangen eine
 * sichtbare Quellenangabe mit Link zu schulferiendaten.de (siehe [HolidaySettingsScreen]).
 */
@Singleton
class HolidaysApi @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** [subdivisionCode] im "DE-NW"-Format (lokaler Cache-Schlüssel), wird für den Request in den schulferiendaten.de-Code umgewandelt. */
    suspend fun fetchHolidays(subdivisionCode: String, year: Int, type: HolidayType): List<HolidayEntity> =
        withContext(Dispatchers.IO) {
            val apiCode = subdivisionCode.removePrefix("DE-").lowercase()
            val segment = if (type == HolidayType.SCHOOL) "ferien" else "feiertage"
            val url = "https://schulferiendaten.de/api/v1/$segment/$apiCode/$year.json"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                if (type == HolidayType.SCHOOL) {
                    parseSchoolHolidays(body, subdivisionCode)
                } else {
                    parsePublicHolidays(body, subdivisionCode)
                }
            }
        }

    // Die Antwort ist bereits pfadseitig auf ein Bundesland gefiltert (kein Code pro Eintrag),
    // daher wird der bereits bekannte subdivisionCode direkt für alle Einträge verwendet.
    private fun parseSchoolHolidays(json: String, subdivisionCode: String): List<HolidayEntity> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val start = runCatching { LocalDate.parse(obj.optString("start")) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(obj.optString("ende")) }.getOrNull() ?: start
            val name = obj.optString("name").ifBlank { null } ?: return@mapNotNull null
            HolidayEntity(
                subdivisionCode = subdivisionCode,
                type = HolidayType.SCHOOL.name,
                name = name,
                startEpochDay = start.toEpochDay(),
                endEpochDay = end.toEpochDay()
            )
        }
    }

    private fun parsePublicHolidays(json: String, subdivisionCode: String): List<HolidayEntity> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val date = runCatching { LocalDate.parse(obj.optString("datum")) }.getOrNull() ?: return@mapNotNull null
            val name = obj.optString("name").ifBlank { null } ?: return@mapNotNull null
            HolidayEntity(
                subdivisionCode = subdivisionCode,
                type = HolidayType.PUBLIC.name,
                name = name,
                startEpochDay = date.toEpochDay(),
                endEpochDay = date.toEpochDay()
            )
        }
    }
}
