package com.mentora.backend

import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.progress.service.ProgressService
import com.mentora.backend.quiz.repository.Option
import com.mentora.backend.quiz.repository.Question
import com.mentora.backend.quiz.repository.QuizDocument
import com.mentora.backend.quiz.repository.QuizRepository
import com.mentora.backend.quiz.service.AttemptAnswerRequest
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.quiz.service.SubmitAttemptRequest
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizServiceTest {
    private val repository = mockk<QuizRepository>(relaxed = true)
    private val courses = mockk<CourseService>(relaxed = true)
    private val enrollments = mockk<EnrollmentService>(relaxed = true)
    private val progress = mockk<ProgressService>(relaxed = true)
    private val mongoClient = mockk<MongoClient>()
    private val session = mockk<ClientSession>(relaxed = true)
    private val service = QuizService(repository, courses, enrollments, progress, mongoClient)
    private val courseId = ObjectId()
    private val principal = MentoraPrincipal(ObjectId(), Role.student)

    init {
        coEvery { mongoClient.startSession() } returns session
        every { session.hasActiveTransaction() } returns true
    }

    @Test
    fun `zero correct answers scores zero and fails`() = runBlocking {
        val response = submit(questions = 10, correctAnswers = 0)

        assertEquals(0, response.score)
        assertFalse(response.passed)
        assertTrue(response.breakdown.none { it.isCorrect })
    }

    @Test
    fun `all correct answers score one hundred and pass`() = runBlocking {
        val response = submit(questions = 10, correctAnswers = 10)

        assertEquals(100, response.score)
        assertTrue(response.passed)
        assertTrue(response.breakdown.all { it.isCorrect })
    }

    @Test
    fun `seventy percent is the inclusive passing boundary`() = runBlocking {
        val belowBoundary = submit(questions = 10, correctAnswers = 6)
        val atBoundary = submit(questions = 10, correctAnswers = 7)

        assertEquals(60, belowBoundary.score)
        assertFalse(belowBoundary.passed)
        assertEquals(70, atBoundary.score)
        assertTrue(atBoundary.passed)
    }

    @Test
    fun `answer for nonexistent question is ignored`() = runBlocking {
        val response = submit(
            questions = 1,
            answers = listOf(AttemptAnswerRequest("unknown-question", "correct-0")),
        )

        assertEquals(0, response.score)
        assertNull(response.breakdown.single().selectedOptionId)
        assertFalse(response.breakdown.single().isCorrect)
    }

    @Test
    fun `missing answer remains unselected in breakdown`() = runBlocking {
        val response = submit(
            questions = 2,
            answers = listOf(AttemptAnswerRequest("question-0", "correct-0")),
        )

        assertEquals(50, response.score)
        assertEquals("correct-0", response.breakdown[0].selectedOptionId)
        assertNull(response.breakdown[1].selectedOptionId)
        assertFalse(response.breakdown[1].isCorrect)
    }

    private suspend fun submit(questions: Int, correctAnswers: Int) = submit(
        questions,
        (0 until questions).map { index ->
            AttemptAnswerRequest("question-$index", if (index < correctAnswers) "correct-$index" else "wrong-$index")
        },
    )

    private suspend fun submit(questions: Int, answers: List<AttemptAnswerRequest>) =
        quiz(questions).let { quiz ->
            coEvery { repository.findByCourseId(courseId) } returns quiz
            service.submit(courseId.toHexString(), principal, SubmitAttemptRequest(answers))
        }

    private fun quiz(questionCount: Int) = QuizDocument(
        id = ObjectId(),
        courseId = courseId,
        questions = (0 until questionCount).map { index ->
            Question(
                questionId = "question-$index",
                prompt = "Question $index",
                order = index,
                options = listOf(
                    Option("correct-$index", "Correct", true),
                    Option("wrong-$index", "Wrong", false),
                ),
            )
        },
    )
}
