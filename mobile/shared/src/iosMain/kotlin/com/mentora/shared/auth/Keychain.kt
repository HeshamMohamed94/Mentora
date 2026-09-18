package com.mentora.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// NOT compiled/verified on this Windows machine — iosArm64/iosSimulatorArm64 require a macOS
// host. See "Disclosed limitation B1" in execution/PHASE_3_KMP_PLAN.md.
//
// Phase 5 Task T1b (PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1, K6): the raw `SecItem*` C APIs cannot be
// mocked, so every genuine-Keychain-failure path in IosTokenStorage was untestable as the Phase 3
// file was written. This file is the seam that fixes that: [KeychainStore] is the interface
// [IosTokenStorage] actually talks to, [SecurityFrameworkKeychain] is the only code in this module
// that calls `platform.Security` directly, and `iosTest`'s `FakeKeychain` is the other
// implementation (failure-injectable, no real Keychain involved).

/** Same (service, account) shape Phase 3 shipped for the single Keychain item this module owns —
 * preserved verbatim per T1b's "same Keychain class and service" constraint. K1: the access/
 * refresh pair is stored as one JSON value under this one (service, account), never as two
 * separately-mutated items, so a partial pair is structurally impossible rather than compensated
 * for after the fact. */
internal const val KEYCHAIN_SERVICE = "com.mentora.shared.tokenStorage"
private const val KEYCHAIN_ACCOUNT = "authTokens"

/**
 * The outcome of a `SecItemCopyMatching` call: [status] is the raw `OSStatus` (K2 — inspected,
 * never discarded); [value] is the decoded UTF-8 string only when [status] is
 * [platform.Security.errSecSuccess] (`null` otherwise, including for the expected
 * `errSecItemNotFound` "no stored session" case).
 */
internal data class KeychainReadResult(val status: Int, val value: String?)

/**
 * The Keychain seam [IosTokenStorage] talks to instead of calling `platform.Security` itself
 * (§ 9.1 K6). `internal`, so it never appears in the generated Swift API surface — only
 * [IosTokenStorage]'s defaulted public constructor parameter does, and SKIE only sees that
 * default, not this type. All four operations act on the single fixed (service, account) item K1
 * stores the whole [AuthTokens] pair under; there is deliberately no per-account parameter, unlike
 * the two-item Phase 3 shape this replaces.
 */
internal interface KeychainStore {
    /** `SecItemAdd` for a brand-new item holding [value]. Returns the raw `OSStatus`. */
    fun add(value: String): Int

    /** `SecItemUpdate` on the existing item, replacing its value with [value]. Returns the raw
     * `OSStatus`. */
    fun update(value: String): Int

    /** `SecItemDelete` on the item. Returns the raw `OSStatus` (`errSecItemNotFound` is not an
     * error here — it means the item is already gone). */
    fun delete(): Int

    /** `SecItemCopyMatching` for the item's value. See [KeychainReadResult]. */
    fun copyMatching(): KeychainReadResult
}

/**
 * The real [KeychainStore], and the only code in this module that calls `platform.Security`
 * directly. `kSecClassGenericPassword` item, service [service] / account [account] — same
 * (service, account) Phase 3 shipped.
 *
 * K4: every query starts from [baseQuery], which explicitly asks for
 * [kSecAttrAccessibleWhenUnlockedThisDeviceOnly] — the same unlock requirement Phase 3's omitted
 * attribute already defaulted to (so this does not weaken locked-device protection), plus explicit
 * non-migratability (excluded from iCloud Keychain sync and from a restore onto a different
 * device), which the omitted-attribute default did not provide.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SecurityFrameworkKeychain(
    private val service: String = KEYCHAIN_SERVICE,
    private val account: String = KEYCHAIN_ACCOUNT,
) : KeychainStore {

    private fun baseQuery(): NSMutableDictionary {
        val query = NSMutableDictionary()
        query.setObject(kSecClassGenericPassword, forKey = kSecClass as NSString)
        query.setObject(service, forKey = kSecAttrService as NSString)
        query.setObject(account, forKey = kSecAttrAccount as NSString)
        query.setObject(
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            forKey = kSecAttrAccessible as NSString,
        )
        return query
    }

    private fun String.toKeychainData(): NSData =
        (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Failed to UTF-8 encode a Keychain value")

    override fun add(value: String): Int {
        val newItem = baseQuery()
        newItem.setObject(value.toKeychainData(), forKey = kSecValueData as NSString)
        return SecItemAdd(newItem as CFDictionaryRef, null)
    }

    override fun update(value: String): Int {
        val attributesToUpdate = NSMutableDictionary()
        attributesToUpdate.setObject(value.toKeychainData(), forKey = kSecValueData as NSString)
        return SecItemUpdate(baseQuery() as CFDictionaryRef, attributesToUpdate as CFDictionaryRef)
    }

    override fun delete(): Int = SecItemDelete(baseQuery() as CFDictionaryRef)

    override fun copyMatching(): KeychainReadResult {
        val query = baseQuery()
        query.setObject(true, forKey = kSecReturnData as NSString)
        query.setObject(kSecMatchLimitOne, forKey = kSecMatchLimit as NSString)

        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, resultRef.ptr)
            if (status != errSecSuccess) return@memScoped KeychainReadResult(status, null)

            @Suppress("UNCHECKED_CAST")
            val data = resultRef.value as? NSData
            val value = data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
            KeychainReadResult(status, value)
        }
    }
}

/** Which [IosTokenStorage] operation a [KeychainFailure] came from. Diagnostic only. */
enum class KeychainOperation {
    SAVE,
    CLEAR,
    READ,
}

/**
 * A genuine (non-`errSecItemNotFound`) Keychain `OSStatus` that survived every recovery
 * [IosTokenStorage] attempts (§ 9.1 K2/K3). Carries [status] — the raw `OSStatus` — and nothing
 * else: never a token value, by construction (`architecture/AUTH_SECURITY.md § 4`) — there is no
 * field here one could be put in.
 */
data class KeychainFailure(
    val operation: KeychainOperation,
    val status: Int,
)

/**
 * The one in-band channel a surviving Keychain failure is published on, because
 * `TokenStorage.saveTokens`/`clearTokens` are plain `commonMain` `suspend fun`s with no
 * `@Throws`, and a raw Kotlin exception crossing into Swift terminates the process — and adding
 * `@Throws` would itself be a `commonMain` public-API change A6 forbids for this phase (§ 9.1 K3).
 * `SessionController` (T4b, iOS-side) is expected to subscribe to [failures] exactly once at app
 * launch: this repo's sixth sanctioned non-façade entry point (A2).
 */
object KeychainStatus {
    private val _failures = MutableStateFlow<KeychainFailure?>(null)

    val failures: StateFlow<KeychainFailure?> = _failures.asStateFlow()

    internal fun publish(failure: KeychainFailure) {
        _failures.value = failure
    }

    /** Test-only reset — [KeychainStatus] is a process-wide singleton, so `iosTest` needs a way to
     * clear the last-published failure between tests instead of leaking state across them. */
    internal fun resetForTest() {
        _failures.value = null
    }
}
