package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun enrollment(id: String, courseId: String) = Enrollment(
    id = id, courseId = courseId, source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z", status = EnrollmentStatus.Active,
)

private class FakeEnrollmentRepositoryForList(
    private val pages: List<ApiResult<CursorPage<Enrollment>>>,
) : EnrollmentRepository {
    private var callIndex = 0
    val seenCursors = mutableListOf<String?>()

    override suspend fun getCheckoutPreview(courseId: String): ApiResult<CheckoutPreview> = throw NotImplementedError()

    override suspend fun completeCheckout(courseId: String): ApiResult<EnrollmentCompletion> = throw NotImplementedError()

    override suspend fun listEnrollments(cursor: String?, limit: Int?): ApiResult<CursorPage<Enrollment>> {
        seenCursors += cursor
        return pages[callIndex++]
    }
}

class ListEnrollmentsUseCaseTest {

    @Test
    fun `invoke pages through two sequential results via nextCursor`() = runTest {
        val firstPage = ApiResult.Success(CursorPage(items = listOf(enrollment("e1", "c1")), nextCursor = "cursor-2"))
        val secondPage = ApiResult.Success(CursorPage(items = listOf(enrollment("e2", "c2")), nextCursor = null))
        val repository = FakeEnrollmentRepositoryForList(listOf(firstPage, secondPage))
        val useCase = ListEnrollmentsUseCase(repository)

        val result1 = useCase(cursor = null)
        require(result1 is ApiResult.Success)
        assertEquals(listOf("e1"), result1.data.items.map { it.id })
        assertEquals("cursor-2", result1.data.nextCursor)

        val result2 = useCase(cursor = result1.data.nextCursor)
        require(result2 is ApiResult.Success)
        assertEquals(listOf("e2"), result2.data.items.map { it.id })
        assertNull(result2.data.nextCursor)

        assertEquals(listOf(null, "cursor-2"), repository.seenCursors)
    }
}
