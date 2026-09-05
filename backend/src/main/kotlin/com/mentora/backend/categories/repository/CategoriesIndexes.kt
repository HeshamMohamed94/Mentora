package com.mentora.backend.categories.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureCategoriesIndexes(database: MongoDatabase) {
    database.getCollection<CategoryDocument>("categories")
        .createIndex(ascending("slug"), IndexOptions().unique(true))
}
