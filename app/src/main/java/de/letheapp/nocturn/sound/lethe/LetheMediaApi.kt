package de.letheapp.nocturn.sound.lethe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Produktiv-Backend der Lethe-Plattform (siehe Apache2-site-confs/letheapp.de-le-ssl.conf im Lethe-Repo). */
const val LETHE_BACKEND_BASE_URL = "https://letheapp.de"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class LetheMediaApiException(message: String) : Exception(message)

data class LethePairingRequest(val requestId: String, val expiresAt: String)

sealed class LethePairingPollResult {
    data object Pending : LethePairingPollResult()
    data class Approved(val token: String) : LethePairingPollResult()
    data object Denied : LethePairingPollResult()
    data object Expired : LethePairingPollResult()
}

data class LetheMediaTrack(
    val id: String,
    val title: String?,
    val artist: String?,
    val coverUrl: String?,
    val durationSeconds: Int?,
    val streamUrl: String
)

/**
 * Client für den schlanken `media:read`-Scope im Lethe-Backend (siehe CLAUDE.md "Ton-Auswahl ...
 * Lethe-Bibliothek"). Gibt NocTurn bewusst NIE Zugriff auf einen vollen Login-JWT oder den
 * UMK (Ende-zu-Ende-Verschlüsselungs-Schlüssel) des Lethe Messengers — nur einen widerrufbaren
 * `nm_<token>`, der ausschließlich für die /nocturn/media-Endpunkte gültig ist. Freigabe erfolgt durch den
 * Nutzer auf seinem Lethe-Messenger-Host-Gerät (Push-Bestätigung), nicht durch NocTurn selbst.
 *
 * Bewusst mit OkHttp + org.json statt Retrofit umgesetzt (gleiches Muster wie HolidaysApi),
 * passend zur FOSS-Minimalismus-Leitlinie: nur vier einfache JSON-Endpunkte.
 */
@Singleton
class LetheMediaApi @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun requestPairing(
        phoneNumber: String,
        password: String,
        deviceFingerprint: String,
        deviceName: String
    ): LethePairingRequest = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("phone_number", phoneNumber)
            put("password", password)
            put("device_fingerprint", deviceFingerprint)
            put("device_name", deviceName)
        }
        val request = Request.Builder()
            .url("$LETHE_BACKEND_BASE_URL/nocturn/media/pair/request")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw LetheMediaApiException(extractDetail(body) ?: "Kopplungsanfrage fehlgeschlagen (${response.code}).")
            }
            val obj = JSONObject(body)
            LethePairingRequest(obj.getString("request_id"), obj.optString("expires_at"))
        }
    }

    suspend fun pollPairing(requestId: String): LethePairingPollResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply { put("request_id", requestId) }
        val request = Request.Builder()
            .url("$LETHE_BACKEND_BASE_URL/nocturn/media/pair/poll")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                200 -> {
                    val obj = JSONObject(body)
                    if (obj.optString("status") == "approved") {
                        val token = obj.optString("token").takeIf { it.isNotBlank() }
                            ?: throw LetheMediaApiException("Kopplungs-Token konnte nicht abgeholt werden. Bitte erneut koppeln.")
                        LethePairingPollResult.Approved(token)
                    } else {
                        LethePairingPollResult.Pending
                    }
                }
                403 -> LethePairingPollResult.Denied
                410 -> LethePairingPollResult.Expired
                else -> throw LetheMediaApiException(extractDetail(body) ?: "Unerwarteter Fehler (${response.code}).")
            }
        }
    }

    suspend fun fetchLibrary(token: String): List<LetheMediaTrack> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$LETHE_BACKEND_BASE_URL/nocturn/media/library")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw LetheMediaApiException(extractDetail(body) ?: "Bibliothek konnte nicht geladen werden (${response.code}).")
            }
            val array = JSONArray(body)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                LetheMediaTrack(
                    id = obj.getString("id"),
                    title = obj.optString("title").ifBlank { null },
                    artist = obj.optString("artist").ifBlank { null },
                    coverUrl = obj.optString("cover_url").ifBlank { null },
                    durationSeconds = if (obj.isNull("duration_seconds")) null else obj.optInt("duration_seconds"),
                    // Token als Query-Parameter, da Androids MediaPlayer beim Abspielen einer reinen
                    // URI keinen eigenen Authorization-Header mitsenden kann (serverseitig als
                    // Fallback zum Header in get_media_scope_user unterstützt).
                    streamUrl = "$LETHE_BACKEND_BASE_URL${obj.getString("stream_url")}?token=$token"
                )
            }
        }
    }

    private fun extractDetail(body: String): String? =
        runCatching { JSONObject(body).optString("detail").ifBlank { null } }.getOrNull()
}
