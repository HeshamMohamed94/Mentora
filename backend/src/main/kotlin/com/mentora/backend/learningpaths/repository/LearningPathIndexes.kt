package com.mentora.backend.learningpaths.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureLearningPathIndexes(database: MongoDatabase) {
    val follows = database.getCollection<LearningPathFollowDocument>("learningPathFollows")
    follows.createIndex(
        compoundIndex(ascending("userId"), ascending("pathId")),
        IndexOptions().unique(true),
    )
    follows.createIndex(ascending("userId"))
}
