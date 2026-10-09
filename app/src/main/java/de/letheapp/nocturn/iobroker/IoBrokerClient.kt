package de.letheapp.nocturn.iobroker

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "IoBrokerClient"

/** Ein ioBroker-Datenpunkt (State), z. B. "hm-rpc.0.OEQ123.1.STATE" / "Wohnzimmerlampe". */
data class IoBrokerDataPoint(val id: String, val name: String, val value: String?)

/**
 * Schlanker REST-Client für den eigenen "iosync"-ioBroker-Adapter (HTTPS-Datenpunkt-Broker,
 * Standardport 7443, Basic-Auth) — denselben, den auch die Iosync-Android-App
 * (`/opt/Iosync/app/src/main/java/com/iosync/app/data/network/IoSyncClient.kt`) anspricht, statt
 * des zuvor fälschlich angenommenen `simple-api`-Adapters. Reines OkHttp + `org.json`, bewusst
 * ohne Retrofit/Moshi — passend zur FOSS-Minimalismus-Leitlinie (siehe CLAUDE.md). Nutzt wie das
 * Iosync-Vorbild einen Trust-All-SSL-Context, da der Adapter typischerweise ein selbstsigniertes
 * Zertifikat im lokalen Netzwerk ausstellt.
 *
 * API-Endpunkte:
 *   GET  /api/health      → Adapter-Status
 *   GET  /api/datapoints  → alle konfigurierten Datenpunkte { datapoints: [{ id, alias, value, ... }] }
 *   POST /api/setState    → Wert in ioBroker schreiben { id, value }
 */
@Singleton
class IoBrokerClient @Inject constructor() {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val httpClient: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkHealth(host: String, port: Int, useHttps: Boolean, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildUrl(host, port, useHttps, "/api/health")
                httpClient.newCall(buildRequest(url, username, password).get().build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /** Lädt alle Datenpunkte, sortiert nach Anzeigename (Adapter-Alias). */
    suspend fun fetchDataPoints(
        host: String,
        port: Int,
        useHttps: Boolean,
        username: String,
        password: String
    ): Result<List<IoBrokerDataPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildUrl(host, port, useHttps, "/api/datapoints")
            val response = httpClient.newCall(buildRequest(url, username, password).get().build()).execute()
            response.use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                val root = JSONObject(it.body?.string().orEmpty())
                val arr = root.getJSONArray("datapoints")
                (0 until arr.length()).map { i ->
                    val dp = arr.getJSONObject(i)
                    IoBrokerDataPoint(
                        id = dp.getString("id"),
                        name = dp.optString("alias").ifBlank { dp.getString("id").substringAfterLast('.') },
                        value = dp.opt("value")?.takeIf { v -> v != JSONObject.NULL }?.toString()
                    )
                }.sortedBy { it.name.lowercase() }
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
            val url = buildUrl(host, port, useHttps, "/api/setState")
            val body = JSONObject().apply { put("id", id); put("value", value) }
                .toString().toRequestBody("application/json".toMediaType())
            val response = httpClient.newCall(buildRequest(url, username, password).post(body).build()).execute()
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
