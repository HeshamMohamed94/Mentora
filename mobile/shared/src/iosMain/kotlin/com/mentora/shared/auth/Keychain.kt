package com.mentora.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecParam
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
//
// Review round 2 (Opus code review of the T1b commit) found bugs in this file too; the fixes:
//   Fix 2 -- KeychainStatus.failures was a MutableStateFlow, which drops a publish() that is
//            structurally equal to the currently-held value and never resets after a failure.
//            Switched to a MutableSharedFlow (see KeychainStatus below for the full reasoning).
//   Fix 3 -- added the exists() probe method below, a lighter-weight existence-only query (no
//            kSecReturnData) than copyMatching(), so IosTokenStorage's add-vs-update decision in
//            saveTokens no longer materializes and decrypts the previous token pair into memory
//            just to answer a yes/no question.
//   Fix 5 -- toKeychainData() used to call error(...) (an uncaught IllegalStateException) when
//            UTF-8 encoding failed, violating K3 (never throw across the Swift boundary). Now
//            returns null and add()/update() turn that into an errSecParam OSStatus instead.
//   Fix 6 -- copyMatching() used to cast the SecItemCopyMatching out-parameter with a plain
//            `as? NSData`, which neither balances the +1 Core Foundation "Create Rule" retain
//            (leaking the object every read) nor reports anything if the bridge silently fails.
//            Now uses CFBridgingRelease. This one is an inherited Phase 3 bug, not something T1b
//            introduced -- see DECISIONS_LOG.md.
//
// Review round 3 (a second Opus verification pass on the round-2 fix-up commit) found two of the
// round-2 fixes above were only partially complete, plus one new pre-existing issue; see
// DECISIONS_LOG.md D98/D99 for the full summary. The fixes that touch this file:
//   Fix B -- baseQuery() used to include kSecAttrAccessible, which is a *matchable* search
//            attribute, not just a write-time one -- putting it in the dictionary shared by every
//            search/update/delete query could make a pre-existing item invisible to all of those
//            while still colliding with add()'s primary-key match, a permanent unfixable
//            "duplicate item" loop. Now set only in add()'s own dictionary -- see baseQuery's kdoc.
//   Fix C -- copyMatching()'s Fix 6 above only balanced the CFBridgingRelease retain count; it did
//            not make a genuine bridging failure (errSecSuccess status but a null bridged value)
//            reportable. IosTokenStorage.readTokens (not this file) now tells that case apart from
//            a real "no session" and publishes a KeychainFailure for it -- see its kdoc.
//   Fix D -- resetForTest() called the @ExperimentalCoroutinesApi resetReplayCache() without the
//            @OptIn annotation every other experimental API in this file already carries; added.
//            KeychainStatus.lastFailure was also public when only iosTest (via test-compilation
//            association) needs it; narrowed to internal.
//
// Real-CI fix round (see DECISIONS_LOG.md D109): the first actual on-device launch (Phase 5 Task
// T4b's real CI run #11) crashed with a genuine, deterministic `kotlin.TypeCastException: class
// kotlinx.cinterop.CPointer cannot be cast to class platform.Foundation.NSString` inside
// baseQuery() -- every `kSecXxx` constant this file uses is a raw, un-bridged cinterop
// `CFStringRef?`/`CPointer` at the Kotlin type level, not Kotlin/Native's internal `NSString`
// object representation, even though they are toll-free-bridged at the ObjC/CF ABI level. That
// round's fix was a private `CFStringRef?.asNSString()` extension (`interpretObjCPointer`-based, a
// non-retaining "Get Rule" reinterpret cast) used at every `kSecXxx` cast/value site, applied atop
// an `NSMutableDictionary`-based query. D109 is the full incident writeup and root-cause detail.
//
// Real-CI fix round 2 (see DECISIONS_LOG.md D110): an Opus review of the D109 commit, done BEFORE
// pushing it, found that fix was correct but incomplete -- it fixed every `kSecXxx as NSString`
// cast (destination type IS an Objective-C type, so Kotlin/Native's `as` took the ObjC-aware
// `isKindOfClass:` path, which is what needed the non-retaining reinterpret), but left six
// `queryDict as CFDictionaryRef` casts untouched at every `SecItem*` call site. Those are the
// mirror-image bug: the destination type (`kotlinx.cinterop.CPointer`/`CFDictionaryRef`) is a
// plain Kotlin class, NOT an Objective-C type, so Kotlin/Native's `as` falls back to its ordinary
// Kotlin TypeInfo subtype check instead of the ObjC-aware path -- and an `NSMutableDictionary`
// instance is not a `CPointer` subtype at that level, so every one of those casts would have
// thrown `TypeCastException` too, just six lines further down the same functions, on the very next
// real CI run. The fix: `baseQuery()` and every ad-hoc dictionary in `add()`/`update()` are now
// built as genuine `CFDictionary`s via `CFDictionaryCreateMutable`/`CFDictionaryAddValue`, not
// `NSMutableDictionary` -- this sidesteps the NSObject<->CFTypeRef bridging problem for the
// dictionary itself in both directions at once, so [asNSString] (deleted by this fix round) is no
// longer needed for keys/values either. This also incidentally fixed a second, independently-found
// issue (`kCFBooleanTrue` used instead of a Kotlin `true` literal for `kSecReturnData` -- see
// D110), and `SecurityFrameworkKeychain`'s five methods now `runCatching` their bodies so a future
// interop mistake in this class degrades to an `errSecParam`-shaped `KeychainFailure` instead of
// crashing the process (K3). See D110 for the full writeup, including the explicit disclosure that
// this round's own finding (the `as CFDictionaryRef` bug) was never actually observed in a real CI
// crash -- it is a pre-emptive fix based on the same class of compiler-internals reasoning that
// found the original D109 bug, not yet confirmed by a real crash log.

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
 * [IosTokenStorage]'s zero-parameter public constructor does. All five operations act on the
 * single fixed (service, account) item K1 stores the whole [AuthTokens] pair under; there is
 * deliberately no per-account parameter, unlike the two-item Phase 3 shape this replaces.
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

    /** A lightweight existence probe (Fix 3, review round 2): `SecItemCopyMatching` with no
     * `kSecReturnData` at all, so it can answer "does the item exist" purely from the returned
     * `OSStatus` (`errSecSuccess` vs `errSecItemNotFound`) without ever materializing the item's
     * value into memory. `saveTokens` uses this only to decide add-vs-update; it is inherently
     * racy against a concurrent writer no matter which query shape it uses, so `saveTokens` never
     * trusts this alone -- `add()`'s own `errSecDuplicateItem` response is what actually settles
     * add-vs-update when this probe and reality disagree. */
    fun exists(): Int
}

