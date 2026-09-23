package com.mentora.backend.quiz.service

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.withRetryableTransaction
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.progress.service.ProgressService
import com.mentora.backend.quiz.repository.AnswerRecord
import com.mentora.backend.quiz.repository.Option
import com.mentora.backend.quiz.repository.Question
import com.mentora.backend.quiz.repository.QuizAttemptDocument
import com.mentora.backend.quiz.repository.QuizDocument
import com.mentora.backend.quiz.repository.QuizRepository
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.UUID

@Serializable data class StudentQuizOption(val optionId: String, val text: String)
@Serializable data class StudentQuizQuestion(
    val questionId: String, val prompt: String, val order: Int, val options: List<StudentQuizOption>,
)
@Serializable data class StudentQuizResponse(val courseId: String, val questions: List<StudentQuizQuestion>)
@Serializable data class EditorQuizOption(val optionId: String, val text: String, val isCorrect: Boolean)
@Serializable data class EditorQuizQuestion(
    val questionId: String, val prompt: String, val order: Int, val options: List<EditorQuizOption>,
)
@Serializable data class EditorQuizResponse(val courseId: String, val questions: List<EditorQuizQuestion>)
@Serializable data class EditorOptionRequest(val optionId: String? = null, val text: String, val isCorrect: Boolean)
@Serializable data class EditorQuestionRequest(
    val questionId: String? = null, val prompt: String, val order: Int, val options: List<EditorOptionRequest>,
)
@Serializable data class PutQuizRequest(val questions: List<EditorQuestionRequest>)
@Serializable data class AttemptAnswerRequest(val questionId: String, val selectedOptionId: String)
@Serializable data class SubmitAttemptRequest(val answers: List<AttemptAnswerRequest>)
@Serializable data class AttemptBreakdown(
    val questionId: String, val selectedOptionId: String?, val correctOptionId: String, val isCorrect: Boolean,
)
@Serializable data class AttemptResponse(val score: Int, val passed: Boolean, val breakdown: List<AttemptBreakdown>)

