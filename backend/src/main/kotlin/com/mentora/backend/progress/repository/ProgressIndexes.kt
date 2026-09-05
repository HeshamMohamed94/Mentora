package com.mentora.backend.progress.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureProgressIndexes(database: MongoDatabase) {
    val progress = database.getCollection<ProgressDocument>("progress")
    progress.createIndex(
        compoundIndex(ascending("userId"), ascending("courseId")),
        IndexOptions().unique(true),
    )
    progress.createIndex(ascending("userId"))
}
