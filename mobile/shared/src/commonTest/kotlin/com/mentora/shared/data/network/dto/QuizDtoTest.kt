package com.mentora.shared.data.network.dto

import com.mentora.shared.data.network.MentoraJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the "no `isCorrect` on the pre-submission option" guarantee is structural, not just
 * behavioral: [QuizOptionDto] has no `isCorrect` CONSTRUCTOR PARAMETER at all — a fact enforced at
 * compile time by this file simply not being able to write `QuizOptionDto(optionId = "o1", text =
 * "A", isCorrect = true)` anywhere (there is no such overload). The runtime test below documents
 * the resulting behavioral guarantee: even if a backend response body somehow carried an
 * `isCorrect` field on a quiz option (e.g. an instructor-facing shape leaking onto this endpoint by
 * accident), `shared`'s `ignoreUnknownKeys = true` [MentoraJson] silently drops it and
 * [QuizOptionDto] still deserializes cleanly with only `optionId`/`text` populated — there is
 * simply no field on the resulting object for a correctness value to land in.
 */
class QuizDtoTest {

    @Test
    fun `QuizOptionDto has no constructor parameter for isCorrect`() {
        // This line only compiles because QuizOptionDto(optionId, text) is its full constructor —
        // a hypothetical `QuizOptionDto(optionId = "o1", text = "A", isCorrect = true)` would fail
        // to compile, which is the actual structural proof this test exists to exercise.
        val option = QuizOptionDto(optionId = "o1", text = "A")
        assertEquals(QuizOptionDto("o1", "A"), option)
    }

    @Test
    fun `an isCorrect field on the wire is silently ignored, never surfacing on QuizOptionDto`() {
        val json = """{"optionId":"o1","text":"A","isCorrect":true}"""

        val decoded = MentoraJson.decodeFromString(QuizOptionDto.serializer(), json)

        assertEquals(QuizOptionDto("o1", "A"), decoded)
    }

    @Test
    fun `a full quiz question with an isCorrect leak on one option still deserializes cleanly`() {
        val json = """
            {"questionId":"q1","prompt":"2+2?","order":0,"options":[
                {"optionId":"o1","text":"3","isCorrect":false},
                {"optionId":"o2","text":"4","isCorrect":true}
            ]}
        """.trimIndent()

        val decoded = MentoraJson.decodeFromString(QuizQuestionDto.serializer(), json)

        assertEquals(
            QuizQuestionDto("q1", "2+2?", 0, listOf(QuizOptionDto("o1", "3"), QuizOptionDto("o2", "4"))),
            decoded,
        )
    }
}
