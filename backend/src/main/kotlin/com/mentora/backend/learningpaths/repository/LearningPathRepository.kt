package com.mentora.backend.learningpaths.repository

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.setOnInsert
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

class LearningPathRepository(database: MongoDatabase) {
    private val paths = database.getCollection<LearningPathDocument>("learningPaths")
    private val follows = database.getCollection<LearningPathFollowDocument>("learningPathFollows")

    suspend fun list(): List<LearningPathDocument> = paths.find().toList()

    suspend fun findById(id: ObjectId): LearningPathDocument? = paths.find(eq("_id", id)).firstOrNull()

    suspend fun isFollowing(userId: ObjectId, pathId: ObjectId): Boolean =
        follows.find(followFilter(userId, pathId)).firstOrNull() != null

    suspend fun follow(userId: ObjectId, pathId: ObjectId, followedAt: Instant) {
        follows.updateOne(
            followFilter(userId, pathId),
            combine(
                setOnInsert("userId", userId),
                setOnInsert("pathId", pathId),
                setOnInsert("followedAt", followedAt),
            ),
            UpdateOptions().upsert(true),
        )
    }

    suspend fun unfollow(userId: ObjectId, pathId: ObjectId) {
        follows.deleteOne(followFilter(userId, pathId))
    }

    private fun followFilter(userId: ObjectId, pathId: ObjectId) =
        and(eq("userId", userId), eq("pathId", pathId))
}
