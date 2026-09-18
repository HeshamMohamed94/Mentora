package com.mentora.shared.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// NOT compiled/verified on this Windows machine — the `iosTest` source set does not materialize
// without a macOS/Kotlin-Native toolchain (`kotlin.native.ignoreDisabledTargets=true` here), so
// this file is authored but unexercised until MC-1 runs `:shared:iosSimulatorArm64Test`. These
// tests exist specifically because a happy-path round trip proves nothing about the failure paths
// PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1 fixes — every scenario below drives [IosTokenStorage] through
// a [FakeKeychain] with an injected `OSStatus`.

private val TOKENS = AuthTokens(accessToken = "secret-access-value", refreshToken = "secret-refresh-value")

private const val EXISTING_PAYLOAD_JSON = "{\"accessToken\":\"old-access\",\"refreshToken\":\"old-refresh\"}"
private const val SAVED_PAYLOAD_JSON =
    "{\"accessToken\":\"secret-access-value\",\"refreshToken\":\"secret-refresh-value\"}"

class IosTokenStorageTest {

    @BeforeTest
    fun resetSharedFailureChannel() {
        // KeychainStatus is a process-wide singleton (§ 9.1 K3) — reset its last-published
        // failure between tests so they stay independent of run order.
        KeychainStatus.resetForTest()
    }

    // --- saveTokens --------------------------------------------------------------------------

    @Test
    fun `save adds a new item when none exists yet`() = runBlocking {
        val keychain = FakeKeychain(copyMatchingResults = listOf(KeychainReadResult(NOT_FOUND, null)))
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(1, keychain.addCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertEquals(0, keychain.deleteCallCount)
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `save updates the existing item instead of adding a second one`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(FakeKeychain.SUCCESS, EXISTING_PAYLOAD_JSON)),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(0, keychain.addCallCount)
        assertEquals(1, keychain.updateCallCount)
        assertEquals(SAVED_PAYLOAD_JSON, keychain.lastWrittenValue)
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `add failing surfaces a failure and purges any partial state`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(NOT_FOUND, null)),
            addStatuses = listOf(GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(1, keychain.deleteCallCount) // best-effort purge (K3)
        val failure = assertNotNull(KeychainStatus.failures.value)
        assertEquals(KeychainOperation.SAVE, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `update failing surfaces a failure and purges any partial state`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(FakeKeychain.SUCCESS, EXISTING_PAYLOAD_JSON)),
            updateStatuses = listOf(GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(1, keychain.deleteCallCount) // best-effort purge (K3)
        val failure = assertNotNull(KeychainStatus.failures.value)
        assertEquals(KeychainOperation.SAVE, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    // --- clearTokens ---------------------------------------------------------------------------

    @Test
    fun `clear succeeds outright when the first delete succeeds`() = runBlocking {
        val keychain = FakeKeychain(deleteStatuses = listOf(FakeKeychain.SUCCESS))
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(1, keychain.deleteCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `clear treats item-not-found as an already-completed delete`() = runBlocking {
        val keychain = FakeKeychain(deleteStatuses = listOf(NOT_FOUND))
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(1, keychain.deleteCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `clear self-heals via the tombstone overwrite when both deletes fail`() = runBlocking {
        val keychain = FakeKeychain(
            deleteStatuses = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS),
            updateStatuses = listOf(FakeKeychain.SUCCESS),
        )
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(2, keychain.deleteCallCount) // the original attempt plus one retry
        assertEquals(1, keychain.updateCallCount) // the tombstone overwrite
        assertNull(KeychainStatus.failures.value) // self-healed — never surfaced
    }

    @Test
    fun `clear publishes a failure when both deletes and the tombstone overwrite fail`() = runBlocking {
        val keychain = FakeKeychain(
            deleteStatuses = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS),
            updateStatuses = listOf(GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(2, keychain.deleteCallCount)
        assertEquals(1, keychain.updateCallCount)
        val failure = assertNotNull(KeychainStatus.failures.value)
        assertEquals(KeychainOperation.CLEAR, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    // --- readTokens ------------------------------------------------------------------------------

    @Test
    fun `read round-trips a previously saved pair`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(FakeKeychain.SUCCESS, SAVED_PAYLOAD_JSON)),
        )
        val storage = IosTokenStorage(keychain)

        assertEquals(TOKENS, storage.readTokens())
    }

    @Test
    fun `read returns null and publishes no failure when there is no stored session`() = runBlocking {
        val keychain = FakeKeychain(copyMatchingResults = listOf(KeychainReadResult(NOT_FOUND, null)))
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `read returns null and publishes a failure for a genuine query status`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(GENERIC_FAILURE_STATUS, null)),
        )
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        val failure = assertNotNull(KeychainStatus.failures.value)
        assertEquals(KeychainOperation.READ, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `a malformed stored payload reads as no session, not a crash or a failure`() = runBlocking {
        val keychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(FakeKeychain.SUCCESS, "not valid json")),
        )
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        assertNull(KeychainStatus.failures.value)
    }

    @Test
    fun `a tombstoned item reads as no session`() = runBlocking {
        val keychain = FakeKeychain(deleteStatuses = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS))
        val storage = IosTokenStorage(keychain)
        storage.clearTokens() // writes the tombstone via the fallback path exercised above

        val tombstonedKeychain = FakeKeychain(
            copyMatchingResults = listOf(KeychainReadResult(FakeKeychain.SUCCESS, keychain.lastWrittenValue)),
        )
        assertNull(IosTokenStorage(tombstonedKeychain).readTokens())
    }

    private fun assertNoTokenLeaked(failure: KeychainFailure) {
        val serialized = failure.toString()
        assertFalse(serialized.contains(TOKENS.accessToken))
        assertFalse(serialized.contains(TOKENS.refreshToken))
    }
}
