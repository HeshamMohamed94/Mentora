package com.mentora.backend.quiz.repository

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts.descending
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class Option(val optionId: String, val text: String, val isCorrect: Boolean)
@Serializable data class Question(val questionId: String, val prompt: String, val order: Int, val options: List<Option>)
@Serializable data class QuizDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val courseId: ObjectId,
    val questions: List<Question>,
)
@Serializable data class AnswerRecord(val questionId: String, val selectedOptionId: String?)
@Serializable data class QuizAttemptDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val quizId: ObjectId,
    @Contextual val courseId: ObjectId,
    val answers: List<AnswerRecord>,
    val score: Int,
    val passed: Boolean,
    val submittedAt: Instant,
)

class QuizRepository(database: MongoDatabase) {
    private val quizzes = database.getCollection<QuizDocument>("quizzes")
    private val attempts = database.getCollection<QuizAttemptDocument>("quizAttempts")

    suspend fun findByCourseId(courseId: ObjectId): QuizDocument? =
        quizzes.find(eq("courseId", courseId)).firstOrNull()

    suspend fun replaceByCourseId(document: QuizDocument): QuizDocument {
        quizzes.replaceOne(eq("courseId", document.courseId), document, ReplaceOptions().upsert(true))
        return requireNotNull(findByCourseId(document.courseId))
    }

    suspend fun insertAttempt(session: ClientSession, document: QuizAttemptDocument): QuizAttemptDocument {
        val id = requireNotNull(attempts.insertOne(session, document).insertedId?.asObjectId()?.value)
        return document.copy(id = id)
    }

    suspend fun findLatest(userId: ObjectId, quizId: ObjectId): QuizAttemptDocument? =
        attempts.find(and(eq("userId", userId), eq("quizId", quizId)))
            .sort(descending("submittedAt")).limit(1).firstOrNull()
}