/**
 * The real [KeychainStore], and the only code in this module that calls `platform.Security`
 * directly. `kSecClassGenericPassword` item, service [service] / account [account] — same
 * (service, account) Phase 3 shipped.
 *
 * K4: every write ([add]) explicitly asks for [kSecAttrAccessibleWhenUnlockedThisDeviceOnly] — the
 * same unlock requirement Phase 3's omitted attribute already defaulted to (so this does not
 * weaken locked-device protection), plus explicit non-migratability (excluded from iCloud Keychain
 * sync and from a restore onto a different device), which the omitted-attribute default did not
 * provide. Fix B (review round 3): this attribute is set only on [add]'s own dictionary, not on
 * [baseQuery] shared by every search/update/delete operation — see [baseQuery]'s kdoc for why
 * putting it there would be actively harmful, not just redundant.
 *
 * Real-CI fix round 2 (D110): every method body is wrapped in `runCatching { }.getOrElse { }` so a
 * future interop mistake in this class (an incorrect cast, a wrong CF calling convention, etc.)
 * degrades to a reportable `errSecParam`-shaped failure instead of crashing the whole process --
 * this class is the only place `platform.Security`/raw `platform.CoreFoundation` calls happen in
 * this module, so it is also the only place such a mistake could ever originate.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SecurityFrameworkKeychain(
    private val service: String = KEYCHAIN_SERVICE,
    private val account: String = KEYCHAIN_ACCOUNT,
) : KeychainStore {

    /**
     * Bridges a plain Kotlin [String] to a genuine, `+1`-owned (Core Foundation "Create Rule")
     * `CFStringRef` via `CFBridgingRetain`. The caller owns the result and must `CFRelease` it
     * exactly once -- every call site below does so immediately after handing the value to
     * `CFDictionaryAddValue` (which retains it a second time via the dictionary's own "Create
     * Rule" value callbacks, so the dictionary's ownership does not depend on this local retain
     * surviving).
     */
    private fun String.toCFStringRef(): CFStringRef = CFBridgingRetain(this as NSString) as CFStringRef

    /**
     * Builds a fresh, `+1`-owned (`CFDictionaryCreateMutable`, Core Foundation "Create Rule")
     * `CFDictionary` for the shared search/update/delete query shape: identifies the item by
     * (class, service, account) only. Fix B (review round 3): deliberately does NOT include
     * [kSecAttrAccessible] -- that attribute is a *matchable* search attribute to
     * `SecItemCopyMatching`/`SecItemUpdate`/`SecItemDelete`, not just a write-time attribute to
     * `SecItemAdd`. Including it here would mean any item whose accessibility class does not
     * exactly match [kSecAttrAccessibleWhenUnlockedThisDeviceOnly] -- e.g. a hypothetical item
     * written before this attribute was ever set, defaulting to the OS's own default accessibility
     * class -- becomes invisible to every read/update/delete query issued through this class,
     * while still colliding with [add]'s primary-key match (class+service+account) on
     * `SecItemAdd`. That combination is a permanent, self-perpetuating "duplicate item that can
     * never be found or fixed" failure loop. [kSecAttrAccessible] is added only in [add], the one
     * place it is actually meant to apply.
     *
     * Real-CI fix round 2 (D110): built as a genuine `CFDictionary` (not `NSMutableDictionary`),
     * because every `SecItem*` call site needs `... as CFDictionaryRef`, and that specific cast --
     * `NSMutableDictionary as CFDictionaryRef`, whose *destination* type (`CFDictionaryRef`, a
     * plain `kotlinx.cinterop.CPointer`) is not an Objective-C type -- takes Kotlin/Native's
     * ordinary (non-ObjC-aware) ` as` path and throws `TypeCastException` unconditionally, the
     * mirror image of the bug D109 fixed. Building the dictionary as a real `CFDictionary` from
     * the start sidesteps that bridging problem entirely, in both directions, so no cast of the
     * dictionary itself is needed anywhere below (a `CFMutableDictionaryRef` already satisfies
     * every `SecItem*` function's `CFDictionaryRef?` parameter type).
     *
     * Caller owns the returned dictionary and must `CFRelease` it exactly once when done with it,
     * on every exit path (including early returns and exceptions) -- every caller below does so in
     * a `finally` block.
     */
    private fun baseQuery(): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        // `query` is not yet owned by any caller -- if anything below throws before the `return`,
        // this function (not the caller) is responsible for releasing it, or it leaks silently.
        var built = false
        try {
            // kSecClass/kSecClassGenericPassword are raw, borrowed (Get Rule) CFStringRef
            // constants -- CFDictionaryAddValue takes CFTypeRef? on both sides, so they are used
            // directly, no bridging/casting needed (they were never the problem; the dictionary
            // type was).
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)

            val serviceRef = service.toCFStringRef()
            CFDictionaryAddValue(query, kSecAttrService, serviceRef)
            CFRelease(serviceRef)

            val accountRef = account.toCFStringRef()
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            CFRelease(accountRef)

            built = true
            return query
        } finally {
            if (!built) CFRelease(query)
        }
    }

    /** Fix 5: returns `null` instead of throwing when UTF-8 encoding fails -- a raw Kotlin
     * exception here would propagate out of a non-`@Throws` suspend function and terminate the
     * process on the Kotlin/Native side (K3). Callers turn a `null` into an `errSecParam`
     * `OSStatus`, routing the failure through the same channel as every other Keychain error. */
    private fun String.toKeychainData(): NSData? =
        (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)

    override fun add(value: String): Int = runCatching {
        val data = value.toKeychainData() ?: return@runCatching errSecParam
        val newItem = baseQuery()
        try {
            // The CFBridgingRetain result below is a fresh, locally-owned "Create Rule" bridge of
            // `data` -- same retain-then-release-after-add discipline as service/account above.
            val dataRef = CFBridgingRetain(data) as CFTypeRef
            CFDictionaryAddValue(newItem, kSecValueData, dataRef)
            CFRelease(dataRef)
            // K4, Fix B: kSecAttrAccessible belongs only on the write -- see baseQuery's kdoc for
            // why it must not also be part of the shared search/update/delete query shape. Raw,
            // borrowed (Get Rule) constant, used directly like kSecClass/kSecClassGenericPassword.
            CFDictionaryAddValue(newItem, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            SecItemAdd(newItem as CFDictionaryRef, null)
        } finally {
            CFRelease(newItem)
        }
    }.getOrElse { errSecParam }

    override fun update(value: String): Int = runCatching {
        val data = value.toKeychainData() ?: return@runCatching errSecParam
        val query = baseQuery()
        try {
            val attributesToUpdate = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )!!
            try {
                val dataRef = CFBridgingRetain(data) as CFTypeRef
                CFDictionaryAddValue(attributesToUpdate, kSecValueData, dataRef)
                CFRelease(dataRef)
                SecItemUpdate(query as CFDictionaryRef, attributesToUpdate as CFDictionaryRef)
            } finally {
                CFRelease(attributesToUpdate)
            }
        } finally {
            CFRelease(query)
        }
    }.getOrElse { errSecParam }

    override fun delete(): Int = runCatching {
        val query = baseQuery()
        try {
            SecItemDelete(query as CFDictionaryRef)
        } finally {
            CFRelease(query)
        }
    }.getOrElse { errSecParam }

    override fun exists(): Int = runCatching {
        val query = baseQuery()
        try {
            // Raw, borrowed (Get Rule) constant, used directly -- see baseQuery's kdoc.
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            // No kSecReturnData (or kSecReturnAttributes) at all: the OSStatus alone
            // (errSecSuccess vs errSecItemNotFound) fully answers "does the item exist", so the
            // out-parameter is left null rather than materializing anything (Fix 3).
            SecItemCopyMatching(query as CFDictionaryRef, null)
        } finally {
            CFRelease(query)
        }
    }.getOrElse { errSecParam }

    override fun copyMatching(): KeychainReadResult = runCatching {
        val query = baseQuery()
        try {
            // kCFBooleanTrue (D110): the real CFBoolean singleton Security.framework's query
            // validation expects, not a Kotlin `true` literal -- passing a Kotlin Boolean across
            // the ObjC/CF boundary would produce a Kotlin/Native-synthesized NSNumber-shaped
            // wrapper, not the genuine kCFBooleanTrue instance.
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

            memScoped {
                val resultRef = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query as CFDictionaryRef, resultRef.ptr)
                if (status != errSecSuccess) return@memScoped KeychainReadResult(status, null)

                // Fix 6 (inherited from Phase 3, found during review round 2): SecItemCopyMatching
                // hands back a +1-owned CFTypeRef per the Core Foundation "Create Rule" --
                // CFBridgingRelease is what both correctly bridges it into a Kotlin/Native-managed
                // NSData AND balances that retain count. A plain `as? NSData` cast on the raw
                // pointer neither balances the retain (leaking the object on every read) nor
                // reports anything if the bridge silently fails to produce an NSData.
                // CFBridgingRelease fixes the retain-balance half of that; it does NOT by itself
                // make the silent-bridging-failure case reportable -- this function still just
                // returns `status = errSecSuccess, value = null` if the bridge yields null despite
                // a successful query, indistinguishable here from a normal empty read. Fix C
                // (review round 3) closes that: IosTokenStorage.readTokens is the code that
                // actually tells the two apart and publishes a KeychainFailure for the former,
                // since only it knows that errSecSuccess + null value can never legitimately mean
                // "no session" (that case is always reported as errSecItemNotFound instead, K2).
                @Suppress("UNCHECKED_CAST")
                val data = CFBridgingRelease(resultRef.value) as? NSData
                val value = data?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
                KeychainReadResult(status, value)
            }
        } finally {
            CFRelease(query)
        }
    }.getOrElse { KeychainReadResult(errSecParam, null) }
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
 *
 * Fix 2 (review round 2): this used to be a `MutableStateFlow<KeychainFailure?>`. A `StateFlow`
 * drops a `publish()` call whose value is structurally equal to the value it already holds, so
 * two failed logouts in a row with the same `OSStatus` would have produced only ONE emission to a
 * live collector -- the second, semantically distinct failure would have shown nothing. Nothing
 * ever reset the value after a failure either, so a stale failure could appear to persist forever
 * across later successful operations. A [MutableSharedFlow] with `replay = 1` and
 * `onBufferOverflow = DROP_OLDEST` fixes both: every [publish] is a real, distinct event to every
 * currently-subscribed collector regardless of value equality (`SharedFlow` never conflates by
 * equality the way `StateFlow` does), and [lastFailure] exists purely as a `StateFlow.value`-
 * shaped convenience for synchronous, non-collecting checks (this module's tests) -- a real
 * consumer should collect [failures] directly rather than poll it.
 */
object KeychainStatus {
    private val _failures = MutableSharedFlow<KeychainFailure>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val failures: SharedFlow<KeychainFailure> = _failures.asSharedFlow()

    /** The most recently published failure, if any. See the [KeychainStatus] kdoc for why this
     * exists alongside [failures]. `internal` (Fix D, review round 3): only `iosTest` (via
     * test-compilation association, which can see `internal` declarations of the module under
     * test) actually needs synchronous access to this for assertions -- a real consumer should
     * collect [failures] directly, so this need not widen the Swift-visible API surface. */
    internal val lastFailure: KeychainFailure?
        get() = _failures.replayCache.lastOrNull()

    internal fun publish(failure: KeychainFailure) {
        _failures.tryEmit(failure)
    }

    /** Test-only reset — [KeychainStatus] is a process-wide singleton, so `iosTest` needs a way to
     * clear the last-published failure between tests instead of leaking state across them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun resetForTest() {
        _failures.resetReplayCache()
    }
}
