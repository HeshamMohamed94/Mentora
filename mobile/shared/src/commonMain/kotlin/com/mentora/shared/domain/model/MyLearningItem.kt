package com.mentora.shared.domain.model

/**
 * [com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase]'s composed result — an
 * [enrollment] paired with the full [course] detail for [Enrollment.courseId], fetched separately
 * (`GET /courses/{id}`) because `GET /enrollments` itself never embeds course detail (see
 * [Enrollment]'s kdoc). Not a wire shape — no single backend response ever returns this combined
 * shape; it exists purely so Android/iOS share one "my learning" composition instead of each
 * reimplementing it.
 */
data class MyLearningItem(
    val enrollment: Enrollment,
    val course: Course,
)
