package com.mentora.backend.progress.repository

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import com.mongodb.client.model.Updates.setOnInsert
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

data class CompletionUpdate(
    val userId: ObjectId, val courseId: ObjectId, val lessonId: String,
    val completed: Map<String, Instant>, val percentage: Int, val now: Instant,
)

data class PositionUpdate(
    val userId: ObjectId, val courseId: ObjectId, val lessonId: String,
    val positionSeconds: Int, val now: Instant,
)

class ProgressRepository(database: MongoDatabase) {
    private val progress = database.getCollection<ProgressDocument>("progress")

    suspend fun findOrCreate(userId: ObjectId, courseId: ObjectId, now: Instant): ProgressDocument =
        requireNotNull(progress.findOneAndUpdate(
            filter(userId, courseId),
            combine(
                setOnInsert("userId", userId), setOnInsert("courseId", courseId),
                setOnInsert("completedLessonIds", emptyMap<String, Instant>()),
                setOnInsert("quizPassed", null), setOnInsert("completionPercent", 0),
                setOnInsert("courseCompletedAt", null), setOnInsert("updatedAt", now),
            ),
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
        ))

    suspend fun markComplete(update: CompletionUpdate): ProgressDocument =
        requireNotNull(progress.findOneAndUpdate(
        filter(update.userId, update.courseId),
        combine(
            set("completedLessonIds", update.completed), set("currentLessonId", update.lessonId),
            set("completionPercent", update.percentage), set("updatedAt", update.now),
        ),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
    ))

    suspend fun updatePosition(update: PositionUpdate): ProgressDocument =
        requireNotNull(progress.findOneAndUpdate(
        filter(update.userId, update.courseId),
        combine(
            setOnInsert("userId", update.userId), setOnInsert("courseId", update.courseId),
            setOnInsert("completedLessonIds", emptyMap<String, Instant>()),
            setOnInsert("quizPassed", null), setOnInsert("completionPercent", 0),
            setOnInsert("courseCompletedAt", null), set("currentLessonId", update.lessonId),
            set("currentPositionSeconds", update.positionSeconds), set("updatedAt", update.now),
        ),
        FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
    ))

    suspend fun find(userId: ObjectId, courseId: ObjectId): ProgressDocument? =
        progress.find(filter(userId, courseId)).firstOrNull()

    private fun filter(userId: ObjectId, courseId: ObjectId) =
        and(eq("userId", userId), eq("courseId", courseId))
}
