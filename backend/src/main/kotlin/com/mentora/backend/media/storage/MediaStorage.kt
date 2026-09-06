package com.mentora.backend.media.storage

import io.ktor.utils.io.ByteReadChannel
import java.io.File

interface MediaStorage {
    suspend fun store(key: String, contentType: String, bytes: ByteReadChannel): StoredMediaInfo
    suspend fun read(key: String): MediaContent
    suspend fun exists(key: String): Boolean
    suspend fun delete(key: String)
}

data class StoredMediaInfo(val storageKey: String, val sizeBytes: Long)
data class MediaContent(val file: File, val contentType: String, val sizeBytes: Long)
