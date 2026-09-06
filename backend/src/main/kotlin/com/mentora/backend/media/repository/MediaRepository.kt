package com.mentora.backend.media.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId

class MediaRepository(database: MongoDatabase) {
    private val media = database.getCollection<MediaDocument>("media")

    suspend fun insert(document: MediaDocument): MediaDocument {
        val id = requireNotNull(media.insertOne(document).insertedId?.asObjectId()?.value)
        return document.copy(id = id)
    }

    suspend fun findById(id: ObjectId): MediaDocument? = media.find(eq("_id", id)).firstOrNull()
}
