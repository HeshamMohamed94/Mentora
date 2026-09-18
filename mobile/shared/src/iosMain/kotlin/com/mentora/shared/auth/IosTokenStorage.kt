package com.mentora.shared.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess

// NOT compiled/verified on this Windows machine -- iosArm64/iosSimulatorArm64 require a macOS
// host. See "Disclosed limitation B1" in execution/PHASE_3_KMP_PLAN.md.
//
// Phase 5 Task T1b rewrite (PHASE_5_IOS_SYSTEM_DESIGN.md section 9.1): the shipped Phase 3
// version of this file discarded every Keychain OSStatus -- setKeychainValue ignored the result
// of both SecItemAdd and SecItemUpdate, and deleteKeychainValue ignored SecItemDelete entirely --
// so a failed refresh-token write could leave a mismatched token pair (access token rotated,
// refresh token stale) and a failed logout delete could report success while the session survived
// into the next launch. This version:
//   K1 -- stores the pair as ONE Keychain item holding a JSON {accessToken, refreshToken} value,
//         so a partial pair is structurally impossible rather than cleaned up after the fact.
//   K2 -- inspects every add/update/delete/query OSStatus; errSecItemNotFound is the expected
//         "no stored session" outcome, never a failure.
//   K3 -- never throws across the Swift boundary: a save failure best-effort purges, a clear
//         failure retries once then tombstone-overwrites, and anything that survives both is
//         published on KeychainStatus.failures (see Keychain.kt) for SessionController (T4b) to
//         surface -- never a thrown Kotlin exception.
//   K4 -- delegated to Keychain.kt's SecurityFrameworkKeychain, which sets an explicit
//         kSecAttrAccessibleWhenUnlockedThisDeviceOnly on every query.
//   K6 -- talks to the Keychain only through the KeychainStore seam (Keychain.kt), defaulted to
//         the real SecurityFrameworkKeychain so PlatformModule.ios.kt's IosTokenStorage() call
//         site is unchanged.

private val tokenStorageJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class AuthTokensPayload(val accessToken: String, val refreshToken: String)

/** The value `clearTokens()` overwrites the item with when `SecItemDelete` fails twice (K3) --
 * deliberately not valid JSON, so `readTokens()` maps it to `null` exactly like a missing item:
 * two independent ways to neutralize a session that could not be deleted outright. */
private const val TOMBSTONE_VALUE = "MENTORA_KEYCHAIN_TOMBSTONE"

/**
 * iOS Keychain-backed [TokenStorage] (`kSecClassGenericPassword`, service
 * `com.mentora.shared.tokenStorage`). Never logs a token value.
 *
 * A plain class, not an `expect`/`actual` pairing with [com.mentora.shared.auth.TokenStorage] --
 * see that interface's kdoc for why. Constructed directly by iOS platform DI
 * ([com.mentora.shared.di.platformModule]); needs no constructor argument from that call site --
 * [keychain] is the section 9.1 K6 test seam, defaulted to the real [SecurityFrameworkKeychain] so
 * `PlatformModule.ios.kt`'s `IosTokenStorage()` call is unchanged and the seam itself stays out of
 * the generated Swift API (only the defaulted public constructor shape is visible there).
 */
class IosTokenStorage(private val keychain: KeychainStore = SecurityFrameworkKeychain()) : TokenStorage {

    override suspend fun saveTokens(tokens: AuthTokens) {
        val payload = tokenStorageJson.encodeToString(
            AuthTokensPayload.serializer(),
            AuthTokensPayload(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken),
        )

        // One item, one logical write of the pair (K1): this existence check only decides whether
        // that write is a SecItemAdd or a SecItemUpdate -- it is never a second mutation of the
        // pair, and it reuses the same copyMatching() the K6 seam already exposes for reads rather
        // than adding a fifth seam method just for this.
        val existsAlready = keychain.copyMatching().status == errSecSuccess
        val status = if (existsAlready) keychain.update(payload) else keychain.add(payload)

        if (status == errSecSuccess) return

        // Genuine failure: best-effort purge so nothing stale or half-written survives, then
        // publish -- never throw across the Swift boundary (K3).
        keychain.delete()
        KeychainStatus.publish(KeychainFailure(KeychainOperation.SAVE, status))
    }

    override suspend fun readTokens(): AuthTokens? {
        val result = keychain.copyMatching()

        return when {
            // The expected "no stored session" outcome (K2) -- never a failure.
            result.status == errSecItemNotFound -> null
            result.status != errSecSuccess -> {
                KeychainStatus.publish(KeychainFailure(KeychainOperation.READ, result.status))
                null
            }
            result.value == null || result.value == TOMBSTONE_VALUE -> null
            else -> decodePayload(result.value)
        }
    }

    override suspend fun clearTokens() {
        if (isDeletedOrAlreadyGone(keychain.delete())) return
        if (isDeletedOrAlreadyGone(keychain.delete())) return // one retry, per K3

        val tombstoneStatus = keychain.update(TOMBSTONE_VALUE)
        if (tombstoneStatus == errSecSuccess) return

        // The delete retry and the tombstone overwrite both failed: the session cannot be
        // neutralized from in here. Published rather than thrown (K3) so SessionController can
        // keep this from presenting itself as a completed logout.
        KeychainStatus.publish(KeychainFailure(KeychainOperation.CLEAR, tombstoneStatus))
    }

    private fun isDeletedOrAlreadyGone(status: Int): Boolean =
        status == errSecSuccess || status == errSecItemNotFound

    /** A malformed/corrupted stored payload is treated as "no valid session" -- never a crash,
     * and per K3 not itself a published failure (the same bucket as "not found" and the
     * tombstone). */
    private fun decodePayload(raw: String): AuthTokens? = try {
        val payload = tokenStorageJson.decodeFromString(AuthTokensPayload.serializer(), raw)
        AuthTokens(accessToken = payload.accessToken, refreshToken = payload.refreshToken)
    } catch (cause: Exception) {
        null
    }
}
