package de.letheapp.nocturn.sync

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nocturn_caldav_secrets"
private const val KEYSTORE_ALIAS = "nocturn_caldav_password_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128

/**
 * Speichert das Nextcloud-App-Passwort AES/GCM-verschlüsselt mit einem Android-Keystore-Schlüssel
 * in SharedPreferences. Bewusst ohne androidx.security-crypto (EncryptedSharedPreferences), da nur
 * ein einzelner Geheimwert pro Konto abzulegen ist und die rohe Keystore-API dafür ausreicht —
 * eine weitere Abhängigkeit für diesen Fall wäre unnötig.
 */
@Singleton
class NextcloudCredentialsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePassword(accountId: Long, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(keyFor(accountId), Base64.getEncoder().encodeToString(ciphertext))
            .putString(ivKeyFor(accountId), Base64.getEncoder().encodeToString(cipher.iv))
            .apply()
    }

    fun getPassword(accountId: Long): String? {
        val ciphertextB64 = prefs.getString(keyFor(accountId), null) ?: return null
        val ivB64 = prefs.getString(ivKeyFor(accountId), null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.getDecoder().decode(ivB64)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextB64))
        return String(plaintext, Charsets.UTF_8)
    }

    fun clearPassword(accountId: Long) {
        prefs.edit().remove(keyFor(accountId)).remove(ivKeyFor(accountId)).apply()
    }

    private fun keyFor(accountId: Long) = "password_$accountId"
    private fun ivKeyFor(accountId: Long) = "iv_$accountId"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
