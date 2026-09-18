package com.mentora.shared.auth

// NOT compiled/verified on this Windows machine — the `iosTest` source set does not materialize
// without a macOS/Kotlin-Native toolchain (`kotlin.native.ignoreDisabledTargets=true` here), so
// this file is authored but unexercised until MC-1 runs `:shared:iosSimulatorArm64Test`.

/**
 * Test double for [KeychainStore] (§ 9.1 K6) — the raw `SecItem*` C APIs cannot themselves be
 * mocked, so [IosTokenStorageTest] talks to this instead. Every method's `OSStatus` (or, for
 * [copyMatching], full [KeychainReadResult]) is driven by a queue, so a test can script an exact
 * call-by-call sequence — e.g. "the first delete fails, the retry also fails, the tombstone
 * overwrite succeeds." Once a queue is exhausted, [SUCCESS] (or, for [copyMatching],
 * "not found") is returned, matching the least surprising default for an unscripted extra call.
 */
internal class FakeKeychain(
    addStatuses: List<Int> = emptyList(),
    updateStatuses: List<Int> = emptyList(),
    deleteStatuses: List<Int> = emptyList(),
    copyMatchingResults: List<KeychainReadResult> = emptyList(),
) : KeychainStore {

    private val addQueue = ArrayDeque(addStatuses)
    private val updateQueue = ArrayDeque(updateStatuses)
    private val deleteQueue = ArrayDeque(deleteStatuses)
    private val copyMatchingQueue = ArrayDeque(copyMatchingResults)

    var addCallCount = 0
        private set
    var updateCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var copyMatchingCallCount = 0
        private set

    /** The value most recently handed to [add]/[update] — lets a test assert what would have been
     * persisted without standing up a real Keychain. */
    var lastWrittenValue: String? = null
        private set

    override fun add(value: String): Int {
        addCallCount++
        lastWrittenValue = value
        return addQueue.removeFirstOrNull() ?: SUCCESS
    }

    override fun update(value: String): Int {
        updateCallCount++
        lastWrittenValue = value
        return updateQueue.removeFirstOrNull() ?: SUCCESS
    }

    override fun delete(): Int {
        deleteCallCount++
        return deleteQueue.removeFirstOrNull() ?: SUCCESS
    }

    override fun copyMatching(): KeychainReadResult {
        copyMatchingCallCount++
        return copyMatchingQueue.removeFirstOrNull() ?: KeychainReadResult(NOT_FOUND, null)
    }

    companion object {
        /** `errSecSuccess`. Duplicated here (rather than imported from `platform.Security`) so
         * this file has zero Security-framework dependency beyond the raw `OSStatus` integer
         * contract [KeychainStore] already commits to. */
        const val SUCCESS = 0
    }
}

/** `errSecItemNotFound` — the expected "no stored session" `OSStatus` (§ 9.1 K2). Duplicated here
 * for the same zero-Security-framework-dependency reason as [FakeKeychain.SUCCESS]. */
internal const val NOT_FOUND = -25300

/** An arbitrary genuine (non-success, non-not-found) `OSStatus` for tests that need one —
 * `errSecAuthFailed`'s real value, reused as a stand-in "some real Keychain failure happened". */
internal const val GENERIC_FAILURE_STATUS = -25293
