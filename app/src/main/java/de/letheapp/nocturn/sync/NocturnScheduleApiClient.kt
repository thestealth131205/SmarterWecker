package de.letheapp.nocturn.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NocturnScheduleApiException(message: String) : Exception(message)

data class RemoteJob(val uid: String, val name: String, val mode: String, val updatedAt: Long, val deletedAt: Long?)

data class RemoteShift(
    val uid: String,
    val jobUid: String,
    val name: String,
    val color: String,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val earliestWakeOffsetMinutes: Int,
    val latestWakeupOffsetMinutes: Int,
    val updatedAt: Long,
    val deletedAt: Long?
)

data class RemoteRotation(val uid: String, val jobUid: String, val name: String, val updatedAt: Long, val deletedAt: Long?)

data class RemoteRotationDay(
    val uid: String,
    val rotationUid: String,
    val dayIndex: Int,
    val shiftUid: String?,
    val updatedAt: Long,
    val deletedAt: Long?
)

data class RemoteOccurrence(
    val uid: String,
    val jobUid: String,
    val epochDay: Long,
    val shiftUid: String?,
    val rotationUid: String?,
    val source: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

data class RemoteLesson(
    val uid: String,
    val jobUid: String,
    val weekday: Int,
    val name: String,
    val color: String,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val earliestWakeOffsetMinutes: Int,
    val latestWakeupOffsetMinutes: Int,
    val updatedAt: Long,
    val deletedAt: Long?
)

data class RemoteTask(
    val uid: String,
    val title: String,
    val notes: String?,
    val dueEpochDay: Long,
    val dueEpochMillis: Long?,
    val allDay: Boolean,
    val completed: Boolean,
    val color: String,
    val updatedAt: Long,
    val deletedAt: Long?
)

data class RemoteScheduleChanges(
    val jobs: List<RemoteJob>,
    val shifts: List<RemoteShift>,
    val rotations: List<RemoteRotation>,
    val rotationDays: List<RemoteRotationDay>,
    val occurrences: List<RemoteOccurrence>,
    val lessons: List<RemoteLesson>,
    val tasks: List<RemoteTask>,
    val serverTime: Long
)

/**
 * Hand-rollter HTTP-Client für die strukturierte NocTurn-Schedule-REST-API der Nextcloud-
 * Calendar-App (`/apps/nocturnkalender/v1/nocturn/...`, siehe `Nocturn-Kalender/lib/Controller/
 * NocturnScheduleController.php`) — bewusst ohne Retrofit/Moshi (FOSS-Minimalismus, wie bei
 * [CalDavClient]/[IcsFormat]), nur OkHttp + das in Android eingebaute `org.json`.
 *
 * Nutzt dieselbe Basic-Auth (Nextcloud-Login + App-Passwort) wie der CalDAV-Export — beides sind
 * Endpunkte desselben Nextcloud-Nutzerkontos, es wird kein zweites Konto verbunden.
 */
@Singleton
class NocturnScheduleApiClient @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Pro Server-URL der zuletzt funktionierende Basis-Pfad (pretty vs. "/index.php/"-Präfix). */
    private val resolvedBaseCache = HashMap<String, String>()

    /**
     * Prüft über den Standard-OCS-Capabilities-Endpoint (kein Extra-Scope/keine Extra-Berechtigung
     * nötig), ob die NocTurn-Kalender-App auf diesem Nextcloud-Server installiert/aktiviert ist
     * (siehe `Nocturn-Kalender/lib/AppInfo/Capabilities.php`, meldet den Schlüssel
     * "nocturnkalender"). Dient dazu, vor dem Schichtplan-Sync klar zu erkennen "App fehlt" statt
     * nur einen rohen 404 der Schedule-API zu zeigen.
     */
    suspend fun isNocturnKalenderInstalled(serverUrl: String, username: String, password: String): Boolean {
        val trimmed = serverUrl.trim().trimEnd('/')
        val candidates = listOf(
            "$trimmed/ocs/v2.php/cloud/capabilities?format=json",
            "$trimmed/index.php/ocs/v2.php/cloud/capabilities?format=json"
        )
        for (candidate in candidates) {
            val json = runCatching {
                val url = candidate.toHttpUrlOrNull() ?: return@runCatching null
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(username, password))
                    .header("OCS-APIREQUEST", "true")
                    .get()
                    .build()
                executeHttp(request) { response ->
                    if (!response.isSuccessful) null else JSONObject(response.body?.string().orEmpty())
                }
            }.getOrNull() ?: continue
            val capabilities = json?.optJSONObject("ocs")?.optJSONObject("data")?.optJSONObject("capabilities")
            if (capabilities?.has("nocturnkalender") == true) return true
        }
        return false
    }

    suspend fun sync(serverUrl: String, username: String, password: String, since: Long): RemoteScheduleChanges {
        val json = request(serverUrl, username, password, "GET", "sync?since=$since", null)
        val data = json.getJSONObject("data")
        return RemoteScheduleChanges(
            jobs = data.getJSONArray("jobs").map(::parseJob),
            shifts = data.getJSONArray("shifts").map(::parseShift),
            rotations = data.getJSONArray("rotations").map(::parseRotation),
            rotationDays = data.getJSONArray("rotationDays").map(::parseRotationDay),
            occurrences = data.getJSONArray("occurrences").map(::parseOccurrence),
            lessons = data.optJSONArray("lessons")?.map(::parseLesson).orEmpty(),
            tasks = data.optJSONArray("tasks")?.map(::parseTask).orEmpty(),
            serverTime = data.getLong("serverTime")
        )
    }

    suspend fun upsertLesson(
        serverUrl: String,
        username: String,
        password: String,
        uid: String?,
        jobUid: String,
        weekday: Int,
        name: String,
        color: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        earliestWakeOffsetMinutes: Int,
        latestWakeupOffsetMinutes: Int,
        updatedAt: Long?
    ): RemoteLesson {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("jobUid", jobUid)
            add("weekday", weekday.toString())
            add("name", name)
            add("color", color)
            add("startHour", startHour.toString())
            add("startMinute", startMinute.toString())
            add("durationMinutes", durationMinutes.toString())
            add("earliestWakeOffsetMinutes", earliestWakeOffsetMinutes.toString())
            add("latestWakeupOffsetMinutes", latestWakeupOffsetMinutes.toString())
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "lessons", body)
        return parseLesson(json.getJSONObject("data"))
    }

    suspend fun deleteLesson(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "lessons/${encode(uid)}", null)
    }

    suspend fun upsertTask(
        serverUrl: String,
        username: String,
        password: String,
        uid: String?,
        title: String,
        notes: String?,
        dueEpochDay: Long,
        dueEpochMillis: Long?,
        allDay: Boolean,
        completed: Boolean,
        color: String,
        updatedAt: Long?
    ): RemoteTask {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("title", title)
            notes?.let { add("notes", it) }
            add("dueEpochDay", dueEpochDay.toString())
            dueEpochMillis?.let { add("dueEpochMillis", it.toString()) }
            add("allDay", allDay.toString())
            add("completed", completed.toString())
            add("color", color)
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "tasks", body)
        return parseTask(json.getJSONObject("data"))
    }

    suspend fun deleteTask(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "tasks/${encode(uid)}", null)
    }

    suspend fun upsertJob(serverUrl: String, username: String, password: String, uid: String?, name: String, mode: String, updatedAt: Long?): RemoteJob {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("name", name)
            add("mode", mode)
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "jobs", body)
        return parseJob(json.getJSONObject("data"))
    }

    suspend fun deleteJob(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "jobs/${encode(uid)}", null)
    }

    suspend fun upsertShift(
        serverUrl: String,
        username: String,
        password: String,
        uid: String?,
        jobUid: String,
        name: String,
        color: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int,
        earliestWakeOffsetMinutes: Int,
        latestWakeupOffsetMinutes: Int,
        updatedAt: Long?
    ): RemoteShift {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("jobUid", jobUid)
            add("name", name)
            add("color", color)
            add("startHour", startHour.toString())
            add("startMinute", startMinute.toString())
            add("durationMinutes", durationMinutes.toString())
            add("earliestWakeOffsetMinutes", earliestWakeOffsetMinutes.toString())
            add("latestWakeupOffsetMinutes", latestWakeupOffsetMinutes.toString())
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "shifts", body)
        return parseShift(json.getJSONObject("data"))
    }

    suspend fun deleteShift(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "shifts/${encode(uid)}", null)
    }

    /** [dayShiftUids] ist das geordnete Muster, `null` = freier Tag an dieser Stelle. */
    suspend fun upsertRotation(
        serverUrl: String,
        username: String,
        password: String,
        uid: String?,
        jobUid: String,
        name: String,
        dayShiftUids: List<String?>,
        updatedAt: Long?
    ): RemoteRotation {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("jobUid", jobUid)
            add("name", name)
            dayShiftUids.forEachIndexed { index, shiftUid -> add("days[$index]", shiftUid.orEmpty()) }
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "rotations", body)
        return parseRotation(json.getJSONObject("data"))
    }

    suspend fun deleteRotation(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "rotations/${encode(uid)}", null)
    }

    suspend fun upsertOccurrence(
        serverUrl: String,
        username: String,
        password: String,
        uid: String?,
        jobUid: String,
        epochDay: Long,
        shiftUid: String?,
        updatedAt: Long?
    ): RemoteOccurrence {
        val body = formBody {
            uid?.let { add("uid", it) }
            add("jobUid", jobUid)
            add("epochDay", epochDay.toString())
            shiftUid?.let { add("shiftUid", it) }
            updatedAt?.let { add("updatedAt", it.toString()) }
        }
        val json = request(serverUrl, username, password, "POST", "occurrences", body)
        return parseOccurrence(json.getJSONObject("data"))
    }

    suspend fun deleteOccurrence(serverUrl: String, username: String, password: String, uid: String) {
        request(serverUrl, username, password, "DELETE", "occurrences/${encode(uid)}", null)
    }

    private fun parseJob(o: JSONObject) = RemoteJob(
        uid = o.getString("uid"),
        name = o.getString("name"),
        mode = o.getString("mode"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseShift(o: JSONObject) = RemoteShift(
        uid = o.getString("uid"),
        jobUid = o.getString("jobUid"),
        name = o.getString("name"),
        color = o.getString("color"),
        startHour = o.getInt("startHour"),
        startMinute = o.getInt("startMinute"),
        durationMinutes = o.getInt("durationMinutes"),
        earliestWakeOffsetMinutes = o.getInt("earliestWakeOffsetMinutes"),
        latestWakeupOffsetMinutes = o.getInt("latestWakeupOffsetMinutes"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseRotation(o: JSONObject) = RemoteRotation(
        uid = o.getString("uid"),
        jobUid = o.getString("jobUid"),
        name = o.getString("name"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseRotationDay(o: JSONObject) = RemoteRotationDay(
        uid = o.getString("uid"),
        rotationUid = o.getString("rotationUid"),
        dayIndex = o.getInt("dayIndex"),
        shiftUid = o.optStringOrNull("shiftUid"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseLesson(o: JSONObject) = RemoteLesson(
        uid = o.getString("uid"),
        jobUid = o.getString("jobUid"),
        weekday = o.getInt("weekday"),
        name = o.getString("name"),
        color = o.getString("color"),
        startHour = o.getInt("startHour"),
        startMinute = o.getInt("startMinute"),
        durationMinutes = o.getInt("durationMinutes"),
        earliestWakeOffsetMinutes = o.getInt("earliestWakeOffsetMinutes"),
        latestWakeupOffsetMinutes = o.getInt("latestWakeupOffsetMinutes"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseTask(o: JSONObject) = RemoteTask(
        uid = o.getString("uid"),
        title = o.getString("title"),
        notes = o.optStringOrNull("notes"),
        dueEpochDay = o.getLong("dueEpochDay"),
        dueEpochMillis = o.optLongOrNull("dueEpochMillis"),
        allDay = o.getBoolean("allDay"),
        completed = o.getBoolean("completed"),
        color = o.getString("color"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private fun parseOccurrence(o: JSONObject) = RemoteOccurrence(
        uid = o.getString("uid"),
        jobUid = o.getString("jobUid"),
        epochDay = o.getLong("epochDay"),
        shiftUid = o.optStringOrNull("shiftUid"),
        rotationUid = o.optStringOrNull("rotationUid"),
        source = o.getString("source"),
        updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt")
    )

    private inline fun formBody(block: FormBody.Builder.() -> Unit): FormBody =
        FormBody.Builder().apply(block).build()

    private fun FormBody.Builder.add(name: String, value: String) {
        add(name, value)
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    /**
     * Führt den Request gegen die Calendar-App-REST-API aus. Nextcloud mountet App-Routen
     * normalerweise unter "/apps/{appid}/..." (pretty URLs), bei Servern ohne aktiviertes
     * URL-Rewriting aber nur unter "/index.php/apps/{appid}/...". Wird pro Server-URL einmalig
     * ermittelt und für folgende Aufrufe zwischengespeichert (analog [CalDavClient.resolveDavBase]).
     */
    private suspend fun request(serverUrl: String, username: String, password: String, method: String, path: String, body: FormBody?): JSONObject {
        val trimmed = serverUrl.trim().trimEnd('/')
        val cached = resolvedBaseCache[trimmed]
        if (cached != null) {
            runCatching { executeRequest(cached, username, password, method, path, body) }.getOrNull()?.let { return it }
        }
        val candidates = listOfNotNull(cached, "$trimmed/apps/nocturnkalender/v1/nocturn", "$trimmed/index.php/apps/nocturnkalender/v1/nocturn").distinct()
        var lastError: Exception? = null
        for (base in candidates) {
            try {
                val result = executeRequest(base, username, password, method, path, body)
                resolvedBaseCache[trimmed] = base
                return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: NocturnScheduleApiException("Nextcloud-Schedule-API unter \"$serverUrl\" nicht erreichbar")
    }

    private suspend fun executeRequest(base: String, username: String, password: String, method: String, path: String, body: FormBody?): JSONObject {
        val url = "$base/$path".toHttpUrlOrNull() ?: throw NocturnScheduleApiException("Ungültige Adresse: $base/$path")
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("OCS-APIREQUEST", "true")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post(body ?: FormBody.Builder().build())
            else -> throw NocturnScheduleApiException("Unbekannte HTTP-Methode $method")
        }
        return executeHttp(builder.build()) { response ->
            if (!response.isSuccessful) {
                throw NocturnScheduleApiException("Nextcloud-Schedule-API antwortete mit ${response.code} für $method $url")
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) JSONObject().put("status", "success").put("data", JSONObject()) else JSONObject(text)
        }
    }

    private suspend fun <T> executeHttp(request: Request, block: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use(block)
        }
}

private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (isNull(name) || !has(name)) null else getLong(name)

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name) || !has(name)) null else getString(name)
