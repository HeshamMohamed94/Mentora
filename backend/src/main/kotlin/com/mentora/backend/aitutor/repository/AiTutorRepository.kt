package com.mentora.backend.aitutor.repository

import com.mentora.backend.common.PageRequest
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import com.mongodb.client.model.Updates.setOnInsert
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class AiTutorRepository(database: MongoDatabase) {
    private val conversations = database.getCollection<AiConversationDocument>("aiConversations")
    private val messages = database.getCollection<AiMessageDocument>("aiMessages")

    suspend fun findOrCreate(userId: ObjectId, now: Instant): AiConversationDocument =
        requireNotNull(conversations.findOneAndUpdate(
            eq("userId", userId),
            combine(
                setOnInsert("userId", userId),
                setOnInsert("createdAt", now),
                setOnInsert("lastMessageAt", null),
            ),
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
        ))

    suspend fun list(conversationId: ObjectId, page: PageRequest): List<AiMessageDocument> {
        val filters = mutableListOf<Bson>(eq("conversationId", conversationId))
        page.cursor?.let { filters += gt("_id", it) }
        return messages.find(and(filters))
            .sort(ascending("createdAt", "_id"))
            .limit(page.limit + 1)
            .toList()
    }

    suspend fun recent(conversationId: ObjectId, limit: Int): List<AiMessageDocument> =
        messages.find(eq("conversationId", conversationId))
            .sort(com.mongodb.client.model.Sorts.descending("createdAt", "_id"))
            .limit(limit)
            .toList()
            .reversed()

    suspend fun append(message: AiMessageDocument): AiMessageDocument {
        val id = requireNotNull(messages.insertOne(message).insertedId?.asObjectId()?.value)
        conversations.updateOne(eq("_id", message.conversationId), set("lastMessageAt", message.createdAt))
        return message.copy(id = id)
    }
}
