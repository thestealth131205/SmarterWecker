package de.letheapp.nocturn.iobroker

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IoBrokerClient"

/** Ein ioBroker-Datenpunkt (State), z. B. "hm-rpc.0.OEQ123.1.STATE" / "Wohnzimmerlampe". */
data class IoBrokerDataPoint(val id: String, val name: String, val value: String?)

/**
 * Schlanker REST-Client für den ioBroker-Simple-API-Adapter (`simple-api`, Standardport 8087),
 * der States auflistet (`/getStates`, liefert ein Objekt `{ "<id>": { val, ... } }`) und setzt
 * (`/set/<id>?value=...`). Reines OkHttp + Basic-Auth + `org.json`, bewusst ohne Retrofit/Moshi —
 * passend zur FOSS-Minimalismus-Leitlinie (siehe CLAUDE.md) und analog zum bereits im Projekt
 * genutzten [de.letheapp.nocturn.sync.CalDavClient]-Muster.
 */
@Singleton
class IoBrokerClient @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkHealth(host: String, port: Int, useHttps: Boolean, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(host, port, useHttps, "/getStates")
                httpClient.newCall(buildRequest(url, username, password).get().build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /** Lädt alle Datenpunkte, sortiert nach Anzeigename (letztes ID-Segment). */
    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String
    ): Result<List<IoBrokerDataPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, useHttps, "/getStates")
            val response = httpClient.newCall(buildRequest(url, username, password).get().build()).execute()
            response.use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                val root = JSONObject(it.body?.string().orEmpty())
                val points = root.keys().asSequence().map { id ->
                    val entry = root.optJSONObject(id)
                    val value = entry?.opt("val")?.takeIf { v -> v != JSONObject.NULL }?.toString()
                    IoBrokerDataPoint(id = id, name = id.substringAfterLast('.'), value = value)
                }.sortedBy { it.name.lowercase() }.toList()
                points
            }
        }.onFailure { Log.e(TAG, "fetchDataPoints fehlgeschlagen: ${it.message}") }
    }

    /** Setzt einen Datenpunkt auf [value] (z. B. "true"/"false"), um eine Lampe/Steckdose zu schalten. */
    suspend fun setState(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String,
        id: String,
        value: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
            val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
            val url = buildUrl(host, port, useHttps, "/set/$encodedId?value=$encodedValue")
            val response = httpClient.newCall(buildRequest(url, username, password).get().build()).execute()
            response.use { check(it.isSuccessful) { "HTTP ${it.code}" } }
        }.onFailure { Log.e(TAG, "setState($id) fehlgeschlagen: ${it.message}") }
    }

    private fun buildUrl(host: String, port: Int, useHttps: Boolean, path: String): String {
        val scheme = if (useHttps) "https" else "http"
        val h = host.trim().trimEnd('/')
        return "$scheme://$h:$port$path"
    }

    private fun buildRequest(url: String, username: String, password: String) =
        Request.Builder().url(url).apply {
            if (username.isNotBlank() && password.isNotBlank()) {
                addHeader("Authorization", Credentials.basic(username, password))
            }
        }
}
