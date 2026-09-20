package com.mentora.backend.aitutor.service

import com.mentora.backend.aitutor.provider.AiCompletionRequest
import com.mentora.backend.aitutor.provider.AiHistoryTurn
import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.AiToken
import com.mentora.backend.aitutor.repository.AiMessageDocument
import com.mentora.backend.aitutor.repository.AiTutorRepository
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.toPage
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.service.EnrollmentService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class SendAiMessageRequest(
    val content: String,
    val courseId: String? = null,
    val lessonContextId: String? = null,
)

@Serializable
data class AiMessageResponse(
    val id: String,
    val role: String,
    val content: String,
    val lessonContextId: String? = null,
    val createdAt: Instant,
)

@Serializable
data class AiConversationResponse(
    val conversationId: String,
    val messages: List<AiMessageResponse>,
    val nextCursor: String? = null,
)

data class LessonContext(val title: String, val description: String)

class AiTutorService(
    private val repository: AiTutorRepository,
    private val enrollment: EnrollmentService,
    private val courses: CourseService,
    private val provider: AiProvider,
    private val appConfig: AppConfig,
) {
    suspend fun conversation(principal: MentoraPrincipal, page: PageRequest): AiConversationResponse {
        val conversation = repository.findOrCreate(principal.userId, Clock.System.now())
        val messages = repository.list(requireNotNull(conversation.id), page).toPage(page.limit) { requireNotNull(it.id) }
        return AiConversationResponse(
            conversation.id.toHexString(), messages.items.map { it.toResponse() }, messages.nextCursor,
        )
    }

    suspend fun streamMessage(
        principal: MentoraPrincipal,
        request: SendAiMessageRequest,
        requestId: String,
        respond: suspend (Flow<AiToken>) -> Unit,
    ) {
        val content = validatedContent(request)
        resolveLessonContext(principal, request)
        val conversation = repository.findOrCreate(principal.userId, Clock.System.now())
        val conversationId = requireNotNull(conversation.id)
        val history = repository.recent(conversationId, HISTORY_LIMIT)
            .map { AiHistoryTurn(it.role, it.content) }
        appendMessage(conversationId, "user", content, request.lessonContextId)
        provider.complete(
            AiCompletionRequest(
                systemPrompt = SYSTEM_PROMPT,
                history = history,
                userMessage = content,
                maxResponseTokens = appConfig.aiProviderMaxResponseTokens,
            ),
        ) { tokens ->
            val assistant = StringBuilder()
            respond(tokens.onEach { assistant.append(it.text) })
            appendMessage(conversationId, "assistant", assistant.toString(), request.lessonContextId)
        }
    }

    private fun validatedContent(request: SendAiMessageRequest): String {
        val content = request.content.trim()
        if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
            val reason = if (content.isBlank()) "REQUIRED" else "TOO_LONG"
            throw ApiException.Validation(fields = mapOf("content" to reason))
        }
        if ((request.courseId == null) != (request.lessonContextId == null)) {
            throw ApiException.Validation(fields = mapOf("lessonContext" to "COURSE_AND_LESSON_REQUIRED_TOGETHER"))
        }
        return content
    }

    private suspend fun resolveLessonContext(
        principal: MentoraPrincipal,
        request: SendAiMessageRequest,
    ): LessonContext? {
        val courseId = request.courseId ?: return null
        val lessonId = requireNotNull(request.lessonContextId)
        enrollment.requireEnrollment(principal.userId, objectId(courseId))
        val course = courses.get(courseId, principal)
        val lesson = course.sections.flatMap { it.lessons }.firstOrNull { it.lessonId == lessonId }
            ?: throw ApiException.NotFound("LESSON_NOT_FOUND", "The lesson was not found.")
        return LessonContext(lesson.title, lesson.description)
    }

    private suspend fun appendMessage(
        conversationId: ObjectId,
        role: String,
        content: String,
        lessonContextId: String?,
    ) {
        repository.append(AiMessageDocument(
            conversationId = conversationId,
            role = role,
            content = content,
            lessonContextId = lessonContextId,
            createdAt = Clock.System.now(),
        ))
    }

    private fun objectId(value: String) = try {
        ObjectId(value)
    } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf("courseId" to "INVALID"))
    }

    private fun AiMessageDocument.toResponse() = AiMessageResponse(
        requireNotNull(id).toHexString(), role, content, lessonContextId, createdAt,
    )

    private companion object {
        const val MAX_CONTENT_LENGTH = 4_000
        const val HISTORY_LIMIT = 20
        const val SYSTEM_PROMPT = "You are Mentora's helpful AI Tutor. Explain concepts clearly and support learning. " +
            "You are read-and-explain-only: never claim to modify enrollment, progress, quiz, or account state."
    }
}
