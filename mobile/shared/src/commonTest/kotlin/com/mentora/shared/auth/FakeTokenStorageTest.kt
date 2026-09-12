package com.mentora.shared.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeTokenStorageTest {

    @Test
    fun `read returns null before anything has been saved`() = runBlocking {
        val storage = FakeTokenStorage()

        assertNull(storage.readTokens())
    }

    @Test
    fun `save then read round-trips the exact tokens`() = runBlocking {
        val storage = FakeTokenStorage()
        val tokens = AuthTokens(accessToken = "access-123", refreshToken = "refresh-456")

        storage.saveTokens(tokens)

        assertEquals(tokens, storage.readTokens())
    }

    @Test
    fun `saving again overwrites the previous tokens`() = runBlocking {
        val storage = FakeTokenStorage()
        storage.saveTokens(AuthTokens(accessToken = "old-access", refreshToken = "old-refresh"))

        val rotated = AuthTokens(accessToken = "new-access", refreshToken = "new-refresh")
        storage.saveTokens(rotated)

        assertEquals(rotated, storage.readTokens())
    }

    @Test
    fun `clear removes the stored tokens`() = runBlocking {
        val storage = FakeTokenStorage()
        storage.saveTokens(AuthTokens(accessToken = "access-123", refreshToken = "refresh-456"))

        storage.clearTokens()

        assertNull(storage.readTokens())
    }
}
