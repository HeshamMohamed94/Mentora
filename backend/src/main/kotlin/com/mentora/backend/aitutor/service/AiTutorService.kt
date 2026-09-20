package com.mentora.backend.aitutor.service

import com.mentora.backend.aitutor.provider.AiCompletionRequest
import com.mentora.backend.aitutor.provider.AiHistoryTurn
import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.AiToken
import com.mentora.backend.aitutor.provider.AiUsage
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
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

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

class AiTutorService(
    private val repository: AiTutorRepository,
    private val enrollment: EnrollmentService,
    private val courses: CourseService,
    private val provider: AiProvider,
    private val appConfig: AppConfig,
) {
    private val log = LoggerFactory.getLogger(AiTutorService::class.java)

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
        val startedAt = System.currentTimeMillis()
        val mode = if (request.courseId != null) "lesson" else "global"
        var conversationIdForLog = "-"
        var historyTurns = 0
        var enrolledCourseCount = 0
        var usage: AiUsage? = null
        var firstTokenMs: Long? = null
        var outcome = "ok"
        var level = Level.INFO
        var errorMessage: String? = null

        try {
            val content = validatedContent(request)
            val lessonContext = resolveLessonContext(principal, request)
            val conversation = repository.findOrCreate(principal.userId, Clock.System.now())
            val conversationId = requireNotNull(conversation.id)
            conversationIdForLog = conversationId.toHexString()
            val history = repository.recent(conversationId, HISTORY_LIMIT)
                .map { AiHistoryTurn(it.role, it.content) }
            val enrolled = enrolledCourses(principal)
            enrolledCourseCount = enrolled.size
            val systemPrompt = AiPromptBuilder.buildSystemPrompt(enrolled, lessonContext)
            val normalizedHistory = AiPromptBuilder.normalizeHistory(history)
            historyTurns = normalizedHistory.size
            appendMessage(conversationId, "user", content, request.lessonContextId)

            // F1: the new user turn is folded into the same normalization pass as the rest of the
            // history (merging into a trailing same-role turn if one exists) before it ever reaches
            // the provider — never appended separately, which is what let two adjacent `user` turns
            // reach Anthropic after a retry.
            val turns = AiPromptBuilder.appendUserTurn(normalizedHistory, content)

            val providerStartedAt = System.currentTimeMillis()
            usage = provider.complete(
                AiCompletionRequest(
                    systemPrompt = systemPrompt,
                    history = turns,
                    maxResponseTokens = appConfig.aiProviderMaxResponseTokens,
                ),
            ) { tokens ->
                firstTokenMs = System.currentTimeMillis() - providerStartedAt
                val assistant = StringBuilder()
                respond(tokens.onEach { assistant.append(it.text) })
                appendMessage(conversationId, "assistant", assistant.toString(), request.lessonContextId)
            }
        } catch (e: ApiException.ServiceUnavailable) {
            // Design § 11 buckets 1-6, 11 (pre-first-token) and row 12 when the flow itself maps a
            // mid-stream malformed-event to this type — always transient/upstream, never Mentora's fault.
            outcome = "provider_unavailable"
            level = Level.WARN
            errorMessage = e.message
            throw e
        } catch (e: ApiException.Internal) {
            // Design § 11 buckets 7-10 — a real misconfiguration (bad key/model/prompt), never transient.
            // F3: the client-visible `message` is now generic ("Something went wrong.") for this
            // call site; the operator-facing diagnostic hint (status/error.type/env-var name) lives
            // on `cause` instead, so pull it from there for the log — don't lose it, just don't
            // expose it in the HTTP response.
            outcome = "provider_misconfigured"
            level = Level.ERROR
            errorMessage = e.cause?.message ?: e.message
            throw e
        } catch (e: Throwable) {
            // Anything else: an unrecognized provider failure, a mid-stream abort after the first
            // token was already emitted (§ 11 row 12), or a pre-provider failure (validation/
            // enrollment/lesson lookup) — none of those are the "AI Tutor provider" failing per se,
            // but this log line's job is only ever-fires-once observability, not a full error taxonomy.
            // F5: an arbitrary third-party exception's `message` isn't guaranteed to be free of
            // unexpected content (Design § 21) — log only the exception's type name here.
            outcome = "stream_failed"
            level = Level.WARN
            errorMessage = e::class.simpleName
            throw e
        } finally {
            logMessageOutcome(
                requestId = requestId,
                userId = principal.userId.toHexString(),
                conversationId = conversationIdForLog,
                mode = mode,
                lessonContextId = request.lessonContextId,
                historyTurns = historyTurns,
                enrolledCourses = enrolledCourseCount,
                usage = usage,
                firstTokenMs = firstTokenMs,
                totalMs = System.currentTimeMillis() - startedAt,
                outcome = outcome,
                error = errorMessage,
                level = level,
            )
        }
    }

    /**
     * One structured log line per [streamMessage] call, success or failure (Design § 21 / A10).
     * Never logs message content, prompt content, lesson/course titles, or the API key — only ids,
     * counts, timings and outcome/error-type strings.
     */
    private fun logMessageOutcome(
        requestId: String,
        userId: String,
        conversationId: String,
        mode: String,
        lessonContextId: String?,
        historyTurns: Int,
        enrolledCourses: Int,
        usage: AiUsage?,
        firstTokenMs: Long?,
        totalMs: Long,
        outcome: String,
        error: String?,
        level: Level,
    ) {
        val providerMode = appConfig.aiProviderMode()
        val model = if (providerMode == "anthropic") appConfig.aiProviderModel else "-"
        val args = arrayOf<Any?>(
            requestId, userId, conversationId, mode, lessonContextId ?: "-",
            providerMode, model, historyTurns, enrolledCourses,
            usage?.inputTokens?.toString() ?: "-", usage?.outputTokens?.toString() ?: "-",
            firstTokenMs?.toString() ?: "-", totalMs, outcome, error ?: "-",
        )
        when (level) {
            Level.ERROR -> log.error(LOG_TEMPLATE, *args)
            Level.WARN -> log.warn(LOG_TEMPLATE, *args)
            else -> log.info(LOG_TEMPLATE, *args)
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
    ): AiPromptBuilder.LessonContext? {
        val courseId = request.courseId ?: return null
        val lessonId = requireNotNull(request.lessonContextId)
        enrollment.requireEnrollment(principal.userId, objectId(courseId))
        val course = courses.get(courseId, principal)
        val lesson = course.sections.flatMap { it.lessons }.firstOrNull { it.lessonId == lessonId }
            ?: throw ApiException.NotFound("LESSON_NOT_FOUND", "The lesson was not found.")
        return AiPromptBuilder.LessonContext(lesson.title, lesson.description)
    }

    /** Injected on every message, global and lesson-context mode alike (Design § 4.3) — Android
     * renders all five quick actions in lesson-context mode too, so gating this on mode would
     * silently degrade "What should I learn next?" there. */
    private suspend fun enrolledCourses(principal: MentoraPrincipal): List<AiPromptBuilder.EnrolledCourse> {
        val enrollments = enrollment.list(principal, PageRequest(cursor = null, limit = ENROLLED_COURSE_LIMIT))
        if (enrollments.items.isEmpty()) return emptyList()
        return courses.enrolledCourseBriefs(enrollments.items.map { ObjectId(it.courseId) })
            .map { AiPromptBuilder.EnrolledCourse(it.title, it.level) }
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
        const val ENROLLED_COURSE_LIMIT = 20

        /** Design § 21 — never a message/prompt/course-title/key field, only ids/counts/timings/outcome. */
        const val LOG_TEMPLATE = "aiTutor.message requestId={} userId={} conversationId={} mode={} " +
            "lessonContextId={} provider={} model={} historyTurns={} enrolledCourses={} inputTokens={} " +
            "outputTokens={} firstTokenMs={} totalMs={} outcome={} error={}"
    }
}
