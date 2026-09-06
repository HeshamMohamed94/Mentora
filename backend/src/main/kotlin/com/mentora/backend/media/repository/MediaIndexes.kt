package com.mentora.backend.media.repository

import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureMediaIndexes(database: MongoDatabase) {
    database.getCollection<MediaDocument>("media")
        .createIndex(compoundIndex(ascending("ownerRefId"), ascending("kind")))
}
