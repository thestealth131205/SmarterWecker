package de.letheapp.nocturn.sound.lethe

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LetheMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LetheMediaApi,
    private val credentialsStore: LetheMediaCredentialsStore
) {
    fun isPaired(): Boolean = credentialsStore.getToken() != null

    suspend fun startPairing(phoneNumber: String, password: String): LethePairingRequest =
        api.requestPairing(
            phoneNumber = phoneNumber,
            password = password,
            deviceFingerprint = deviceFingerprint(),
            deviceName = "NocTurn Wecker (${Build.MODEL})"
        )

    suspend fun pollPairing(requestId: String): LethePairingPollResult {
        val result = api.pollPairing(requestId)
        if (result is LethePairingPollResult.Approved) {
            credentialsStore.saveToken(result.token)
        }
        return result
    }

    suspend fun library(): List<LetheMediaTrack> {
        val token = credentialsStore.getToken() ?: throw LetheMediaApiException("Nicht mit Lethe gekoppelt.")
        return api.fetchLibrary(token)
    }

    /** Entfernt nur den lokal gespeicherten Token. Der Grant selbst bleibt serverseitig bestehen
     * und lässt sich im Lethe Messenger unter "Bekannte Geräte" jederzeit zusätzlich widerrufen. */
    fun forgetPairing() = credentialsStore.clearToken()

    @Suppress("HardwareIds")
    private fun deviceFingerprint(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
}
