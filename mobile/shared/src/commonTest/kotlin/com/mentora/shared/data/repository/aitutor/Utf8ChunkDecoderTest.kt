package com.mentora.shared.data.repository.aitutor

import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8ChunkDecoderTest {

    @Test
    fun `plain ASCII decodes immediately with nothing held back`() {
        val decoder = Utf8ChunkDecoder()
        val bytes = "Hello, world!".encodeToByteArray()

        val text = decoder.decode(bytes, bytes.size)

        assertEquals("Hello, world!", text)
    }

    @Test
    fun `a 2-byte Arabic character split exactly at its boundary decodes correctly across two reads`() {
        // Arabic "درس" ("lesson") — each letter is a 2-byte UTF-8 sequence.
        val fullBytes = "درس".encodeToByteArray()
        val splitPoint = 1 // lands inside the first character's 2-byte sequence.
        val decoder = Utf8ChunkDecoder()

        val firstText = decoder.decode(fullBytes, splitPoint)
        val secondText = decoder.decode(fullBytes.copyOfRange(splitPoint, fullBytes.size), fullBytes.size - splitPoint)

        assertEquals("", firstText, "the split lead byte's sequence must be held back, not corrupted")
        assertEquals("درس", firstText + secondText)
    }

    @Test
    fun `a 3-byte character split after its first byte decodes correctly across two reads`() {
        // "€" (U+20AC) is a 3-byte UTF-8 sequence: 0xE2 0x82 0xAC.
        val fullBytes = "a€b".encodeToByteArray()
        val splitPoint = 2 // "a" (1 byte) + the euro sign's first byte only.
        val decoder = Utf8ChunkDecoder()

        val firstText = decoder.decode(fullBytes, splitPoint)
        val secondText = decoder.decode(fullBytes.copyOfRange(splitPoint, fullBytes.size), fullBytes.size - splitPoint)

        assertEquals("a", firstText, "the complete leading byte decodes immediately, the split euro sign is held back")
        assertEquals("a€b", firstText + secondText)
    }

    @Test
    fun `a 4-byte emoji split mid-sequence decodes correctly across three reads`() {
        // "🎓" (U+1F393, graduation cap) is a 4-byte UTF-8 sequence.
        val fullBytes = "🎓".encodeToByteArray()
        assertEquals(4, fullBytes.size)
        val decoder = Utf8ChunkDecoder()

        val first = decoder.decode(fullBytes.copyOfRange(0, 1), 1)
        val second = decoder.decode(fullBytes.copyOfRange(1, 3), 2)
        val third = decoder.decode(fullBytes.copyOfRange(3, 4), 1)

        assertEquals("", first + second, "an incomplete 4-byte sequence must never partially decode")
        assertEquals("🎓", first + second + third)
    }

    @Test
    fun `completeUtf8PrefixLength returns 0 for bytes that are entirely continuation bytes`() {
        val onlyContinuationBytes = byteArrayOf(0x80.toByte(), 0x81.toByte())

        assertEquals(0, completeUtf8PrefixLength(onlyContinuationBytes))
    }

    @Test
    fun `completeUtf8PrefixLength returns full length for a buffer with no trailing incomplete sequence`() {
        val bytes = "hello".encodeToByteArray()

        assertEquals(bytes.size, completeUtf8PrefixLength(bytes))
    }
}
