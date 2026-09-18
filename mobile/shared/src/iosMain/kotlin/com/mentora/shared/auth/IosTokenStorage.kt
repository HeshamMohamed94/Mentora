package com.mentora.shared.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecParam
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
//   K3 -- never throws across the Swift boundary: a save failure best-effort purges only when
//         that is genuinely safe (see saveTokens below), a clear failure retries once then
//         tombstone-overwrites, and anything that survives both is published on
//         KeychainStatus.failures (see Keychain.kt) for SessionController (T4b) to surface --
//         never a thrown Kotlin exception.
//   K4 -- delegated to Keychain.kt's SecurityFrameworkKeychain, which sets an explicit
//         kSecAttrAccessibleWhenUnlockedThisDeviceOnly on every query.
//   K6 -- talks to the Keychain only through the KeychainStore seam (Keychain.kt), defaulted to
//         the real SecurityFrameworkKeychain so PlatformModule.ios.kt's IosTokenStorage() call
//         site is unchanged.
//
// Review round 2 (Opus code review of the T1b commit) found a compile blocker and several
// logic/security bugs in this file and in Keychain.kt; see DECISIONS_LOG.md for the summary and
// PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1 history for detail. The fixes that touch this file:
//   Fix 1 -- the public primary constructor's defaulted `KeychainStore` parameter exposed an
//            `internal` type through a public signature (EXPOSED_PARAMETER_TYPE). Fixed below by
//            moving the seam parameter onto an `internal constructor` and keeping a zero-parameter
//            public constructor as the only thing Swift/SKIE ever sees.
//   Fix 3 -- saveTokens's existence probe had a check-then-act race that could delete a valid,
//            just-written item; fixed by falling through to update() on errSecDuplicateItem
//            instead of treating it as a hard failure, and by only purging on a genuine add
//            failure (never on an update failure, since an item update() failed to overwrite
//            necessarily predates this call).
//   Fix 4 -- clearTokens misclassified errSecItemNotFound on the tombstone step as a failure;
//            fixed to treat it as self-healed like every other errSecItemNotFound (K2). A
//            surviving CLEAR failure now carries the original (first) delete's OSStatus rather
//            than the tombstone step's, since that is more diagnostically useful.
//   Fix 5 -- encodeToString was unguarded here (a SerializationException would have propagated
//            uncaught, violating K3); wrapped in runCatching below.
//
// Review round 3 (a second Opus verification pass on the round-2 fix-up commit) found two of the
// round-2 fixes above were still only partially complete; see DECISIONS_LOG.md D98/D99 for the
// full summary. The fixes that touch this file:
//   Fix A -- saveTokens's genuine-add-failure branch used to purge unconditionally, reasoning that
//            SecItemAdd is atomic so nothing else could have been deleted. True only when the
//            existence probe had actually proven the item absent; if the probe itself failed for
//            some other reason, existence was genuinely unknown, and the unconditional delete
//            could destroy a pre-existing valid item the probe simply failed to see. Now only
//            purges when the probe's own status was errSecItemNotFound.
//   Fix C -- readTokens used to treat `status == errSecSuccess, value == null` (a genuine
//            CoreFoundation-to-NSData bridging failure in Keychain.kt's copyMatching(), not a real
//            "no session") identically to a genuine empty Keychain -- silently, with no
//            KeychainFailure published. Now published as a READ failure before returning null.

/**
 * iOS Keychain-backed [TokenStorage] (`kSecClassGenericPassword`, service
 * `com.mentora.shared.tokenStorage`). Never logs a token value.
 *
 * A plain class, not an `expect`/`actual` pairing with [com.mentora.shared.auth.TokenStorage] --
 * see that interface's kdoc for why. Constructed directly by iOS platform DI
 * ([com.mentora.shared.di.platformModule]); needs no constructor argument from that call site --
 * [keychain] is the section 9.1 K6 test seam. It lives on an `internal` primary constructor
 * rather than a defaulted public-constructor parameter (Fix 1 -- see the secondary constructor's
 * kdoc below for why), so the seam itself still stays out of the generated Swift API: only the
 * zero-parameter public constructor is visible there.
 */
class IosTokenStorage internal constructor(private val keychain: KeychainStore) : TokenStorage {

    /**
     * The only constructor Swift/SKIE ever sees. [keychain] is the § 9.1 K6 test seam
     * ([KeychainStore], `internal`); it cannot be a defaulted parameter of this public
     * constructor because a public constructor exposing an `internal`-typed parameter (even with
     * a default) is `EXPOSED_PARAMETER_TYPE`, a real Kotlin compile error (Fix 1). Putting the
     * seam on the `internal` primary constructor instead sidesteps that check entirely -- an
     * `internal` constructor's parameter types are only required to be at least as visible as
     * `internal`, which `KeychainStore` already is -- and this secondary constructor, having zero
     * parameters, exposes nothing through the public API surface either way. iOS platform DI
     * ([com.mentora.shared.di.platformModule]) and `iosTest` (via test-compilation association,
     * which can see `internal` declarations of the module under test) both still resolve exactly
     * as before; only genuinely external (Swift) callers are restricted to this constructor.
     */
    constructor() : this(SecurityFrameworkKeychain())

