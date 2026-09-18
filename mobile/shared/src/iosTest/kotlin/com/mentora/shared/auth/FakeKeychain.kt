package com.mentora.shared.auth

// NOT compiled/verified on this Windows machine — the `iosTest` source set does not materialize
// without a macOS/Kotlin-Native toolchain (`kotlin.native.ignoreDisabledTargets=true` here), so
// this file is authored but unexercised until MC-1 runs `:shared:iosSimulatorArm64Test`.

/**
 * Test double for [KeychainStore] (§ 9.1 K6) — the raw `SecItem*` C APIs cannot themselves be
 * mocked, so [IosTokenStorageTest] talks to this instead.
 *
 * Review round 2 (Fix 7): rewritten from a purely script-driven fake (an `OSStatus` queue per
 * method, oblivious to what was actually "written") to a small in-memory, single-item-backed fake
 * that genuinely tracks whether the one Keychain item this module owns exists, and derives
 * realistic `OSStatus` results from that state — exactly like the real Keychain does (`add` after
 * an existing item really does return `errSecDuplicateItem`; `update`/`delete` on an absent item
 * really does return `errSecItemNotFound`). This is what makes Fix 3's
 * add-returns-duplicate-falls-through-to-update path and Fix 4's tombstone-not-found path actually
 * testable: a purely scripted fake can return any status for any call, but it can't express "this
 * status follows from that state transition" the way the real Keychain's behavior does.
 *
 * A test can still inject an arbitrary failure status for a specific call (the existing
 * failure-injection tests all rely on this) via the `*Overrides` queues passed to the constructor:
 * when a queue for a method has a value queued, that value is returned verbatim for that call
 * *instead of* the state-derived status, and the underlying state is left untouched by that call
 * (mirroring how a real failed Keychain call leaves the previous item state alone). Once a queue
 * is exhausted, the state-derived status applies.
 */
internal class FakeKeychain(
    initiallyStored: String? = null,
    addOverrides: List<Int> = emptyList(),
    updateOverrides: List<Int> = emptyList(),
    deleteOverrides: List<Int> = emptyList(),
    existsOverrides: List<Int> = emptyList(),
    copyMatchingOverrides: List<Int> = emptyList(),
) : KeychainStore {

    private val addQueue = ArrayDeque(addOverrides)
    private val updateQueue = ArrayDeque(updateOverrides)
    private val deleteQueue = ArrayDeque(deleteOverrides)
    private val existsQueue = ArrayDeque(existsOverrides)
    private val copyMatchingQueue = ArrayDeque(copyMatchingOverrides)

    private var storedValue: String? = initiallyStored

    var addCallCount = 0
        private set
    var updateCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var existsCallCount = 0
        private set
    var copyMatchingCallCount = 0
        private set

    /** The value most recently handed to [add]/[update] — lets a test assert what would have been
     * persisted without standing up a real Keychain. */
    var lastWrittenValue: String? = null
        private set

    /** The item's current state, for tests asserting what survives a sequence of calls (e.g. a
     * save -> clear -> save round trip). `null` means "no item stored". */
    val currentValue: String?
        get() = storedValue

    override fun add(value: String): Int {
        addCallCount++
        addQueue.removeFirstOrNull()?.let { return it }

        if (storedValue != null) return DUPLICATE_ITEM // real SecItemAdd semantics: already exists
        storedValue = value
        lastWrittenValue = value
        return SUCCESS
    }

    override fun update(value: String): Int {
        updateCallCount++
        updateQueue.removeFirstOrNull()?.let { return it }

        if (storedValue == null) return NOT_FOUND // real SecItemUpdate semantics: nothing to update
        storedValue = value
        lastWrittenValue = value
        return SUCCESS
    }

    override fun delete(): Int {
        deleteCallCount++
        deleteQueue.removeFirstOrNull()?.let { return it }

        if (storedValue == null) return NOT_FOUND // real SecItemDelete semantics: already gone
        storedValue = null
        return SUCCESS
    }

    override fun exists(): Int {
        existsCallCount++
        existsQueue.removeFirstOrNull()?.let { return it }
        return if (storedValue != null) SUCCESS else NOT_FOUND
    }

    override fun copyMatching(): KeychainReadResult {
        copyMatchingCallCount++
        copyMatchingQueue.removeFirstOrNull()?.let { overrideStatus ->
            return KeychainReadResult(overrideStatus, if (overrideStatus == SUCCESS) storedValue else null)
        }
        val value = storedValue
        return if (value != null) KeychainReadResult(SUCCESS, value) else KeychainReadResult(NOT_FOUND, null)
    }

    companion object {
        /** `errSecSuccess`. Duplicated here (rather than imported from `platform.Security`) so
         * this file has zero Security-framework dependency beyond the raw `OSStatus` integer
         * contract [KeychainStore] already commits to. */
        const val SUCCESS = 0

        /** `errSecDuplicateItem` — the real `SecItemAdd` status when an item already exists.
         * Duplicated here for the same zero-Security-framework-dependency reason as [SUCCESS]. */
        const val DUPLICATE_ITEM = -25299
    }
}

/** `errSecItemNotFound` — the expected "no stored session" `OSStatus` (§ 9.1 K2). Duplicated here
 * for the same zero-Security-framework-dependency reason as [FakeKeychain.SUCCESS]. */
internal const val NOT_FOUND = -25300

/** An arbitrary genuine (non-success, non-not-found, non-duplicate) `OSStatus` for tests that need
 * one — `errSecAuthFailed`'s real value, reused as a stand-in "some real Keychain failure
 * happened". */
internal const val GENERIC_FAILURE_STATUS = -25293

/** A second, distinct genuine-failure `OSStatus` — `errSecMissingEntitlement`'s real value — for
 * tests that need to tell two different failing calls apart (e.g. confirming which of several
 * failing `OSStatus`es actually gets published; see Fix 4's "original delete status" test). */
internal const val ANOTHER_FAILURE_STATUS = -34018
