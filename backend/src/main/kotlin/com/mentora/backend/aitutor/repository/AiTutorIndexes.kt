package com.mentora.backend.aitutor.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureAiTutorIndexes(database: MongoDatabase) {
    database.getCollection<AiConversationDocument>("aiConversations")
        .createIndex(ascending("userId"), IndexOptions().unique(true))
    database.getCollection<AiMessageDocument>("aiMessages")
        .createIndex(compoundIndex(ascending("conversationId"), ascending("createdAt")))
}
