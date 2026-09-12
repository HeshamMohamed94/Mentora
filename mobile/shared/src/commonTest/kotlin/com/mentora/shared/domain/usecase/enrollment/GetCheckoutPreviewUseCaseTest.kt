package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.CheckoutCourse
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val testPreview = CheckoutPreview(
    course = CheckoutCourse("c1", "Kotlin Mastery", "m1"),
    instructorName = "Jane Doe",
    priceDisplay = PriceDisplay(4999, "USD"),
)

private class FakeEnrollmentRepositoryForPreview(
    private val previewResult: ApiResult<CheckoutPreview>,
) : EnrollmentRepository {
    var lastCourseId: String? = null

    override suspend fun getCheckoutPreview(courseId: String): ApiResult<CheckoutPreview> {
        lastCourseId = courseId
        return previewResult
    }

    override suspend fun completeCheckout(courseId: String) = throw NotImplementedError()

    override suspend fun listEnrollments(cursor: String?, limit: Int?) = throw NotImplementedError()
}

class GetCheckoutPreviewUseCaseTest {

    @Test
    fun `invoke forwards the course id and returns the repository result unchanged`() = runTest {
        val repository = FakeEnrollmentRepositoryForPreview(ApiResult.Success(testPreview))
        val useCase = GetCheckoutPreviewUseCase(repository)

        val result = useCase("c1")

        assertEquals(ApiResult.Success(testPreview), result)
        assertEquals("c1", repository.lastCourseId)
    }

    @Test
    fun `a non-existent or unpublished course surfaces as an ordinary CourseNotFound failure`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.CourseNotFound, "The course was not found.", null, 404)
        val repository = FakeEnrollmentRepositoryForPreview(failure)
        val useCase = GetCheckoutPreviewUseCase(repository)

        val result = useCase("missing")

        assertEquals(failure, result)
    }
}
