package com.mentora.shared.auth

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
// a [FakeKeychain] with an injected `OSStatus` and/or pre-existing state.
//
// Review round 2 (Fix 7): FakeKeychain became a genuinely stateful fake (see its kdoc), which
// changed several tests here -- most notably "update failing" no longer expects a purge (Fix 3:
// an update failure never deletes a pre-existing valid item) -- and added new tests for Fix 3's
// duplicate-item fallthrough, Fix 4's tombstone-not-found self-heal, and Fix 2's SharedFlow
// delivery of consecutive structurally-identical failures.

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
        val keychain = FakeKeychain()
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(1, keychain.addCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertEquals(0, keychain.deleteCallCount)
        assertEquals(SAVED_PAYLOAD_JSON, keychain.currentValue)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `save updates the existing item instead of adding a second one`() = runBlocking {
        val keychain = FakeKeychain(initiallyStored = EXISTING_PAYLOAD_JSON)
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(0, keychain.addCallCount)
        assertEquals(1, keychain.updateCallCount)
        assertEquals(SAVED_PAYLOAD_JSON, keychain.lastWrittenValue)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `add failing surfaces a failure and purges any partial state`() = runBlocking {
        val keychain = FakeKeychain(addOverrides = listOf(GENERIC_FAILURE_STATUS))
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        // A plain add failure never wrote anything (SecItemAdd is atomic), so purging here is
        // safe -- it can only remove something this call itself just failed to create (Fix 3).
        assertEquals(1, keychain.deleteCallCount)
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.SAVE, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `update failing surfaces a failure without deleting the pre-existing valid item`() = runBlocking {
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            updateOverrides = listOf(GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        // Fix 3: an update failure never purges -- the item update() failed to overwrite
        // predates this call, so deleting it would destroy a session this call did not write.
        assertEquals(0, keychain.deleteCallCount)
        assertEquals(EXISTING_PAYLOAD_JSON, keychain.currentValue)
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.SAVE, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `save falls through to update when add reports a duplicate item`() = runBlocking {
        // The probe (exists()) says "not found" -- stale/racy -- but the item genuinely already
        // exists in the backing store, so add() genuinely returns errSecDuplicateItem, exactly
        // like a concurrent writer (e.g. login racing a token refresh) racing this save (Fix 3).
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            existsOverrides = listOf(NOT_FOUND),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(1, keychain.addCallCount)
        assertEquals(1, keychain.updateCallCount)
        assertEquals(0, keychain.deleteCallCount) // neither caller's write is destroyed
        assertEquals(SAVED_PAYLOAD_JSON, keychain.currentValue)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `save does not delete an existing valid item when the existence probe itself fails`() = runBlocking {
        // The probe fails for a genuine reason (e.g. errSecInteractionNotAllowed) while a valid
        // item already exists. existsAlready is computed as false, add() is attempted, and add()
        // itself reports errSecDuplicateItem -- the fallthrough to update() must be what resolves
        // this, not a delete (Fix 3's second bug).
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            existsOverrides = listOf(GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(0, keychain.deleteCallCount) // the existing item must survive a probe failure
        assertEquals(SAVED_PAYLOAD_JSON, keychain.currentValue)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `save does not purge an existing valid item when both the probe and the add itself fail`() = runBlocking {
        // Distinct from the duplicate-item fallthrough test above: here add() does NOT report
        // errSecDuplicateItem, so nothing resolves the probe's false "not found" reading before
        // reaching the genuine-add-failure branch. Before Fix A (review round 3) that branch
        // purged unconditionally on the theory that SecItemAdd is atomic so nothing else could be
        // deleted -- true only when the probe had actually proven the item absent. Here the probe
        // failed for a genuine, unrelated reason while a valid item was sitting there the whole
        // time, so the purge must not happen and that item must survive untouched.
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            existsOverrides = listOf(GENERIC_FAILURE_STATUS),
            addOverrides = listOf(ANOTHER_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)

        assertEquals(0, keychain.deleteCallCount) // the pre-existing valid item must survive
        assertEquals(EXISTING_PAYLOAD_JSON, keychain.currentValue) // untouched
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.SAVE, failure.operation)
        assertEquals(ANOTHER_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    // --- clearTokens ---------------------------------------------------------------------------

    @Test
    fun `clear succeeds outright when the first delete succeeds`() = runBlocking {
        val keychain = FakeKeychain(initiallyStored = EXISTING_PAYLOAD_JSON)
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(1, keychain.deleteCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertNull(keychain.currentValue)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `clear treats item-not-found as an already-completed delete`() = runBlocking {
        val keychain = FakeKeychain() // nothing stored -- delete() naturally reports not-found
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(1, keychain.deleteCallCount)
        assertEquals(0, keychain.updateCallCount)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `clear self-heals via the tombstone overwrite when both deletes fail`() = runBlocking {
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            deleteOverrides = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(2, keychain.deleteCallCount) // the original attempt plus one retry
        assertEquals(1, keychain.updateCallCount) // the tombstone overwrite
        assertNull(KeychainStatus.lastFailure) // self-healed — never surfaced
    }

    @Test
    fun `clear self-heals when the tombstone overwrite itself reports item-not-found`() = runBlocking {
        // Nothing stored, so the state-derived tombstone update() naturally returns
        // errSecItemNotFound -- e.g. the item was concurrently removed between the failed
        // deletes and the tombstone attempt. Fix 4: treated the same as every other
        // errSecItemNotFound (K2), not as a failure.
        val keychain = FakeKeychain(deleteOverrides = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS))
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(2, keychain.deleteCallCount)
        assertEquals(1, keychain.updateCallCount)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `clear publishes a failure carrying the original delete status when everything fails`() = runBlocking {
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            deleteOverrides = listOf(GENERIC_FAILURE_STATUS, ANOTHER_FAILURE_STATUS),
            updateOverrides = listOf(ANOTHER_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)

        storage.clearTokens()

        assertEquals(2, keychain.deleteCallCount)
        assertEquals(1, keychain.updateCallCount)
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.CLEAR, failure.operation)
        // Fix 4: the ORIGINAL (first) delete's status, not the tombstone step's.
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    // --- readTokens ------------------------------------------------------------------------------

    @Test
    fun `read round-trips a previously saved pair`() = runBlocking {
        val keychain = FakeKeychain(initiallyStored = SAVED_PAYLOAD_JSON)
        val storage = IosTokenStorage(keychain)

        assertEquals(TOKENS, storage.readTokens())
    }

    @Test
    fun `read returns null and publishes no failure when there is no stored session`() = runBlocking {
        val keychain = FakeKeychain()
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `read returns null and publishes a failure for a genuine query status`() = runBlocking {
        val keychain = FakeKeychain(copyMatchingOverrides = listOf(GENERIC_FAILURE_STATUS))
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.READ, failure.operation)
        assertEquals(GENERIC_FAILURE_STATUS, failure.status)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `read publishes a failure when the query succeeds but the bridged value is null`() = runBlocking {
        // Fix C (review round 3): errSecSuccess with a null value can only be a genuine
        // CoreFoundation-to-NSData bridging failure (see Keychain.kt copyMatching()'s kdoc) -- a
        // real "no stored session" is always reported as errSecItemNotFound instead (K2), never
        // as errSecSuccess + null. Nothing is actually stored here; forcing the SUCCESS override
        // reproduces exactly the status-without-a-value combination the real bridge can silently
        // produce, which used to fall straight through to "no session" with nothing published.
        val keychain = FakeKeychain(copyMatchingOverrides = listOf(FakeKeychain.SUCCESS))
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        val failure = assertNotNull(KeychainStatus.lastFailure)
        assertEquals(KeychainOperation.READ, failure.operation)
        assertNoTokenLeaked(failure)
    }

    @Test
    fun `a malformed stored payload reads as no session, not a crash or a failure`() = runBlocking {
        val keychain = FakeKeychain(initiallyStored = "not valid json")
        val storage = IosTokenStorage(keychain)

        assertNull(storage.readTokens())
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `a tombstoned item reads as no session`() = runBlocking {
        val keychain = FakeKeychain(
            initiallyStored = EXISTING_PAYLOAD_JSON,
            deleteOverrides = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)
        storage.clearTokens() // both deletes fail -> tombstone overwrite (self-heals, Fix 4)

        assertNull(storage.readTokens())
    }

    // --- state transitions & KeychainStatus delivery --------------------------------------------

    @Test
    fun `a save, clear, save round trip transitions state correctly across all three calls`() = runBlocking {
        val keychain = FakeKeychain()
        val storage = IosTokenStorage(keychain)

        storage.saveTokens(TOKENS)
        assertEquals(SAVED_PAYLOAD_JSON, keychain.currentValue)
        assertEquals(1, keychain.addCallCount)
        assertEquals(0, keychain.updateCallCount)

        storage.clearTokens()
        assertNull(keychain.currentValue)
        assertEquals(1, keychain.deleteCallCount)

        val secondTokens = AuthTokens(accessToken = "second-access-value", refreshToken = "second-refresh-value")
        val secondPayloadJson =
            "{\"accessToken\":\"second-access-value\",\"refreshToken\":\"second-refresh-value\"}"
        storage.saveTokens(secondTokens)

        assertEquals(secondPayloadJson, keychain.currentValue)
        assertEquals(2, keychain.addCallCount) // a fresh add, since clear genuinely removed the item
        assertEquals(0, keychain.updateCallCount)
        assertNull(KeychainStatus.lastFailure)
    }

    @Test
    fun `two consecutive structurally-identical failures both reach a live observer`() = runBlocking {
        // Fix 2: a MutableStateFlow would have conflated these into a single emission because
        // they are structurally equal. A collector actively subscribed to KeychainStatus.failures
        // for both clearTokens() calls must see two distinct events.
        val keychain = FakeKeychain(
            deleteOverrides = listOf(
                GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS,
                GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS,
            ),
            updateOverrides = listOf(GENERIC_FAILURE_STATUS, GENERIC_FAILURE_STATUS),
        )
        val storage = IosTokenStorage(keychain)
        val observed = mutableListOf<KeychainFailure>()
        val collectorJob = launch { KeychainStatus.failures.collect { observed.add(it) } }
        yield() // let the collector actually subscribe before anything is published

        storage.clearTokens() // publishes CLEAR failure #1
        storage.clearTokens() // publishes a structurally-identical CLEAR failure #2
        yield() // let the collector drain both emissions

        collectorJob.cancel()

        assertEquals(2, observed.size)
        assertEquals(observed[0], observed[1]) // structurally identical, yet both delivered
    }

    private fun assertNoTokenLeaked(failure: KeychainFailure) {
        val serialized = failure.toString()
        assertFalse(serialized.contains(TOKENS.accessToken))
        assertFalse(serialized.contains(TOKENS.refreshToken))
    }
}
