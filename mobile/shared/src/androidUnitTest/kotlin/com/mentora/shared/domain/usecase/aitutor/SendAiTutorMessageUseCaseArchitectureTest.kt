package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.repository.aitutor.AiTutorRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Runs as a real JVM reflection check — deliberately placed under `androidUnitTest`, not
 * `commonTest`, because `java.lang.Class` reflection is a JVM-only API `commonTest` may never
 * depend on (`execution/PHASE_3_KMP_PLAN.md` Decision D-B: "`commonTest` uses `kotlin.test` + Ktor
 * `MockEngine` only (no JVM-only APIs)"). Still executed by the same `:shared:testDebugUnitTest`
 * Gradle task every `commonTest` test runs under — the Android unit-test source set is the one
 * JVM-executable path with `androidTarget()` + iOS targets declared (D-B).
 *
 * Asserts the real architecture-boundary AC from Task 14: [SendAiTutorMessageUseCase] must have
 * ZERO dependency on `com.mentora.shared.data.repository.quiz.QuizRepository` (or any other
 * quiz-domain type) — even though `AiQuickAction.QuizMe` is one of the five quick actions, sending
 * its resulting message is an ordinary text message like any other; this use case never reaches
 * into the quiz domain. This test would fail the moment someone added a `QuizRepository`
 * constructor parameter or field to [SendAiTutorMessageUseCase] — a required parameter breaks the
 * "exactly one constructor parameter" assertion below, and a defaulted/extra parameter or a stored
 * field breaks the "no declared field is quiz-shaped" assertion.
 */
class SendAiTutorMessageUseCaseArchitectureTest {

    @Test
    fun `constructor has exactly one parameter, of type AiTutorRepository`() {
        val constructors = SendAiTutorMessageUseCase::class.java.declaredConstructors
        assertEquals(1, constructors.size, "expected exactly one constructor, found: ${constructors.toList()}")

        val parameterTypes = constructors.single().parameterTypes.toList()
        assertEquals(
            listOf(AiTutorRepository::class.java),
            parameterTypes,
            "SendAiTutorMessageUseCase must depend on nothing but AiTutorRepository",
        )
    }

    @Test
    fun `no declared field anywhere on the class is quiz-shaped`() {
        val forbiddenPrefixes = listOf(
            "com.mentora.shared.data.repository.quiz",
            "com.mentora.shared.domain.model.Quiz",
        )
        SendAiTutorMessageUseCase::class.java.declaredFields.forEach { field ->
            val typeName = field.type.name
            assertFalse(
                forbiddenPrefixes.any { typeName.startsWith(it) },
                "SendAiTutorMessageUseCase must never hold a quiz-shaped field, found: $typeName (field ${field.name})",
            )
        }
    }
}
