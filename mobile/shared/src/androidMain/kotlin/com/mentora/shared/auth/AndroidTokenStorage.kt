package com.mentora.shared.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val DATASTORE_FILE_NAME = "mentora_secure_tokens"
private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_FILE_NAME)

private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "mentora_token_storage_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12
private val ENCRYPTED_TOKENS_KEY = stringPreferencesKey("encrypted_tokens_v1")

@Serializable
private data class AuthTokensPayload(val accessToken: String, val refreshToken: String)

/**
 * Secure Android [TokenStorage]. Tokens are serialized, AES-256-GCM encrypted under a key that is
 * generated inside — and never leaves — the Android Keystore (hardware-backed where the device
 * supports it), and only the resulting ciphertext is persisted, in a Jetpack Preferences DataStore
 * file dedicated to this purpose.
 *
 * Chosen over `androidx.security:security-crypto`'s `EncryptedSharedPreferences` per
 * `execution/PHASE_3_KMP_PLAN.md` Task 4's explicit escape hatch: that artifact has sat at
 * `1.1.0-alpha06` with no stable release for several years, which this catalog's policy of
 * pinning every other dependency to a stable release (Ktor 3.0.1, coroutines 1.9.0, Koin 4.1.0,
 * ...) treats as "problematic to add." This class reproduces the same net security property by
 * hand — AES-GCM ciphertext under an Android-Keystore-resident key, never plain
 * `SharedPreferences` — instead of depending on that artifact. `architecture/AUTH_SECURITY.md § 4`
 * names "Keystore-backed DataStore" as an explicitly acceptable equivalent.
 *
 * Never logs a token value (no `Log.d`/`println`/equivalent, anywhere in this class).
 *
 * [context] is expected to be supplied by Koin's `androidContext()` when the Android platform DI
 * module is wired in Task 15 — this class has no dependency on Koin itself.
 */
class AndroidTokenStorage(context: Context) : TokenStorage {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveTokens(tokens: AuthTokens) {
        val plainText = json.encodeToString(
            AuthTokensPayload.serializer(),
            AuthTokensPayload(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken),
        )
        val encrypted = encrypt(plainText)
        appContext.tokenDataStore.edit { prefs -> prefs[ENCRYPTED_TOKENS_KEY] = encrypted }
    }

    override suspend fun readTokens(): AuthTokens? {
        val encrypted = appContext.tokenDataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()[ENCRYPTED_TOKENS_KEY] ?: return null

        val plainText = decrypt(encrypted) ?: return null
        val payload = json.decodeFromString(AuthTokensPayload.serializer(), plainText)
        return AuthTokens(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
    }

    override suspend fun clearTokens() {
        appContext.tokenDataStore.edit { prefs -> prefs.remove(ENCRYPTED_TOKENS_KEY) }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Returns `null` (rather than throwing) for ciphertext that fails to decrypt/parse — treated
     * as "no valid session," which forces a clean re-login instead of crashing. */
    private fun decrypt(storedValue: String): String? = try {
        val combined = Base64.decode(storedValue, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    } catch (cause: Exception) {
        null
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
