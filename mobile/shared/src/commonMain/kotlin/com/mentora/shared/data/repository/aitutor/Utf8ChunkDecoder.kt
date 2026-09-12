package com.mentora.shared.data.repository.aitutor

/**
 * Decodes raw bytes read off the `POST /api/v1/ai-tutor/conversation/messages` chunked
 * `text/plain` stream into `String` text incrementally, one arbitrarily-sized network read at a
 * time — mirroring what the browser's `TextDecoder(..., { stream: true })` already does for
 * Phase 2 web's `streamAiMessage()` (`web/src/lib/api/ai-tutor.ts`), which this class exists to
 * replicate in Kotlin since no such incremental decoder ships in `kotlinx-io`/Ktor.
 *
 * This matters because a single network read (a TCP segment/buffer fill) can end in the middle of
 * a multi-byte UTF-8 character — and the AI Tutor is a bilingual (English/Arabic) feature, so
 * multi-byte sequences are common, not a rare edge case. Naively calling
 * `ByteArray.decodeToString()` on each raw read independently would silently corrupt whichever
 * character straddled that read boundary into a permanent `U+FFFD` replacement character in the
 * accumulated text. [decode] instead holds back any trailing incomplete sequence at the end of
 * each read and prepends it to the next one, so a split character is decoded correctly once its
 * remaining bytes arrive.
 *
 * Not thread-safe — a single [decode] call sequence is expected per stream, matching the
 * single-collector nature of the `Flow<AiStreamResult>` it backs.
 */
internal class Utf8ChunkDecoder {
    private var pending: ByteArray = EMPTY

    /** Decodes `bytes[0 until length]`, combined with any leftover bytes held back from the
     * previous call, returning only the text that is safe to emit now (i.e. does not end mid
     * multi-byte sequence). Returns an empty string if nothing new is safe to decode yet (e.g. the
     * entire read so far is one still-incomplete multi-byte sequence). */
    fun decode(bytes: ByteArray, length: Int): String {
        val combined = if (pending.isEmpty()) bytes.copyOf(length) else pending + bytes.copyOf(length)
        val safeLength = completeUtf8PrefixLength(combined)
        val text = combined.decodeToString(0, safeLength)
        pending = if (safeLength == combined.size) EMPTY else combined.copyOfRange(safeLength, combined.size)
        return text
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}

/**
 * Returns the length of the longest prefix of [bytes] that contains only complete UTF-8
 * sequences — i.e. holds back a trailing lead byte (and any continuation bytes that follow it)
 * whose sequence is not yet fully present. A standalone, pure function so the exact boundary
 * behavior is directly unit-testable without going through [Utf8ChunkDecoder]'s stateful API.
 */
internal fun completeUtf8PrefixLength(bytes: ByteArray): Int {
    if (bytes.isEmpty()) return 0

    // Walk back from the end over continuation bytes (10xxxxxx) to find the start of the last
    // (possibly incomplete) sequence, holding back at most 3 continuation bytes — a valid UTF-8
    // sequence is at most 4 bytes total, so more than 3 trailing continuation bytes means the
    // lead byte itself is already present earlier and this loop would have stopped there.
    var leadIndex = bytes.size - 1
    var continuationBytesSeen = 0
    while (leadIndex >= 0 && continuationBytesSeen < 3 && (bytes[leadIndex].toInt() and 0xC0) == 0x80) {
        leadIndex--
        continuationBytesSeen++
    }
    if (leadIndex < 0) return 0 // nothing but continuation bytes so far — hold everything back.

    val leadByte = bytes[leadIndex].toInt() and 0xFF
    val sequenceLength = when {
        leadByte and 0x80 == 0x00 -> 1 // 0xxxxxxx — single-byte ASCII.
        leadByte and 0xE0 == 0xC0 -> 2 // 110xxxxx
        leadByte and 0xF0 == 0xE0 -> 3 // 1110xxxx
        leadByte and 0xF8 == 0xF0 -> 4 // 11110xxx
        else -> 1 // Not a valid UTF-8 lead byte — treat as already "complete" at this byte rather
        // than holding it back forever; decodeToString's lenient replacement-character behavior
        // handles the actual malformed byte.
    }
    return if (leadIndex + sequenceLength <= bytes.size) bytes.size else leadIndex
}
