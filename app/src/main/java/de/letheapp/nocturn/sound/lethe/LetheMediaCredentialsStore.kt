package de.letheapp.nocturn.sound.lethe

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

private const val PREFS_NAME = "nocturn_lethe_media_secrets"
private const val KEYSTORE_ALIAS = "nocturn_lethe_media_token_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val KEY_TOKEN = "nm_token"
private const val KEY_IV = "nm_token_iv"

/**
 * Speichert den schlanken Lethe-`media:read`-Scope-Token AES/GCM-verschlüsselt mit einem
 * Android-Keystore-Schlüssel, analog zu NextcloudCredentialsStore. Nur ein Token pro Gerät
 * (keine accountId nötig, im Gegensatz zu Nextcloud mit mehreren möglichen Konten).
 */
@Singleton
class LetheMediaCredentialsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_TOKEN, Base64.getEncoder().encodeToString(ciphertext))
            .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .apply()
    }

    fun getToken(): String? {
        val ciphertextB64 = prefs.getString(KEY_TOKEN, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.getDecoder().decode(ivB64)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(Base64.getDecoder().decode(ciphertextB64)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_IV).apply()
    }

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
