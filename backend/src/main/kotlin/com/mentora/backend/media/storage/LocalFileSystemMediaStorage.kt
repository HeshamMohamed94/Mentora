package com.mentora.backend.media.storage

import com.mentora.backend.common.ApiException
import com.mentora.backend.config.AppConfig
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileSystemMediaStorage(appConfig: AppConfig) : MediaStorage {
    private val storageRoot = File(appConfig.mediaStorageRoot).canonicalFile.also { it.mkdirs() }

    override suspend fun store(key: String, contentType: String, bytes: ByteReadChannel): StoredMediaInfo {
        val destination = safeFile(key)
        destination.parentFile?.mkdirs()
        val sizeLimit = sizeLimit(contentType)
        var stored = false
        return try {
            val sizeBytes = write(destination, bytes, sizeLimit)
            stored = true
            StoredMediaInfo(key, sizeBytes)
        } finally {
            if (!stored) destination.delete()
        }
    }

    override suspend fun read(key: String): MediaContent {
        val file = safeFile(key)
        if (!file.isFile) throw mediaNotFound()
        return MediaContent(file, contentType(file.extension), file.length())
    }

    override suspend fun exists(key: String): Boolean = safeFile(key).isFile

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { safeFile(key).delete() }
    }

    private fun safeFile(key: String): File {
        val resolved = File(storageRoot, key).canonicalFile
        val rootPath = storageRoot.toPath()
        if (!resolved.toPath().startsWith(rootPath)) {
            throw IllegalArgumentException("Media storage key escapes the configured root.")
        }
        return resolved
    }

    private suspend fun write(destination: File, bytes: ByteReadChannel, sizeLimit: Long): Long =
        withContext(Dispatchers.IO) {
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (!bytes.isClosedForRead) {
                    val count = bytes.readAvailable(buffer)
                    if (count == -1) break
                    total += count
                    if (total > sizeLimit) throw tooLarge()
                    output.write(buffer, 0, count)
                }
                total
            }
        }

    private fun sizeLimit(contentType: String): Long = when {
        contentType.startsWith("image/") -> 5L * 1024 * 1024
        contentType.startsWith("video/") -> 500L * 1024 * 1024
        else -> throw ApiException.Validation(fields = mapOf("contentType" to "UNSUPPORTED"))
    }

    private fun contentType(extension: String): String = when (extension.lowercase()) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> throw mediaNotFound()
    }

    private fun tooLarge() = ApiException.Validation(fields = mapOf("file" to "TOO_LARGE"))
    private fun mediaNotFound() = ApiException.NotFound("MEDIA_NOT_FOUND", "The media was not found.")
}