    override suspend fun saveTokens(tokens: AuthTokens) {
        // Fix 5: never throw across the Swift boundary (K3) -- a serialization failure is
        // reported through the same OSStatus-shaped failure channel as any genuine Keychain
        // error, exactly like Keychain.kt's UTF-8-encoding failure (Fix 5's other half).
        val payload = runCatching {
            tokenStorageJson.encodeToString(
                AuthTokensPayload.serializer(),
                AuthTokensPayload(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken),
            )
        }.getOrElse {
            KeychainStatus.publish(KeychainFailure(KeychainOperation.SAVE, errSecParam))
            return
        }

        // One item, one logical write of the pair (K1): this existence probe only decides
        // whether that write is a SecItemAdd or a SecItemUpdate. K2 requires the probe's own
        // OSStatus to actually be inspected (Fix 3) rather than collapsed into a bare boolean:
        // errSecSuccess means the item is really there, errSecItemNotFound means it really
        // isn't, and anything else means the probe itself failed for some other reason (e.g. the
        // device is locked) -- in which case existence is genuinely unknown here, and it is
        // add()'s own errSecDuplicateItem response below (not this guess) that ends up resolving
        // add-vs-update correctly. probeStatus itself is kept (not just the collapsed boolean)
        // because the genuine-add-failure branch below still needs to tell "the probe proved the
        // item absent" apart from "the probe failed for some other reason" (Fix A, review round 3).
        val probeStatus = keychain.exists()
        val existsAlready = probeStatus == errSecSuccess

        if (existsAlready) {
            val status = keychain.update(payload)
            if (status == errSecSuccess) return
            // Fix 3: an update failure never purges. The item update() failed to overwrite
            // necessarily existed before this call started -- deleting it here would destroy a
            // session this call did not write and had no problem before this call began.
            KeychainStatus.publish(KeychainFailure(KeychainOperation.SAVE, status))
            return
        }

        val addStatus = keychain.add(payload)
        if (addStatus == errSecSuccess) return

        if (addStatus == errSecDuplicateItem) {
            // Fix 3: the probe said "not found" (or was inconclusive) but the item exists after
            // all -- e.g. a concurrent writer (login racing a token refresh) added it between the
            // probe and this add(). Fall through to update() instead of treating this as a hard
            // failure, so neither caller's write destroys the other's.
            val updateStatus = keychain.update(payload)
            if (updateStatus == errSecSuccess) return
            // Same reasoning as the direct-update-failure branch above: an item genuinely exists
            // here, so purging is never correct.
            KeychainStatus.publish(KeychainFailure(KeychainOperation.SAVE, updateStatus))
            return
        }

        // A genuine add failure: SecItemAdd is atomic, so nothing was written by this call --
        // but that alone does not make a purge safe. It is only provably safe when the probe
        // above proved the item was genuinely absent (probeStatus == errSecItemNotFound): in that
        // case the item this add() just failed to create is the only thing a delete() here could
        // possibly remove. If the probe instead failed for some other reason (e.g. the device
        // being locked), existence is genuinely unknown -- a pre-existing valid item could be
        // sitting there unseen by the probe, and purging would destroy a session that predates
        // this call and had nothing wrong with it (Fix A, review round 3; K3).
        if (probeStatus == errSecItemNotFound) keychain.delete()
        KeychainStatus.publish(KeychainFailure(KeychainOperation.SAVE, addStatus))
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
            result.value == null -> {
                // Fix C (review round 3): errSecSuccess with a null value can only mean the
                // CoreFoundation-to-NSData bridge inside Keychain.kt's copyMatching() failed
                // despite the underlying query succeeding (see its kdoc) -- a genuine "no stored
                // session" is always reported as errSecItemNotFound instead (K2), never as
                // errSecSuccess + null. Previously this branch did not exist, so this case fell
                // straight through to "return null" exactly like a real empty Keychain, with no
                // KeychainFailure published -- a silent failure indistinguishable from success.
                // errSecParam is reused here as the sentinel status, the same "not a real
                // platform OSStatus, a local processing failure" convention Fix 5 already
                // established for the encodeToString failure in saveTokens above.
                KeychainStatus.publish(KeychainFailure(KeychainOperation.READ, errSecParam))
                null
            }
            result.value == TOMBSTONE_VALUE -> null
            else -> decodePayload(result.value)
        }
    }

    override suspend fun clearTokens() {
        val firstDeleteStatus = keychain.delete()
        if (isDeletedOrAlreadyGone(firstDeleteStatus)) return

        val secondDeleteStatus = keychain.delete() // one retry, per K3
        if (isDeletedOrAlreadyGone(secondDeleteStatus)) return

        val tombstoneStatus = keychain.update(TOMBSTONE_VALUE)
        // Fix 4: errSecItemNotFound on the tombstone step means the same thing it means
        // everywhere else in this file (K2) -- the item is already gone, so the overwrite that
        // "failed" actually has nothing left to neutralize. Self-healed, not a failure.
        if (tombstoneStatus == errSecSuccess || tombstoneStatus == errSecItemNotFound) return

        // Both delete attempts and the tombstone overwrite all failed: the session cannot be
        // neutralized from in here. Published rather than thrown (K3) so SessionController can
        // keep this from presenting itself as a completed logout. Fix 4: carries the ORIGINAL
        // (first) delete's OSStatus, not the tombstone step's -- more diagnostically useful to
        // whoever consumes KeychainStatus.failures later (T4b, on the Mac).
        KeychainStatus.publish(KeychainFailure(KeychainOperation.CLEAR, firstDeleteStatus))
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

private val tokenStorageJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class AuthTokensPayload(val accessToken: String, val refreshToken: String)

/** The value `clearTokens()` overwrites the item with when `SecItemDelete` fails twice (K3) --
 * deliberately not valid JSON, so `readTokens()` maps it to `null` exactly like a missing item:
 * two independent ways to neutralize a session that could not be deleted outright. */
private const val TOMBSTONE_VALUE = "MENTORA_KEYCHAIN_TOMBSTONE"
