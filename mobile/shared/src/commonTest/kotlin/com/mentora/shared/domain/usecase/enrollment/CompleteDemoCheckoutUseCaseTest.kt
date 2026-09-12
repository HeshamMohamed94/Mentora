package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testEnrollment = Enrollment(
    id = "e1", courseId = "c1", source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z", status = EnrollmentStatus.Active,
)

private class FakeEnrollmentRepositoryForComplete(
    private val completionResult: ApiResult<EnrollmentCompletion>,
) : EnrollmentRepository {
    var lastCourseId: String? = null
    var completeCheckoutCallCount = 0
        private set

    override suspend fun getCheckoutPreview(courseId: String): ApiResult<CheckoutPreview> = throw NotImplementedError()

    override suspend fun completeCheckout(courseId: String): ApiResult<EnrollmentCompletion> {
        completeCheckoutCallCount++
        lastCourseId = courseId
        return completionResult
    }

    override suspend fun listEnrollments(cursor: String?, limit: Int?) = throw NotImplementedError()
}

class CompleteDemoCheckoutUseCaseTest {

    @Test
    fun `invoke forwards the course id and returns a first-time completion unchanged`() = runTest {
        val completion = EnrollmentCompletion(enrollment = testEnrollment, alreadyEnrolled = false)
        val repository = FakeEnrollmentRepositoryForComplete(ApiResult.Success(completion))
        val useCase = CompleteDemoCheckoutUseCase(repository)

        val result = useCase("c1")

        require(result is ApiResult.Success)
        assertEquals(false, result.data.alreadyEnrolled)
        assertEquals("c1", repository.lastCourseId)
        assertEquals(1, repository.completeCheckoutCallCount)
    }

    @Test
    fun `invoke returns a repeat completion as Success with alreadyEnrolled true, never as an error`() = runTest {
        val completion = EnrollmentCompletion(enrollment = testEnrollment, alreadyEnrolled = true)
        val repository = FakeEnrollmentRepositoryForComplete(ApiResult.Success(completion))
        val useCase = CompleteDemoCheckoutUseCase(repository)

        val result = useCase("c1")

        assertTrue(result is ApiResult.Success)
        assertEquals(true, result.data.alreadyEnrolled)
    }

    @Test
    fun `invoke never issues more than one completion call per invocation (no client-side retry)`() = runTest {
        val completion = EnrollmentCompletion(enrollment = testEnrollment, alreadyEnrolled = false)
        val repository = FakeEnrollmentRepositoryForComplete(ApiResult.Success(completion))
        val useCase = CompleteDemoCheckoutUseCase(repository)

        useCase("c1")

        assertEquals(1, repository.completeCheckoutCallCount)
    }
}