class QuizService(
    private val repository: QuizRepository,
    private val courses: CourseService,
    private val enrollments: EnrollmentService,
    private val progress: ProgressService,
    private val mongoClient: MongoClient,
) {
    suspend fun hasQuiz(courseId: ObjectId): Boolean = repository.findByCourseId(courseId) != null

    suspend fun studentQuiz(courseId: String, principal: MentoraPrincipal): StudentQuizResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        val quiz = requireQuiz(objectCourseId)
        return StudentQuizResponse(courseId, quiz.questions.map { question ->
            StudentQuizQuestion(question.questionId, question.prompt, question.order,
                question.options.map { StudentQuizOption(it.optionId, it.text) })
        })
    }

    suspend fun editorQuiz(courseId: String, principal: MentoraPrincipal): EditorQuizResponse {
        courses.requireOwnership(courseId, principal)
        return repository.findByCourseId(objectId(courseId))?.toEditorResponse()
            ?: EditorQuizResponse(courseId, emptyList())
    }

    suspend fun replace(courseId: String, principal: MentoraPrincipal, request: PutQuizRequest): EditorQuizResponse {
        courses.requireOwnership(courseId, principal)
        validate(request)
        val document = QuizDocument(courseId = objectId(courseId), questions = request.questions.map { question ->
            Question(question.questionId ?: UUID.randomUUID().toString(), question.prompt.trim(), question.order,
                question.options.map { option ->
                    Option(option.optionId ?: UUID.randomUUID().toString(), option.text.trim(), option.isCorrect)
                })
        })
        return repository.replaceByCourseId(document).toEditorResponse()
    }

    suspend fun submit(courseId: String, principal: MentoraPrincipal, request: SubmitAttemptRequest): AttemptResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        val quiz = requireQuiz(objectCourseId)
        val response = grade(quiz, request)
        val now = Clock.System.now()
        // Phase 8 C4: two concurrent submits (e.g. a double-tap on "Submit Quiz") can hit a
        // MongoDB WriteConflict/TransientTransactionError on the shared `progress` document below —
        // unlike enrollment/certificate issuance, there is no idempotent-duplicate case to recover
        // here (each attempt is a legitimately new, independent record), so `withRetryableTransaction`
        // simply re-runs the whole transaction body, which is safe to redo from scratch.
        mongoClient.withRetryableTransaction { session ->
            repository.insertAttempt(session, QuizAttemptDocument(
                userId = principal.userId, quizId = requireNotNull(quiz.id), courseId = objectCourseId,
                answers = response.breakdown.map { AnswerRecord(it.questionId, it.selectedOptionId) },
                score = response.score, passed = response.passed, submittedAt = now,
            ))
            progress.setQuizPassed(session, principal.userId, objectCourseId, response.passed, now)
        }
        return response
    }

    suspend fun latest(courseId: String, principal: MentoraPrincipal): AttemptResponse {
        val objectCourseId = objectId(courseId)
        enrollments.requireEnrollment(principal.userId, objectCourseId)
        val quiz = requireQuiz(objectCourseId)
        val attempt = repository.findLatest(principal.userId, requireNotNull(quiz.id))
            ?: throw ApiException.NotFound("ATTEMPT_NOT_FOUND", "No quiz attempt was found.")
        val answers = attempt.answers.associateBy { it.questionId }
        return AttemptResponse(attempt.score, attempt.passed, quiz.questions.map { question ->
            val selected = answers[question.questionId]?.selectedOptionId
            val correct = requireNotNull(question.options.singleOrNull { it.isCorrect })
            AttemptBreakdown(question.questionId, selected, correct.optionId, selected == correct.optionId)
        })
    }

    private fun grade(quiz: QuizDocument, request: SubmitAttemptRequest): AttemptResponse {
        val answers = request.answers.associateBy { it.questionId }
        val breakdown = quiz.questions.map { question ->
            val selected = answers[question.questionId]?.selectedOptionId
            val correct = requireNotNull(question.options.singleOrNull { it.isCorrect })
            AttemptBreakdown(question.questionId, selected, correct.optionId, selected == correct.optionId)
        }
        val score = breakdown.count { it.isCorrect } * 100 / quiz.questions.size
        return AttemptResponse(score, score >= PASS_PERCENT, breakdown)
    }

    private fun validate(request: PutQuizRequest) {
        if (request.questions.isEmpty()) throw ApiException.Validation(fields = mapOf("questions" to "REQUIRED"))
        request.questions.forEachIndexed { index, question ->
            val key = question.questionId ?: index.toString()
            val error = when {
                question.prompt.isBlank() -> "PROMPT_REQUIRED"
                question.options.size < 2 -> "AT_LEAST_TWO_OPTIONS_REQUIRED"
                question.options.any { it.text.isBlank() } -> "OPTION_TEXT_REQUIRED"
                question.options.count { it.isCorrect } != 1 -> "EXACTLY_ONE_CORRECT_OPTION_REQUIRED"
                else -> null
            }
            if (error != null) throw ApiException.Validation(fields = mapOf("questions[$key]" to error))
        }
    }

    private suspend fun requireQuiz(courseId: ObjectId) = repository.findByCourseId(courseId)
        ?: throw ApiException.NotFound("QUIZ_NOT_FOUND", "The quiz was not found.")

    private fun objectId(value: String) = try { ObjectId(value) }
    catch (_: IllegalArgumentException) { throw ApiException.Validation(fields = mapOf("id" to "INVALID")) }

    private fun QuizDocument.toEditorResponse() = EditorQuizResponse(courseId.toHexString(), questions.map { question ->
        EditorQuizQuestion(question.questionId, question.prompt, question.order,
            question.options.map { EditorQuizOption(it.optionId, it.text, it.isCorrect) })
    })

    private companion object { const val PASS_PERCENT = 70 }
}
