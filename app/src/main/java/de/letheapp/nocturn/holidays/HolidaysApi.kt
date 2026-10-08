package de.letheapp.nocturn.holidays

import de.letheapp.nocturn.data.db.HolidayEntity
import de.letheapp.nocturn.data.db.HolidayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimaler Client für die kostenlose, schlüssellose OpenHolidaysAPI (openholidaysapi.org,
 * ODbL-Lizenz) — liefert sowohl gesetzliche Feiertage als auch Schulferien pro Bundesland,
 * daher hier statt z. B. feiertage-api.de verwendet (siehe CLAUDE.md Kernfunktion 6,
 * "Ferien, gesetzliche Feiertage"). Bewusst mit OkHttp + Androids eingebautem `org.json` statt
 * Retrofit/Moshi umgesetzt, passend zur FOSS-Minimalismus-Leitlinie (keine neue Abhängigkeit
 * für zwei einfache GET-Endpunkte).
 */
@Singleton
class HolidaysApi @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchHolidays(
        subdivisionCode: String,
        validFrom: LocalDate,
        validTo: LocalDate,
        type: HolidayType
    ): List<HolidayEntity> = withContext(Dispatchers.IO) {
        val path = if (type == HolidayType.SCHOOL) "SchoolHolidays" else "PublicHolidays"
        val url = "https://openholidaysapi.org/$path".toHttpUrl().newBuilder()
            .addQueryParameter("countryIsoCode", "DE")
            .addQueryParameter("languageIsoCode", "DE")
            .addQueryParameter("subdivisionCode", subdivisionCode)
            .addQueryParameter("validFrom", validFrom.toString())
            .addQueryParameter("validTo", validTo.toString())
            .build()
        val request = Request.Builder().url(url).build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body?.string().orEmpty()
            parseHolidays(body, subdivisionCode, type)
        }
    }

    private fun parseHolidays(json: String, subdivisionCode: String, type: HolidayType): List<HolidayEntity> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val start = runCatching { LocalDate.parse(obj.optString("startDate")) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(obj.optString("endDate")) }.getOrNull() ?: start
            val name = extractGermanName(obj) ?: return@mapNotNull null
            HolidayEntity(
                subdivisionCode = subdivisionCode,
                type = type.name,
                name = name,
                startEpochDay = start.toEpochDay(),
                endEpochDay = end.toEpochDay()
            )
        }
    }

    private fun extractGermanName(obj: JSONObject): String? {
        val names = obj.optJSONArray("name") ?: return null
        var fallback: String? = null
        for (i in 0 until names.length()) {
            val entry = names.optJSONObject(i) ?: continue
            val text = entry.optString("text").ifBlank { null } ?: continue
            if (fallback == null) fallback = text
            if (entry.optString("language") == "DE") return text
        }
        return fallback
    }
}
