package com.mentora.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.AuthTokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T4 Part D — closes the one Phase 3 limitation explicitly assigned to Phase 4
 * (`execution/PHASE_HANDOFF.md`: "the REAL AES-256-GCM/Android-Keystore code path has never run
 * against a real Android Keystore provider; no Robolectric..."). A real `androidTest`
 * (`connectedDebugAndroidTest`) — runs on an actual device/emulator against the real
 * `AndroidKeyStore` provider, NOT the JVM-only `test`/`androidUnitTest` surface `:shared` uses
 * everywhere else.
 *
 * **Tamper-detection sub-case — honestly not covered here.** [AndroidTokenStorage] exposes only
 * `saveTokens`/`readTokens`/`clearTokens`; there is no lower-level API surface this test (living in
 * a different Gradle module, `:androidApp`) can reach to feed deliberately-corrupted ciphertext
 * through the REAL encrypt/decrypt path without reaching into that class's private
 * implementation (its Keystore key alias, DataStore file/key name, and Base64/JSON payload shape
 * are all private to it). Directly corrupting the underlying DataStore file's on-disk bytes from
 * outside the class was considered and rejected: doing that would only prove
 * `Base64.decode`/`kotlinx.serialization` can throw on garbage input, not that AES-GCM's own
 * authentication-tag check is what rejects the tampered ciphertext — the actual thing worth
 * proving. Genuinely exercising that would need either a constructor seam `AndroidTokenStorage`
 * doesn't have (an injectable `Cipher`/`KeyStore`) or duplicating its private Keystore/DataStore
 * logic here just to write bad ciphertext under the same key — both amount to reimplementing
 * internals a test at this layer shouldn't need to know about. Left honestly uncovered rather than
 * faked with a test that doesn't really exercise tamper detection. The three round-trip tests below
 * are what actually close the disclosed gap: proving the real Keystore-backed save/read/clear path
 * works end-to-end on a real device.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTokenStorageInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun saveTokens_thenReadTokens_roundTripsExactly() = runBlocking {
        val storage = AndroidTokenStorage(context)
        storage.clearTokens() // start from a clean slate regardless of any prior run's leftovers

        val tokens = AuthTokens(
            accessToken = "test-access-token-${System.nanoTime()}",
            refreshToken = "test-refresh-token-${System.nanoTime()}",
        )
        storage.saveTokens(tokens)

        assertEquals(tokens, storage.readTokens())

        storage.clearTokens()
    }

    @Test
    fun clearTokens_thenReadTokens_returnsNull() = runBlocking {
        val storage = AndroidTokenStorage(context)
        storage.saveTokens(AuthTokens(accessToken = "will-be-cleared", refreshToken = "will-be-cleared"))

        storage.clearTokens()

        assertNull(storage.readTokens())
    }

    @Test
    fun readTokens_withNothingEverSaved_returnsNull() = runBlocking {
        val storage = AndroidTokenStorage(context)
        storage.clearTokens() // ensure no leftover state from a previous test run

        assertNull(storage.readTokens())
    }
}
