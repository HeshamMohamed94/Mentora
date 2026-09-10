package com.mentora.backend.instructor.service

import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.courses.repository.resolvedTitle
import com.mentora.backend.instructor.repository.InstructorDashboardDocuments
import com.mentora.backend.instructor.repository.InstructorRepository
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import kotlin.math.roundToInt

@Serializable data class InstructorDashboardStats(
    val totalCourses: Int,
    val publishedCount: Int,
    val totalEnrollments: Int,
)

@Serializable data class InstructorCourseDashboard(
    val id: String,
    val title: String,
    val status: String,
    val enrollmentCount: Int,
    val completionRate: Int,
)

@Serializable data class InstructorDashboardResponse(
    val stats: InstructorDashboardStats,
    val courses: List<InstructorCourseDashboard>,
)

class InstructorService(private val repository: InstructorRepository) {
    suspend fun dashboard(principal: MentoraPrincipal, language: String? = null): InstructorDashboardResponse {
        val documents = repository.dashboard(principal.userId)
        val enrollmentCounts = documents.enrollments.groupingBy { it.courseId }.eachCount()
        val completionRates = documents.progress.groupBy { it.courseId }
            .mapValues { (_, courseProgress) -> courseProgress.map { it.completionPercent }.average().roundToInt() }

        return InstructorDashboardResponse(
            stats = InstructorDashboardStats(
                totalCourses = documents.courses.size,
                publishedCount = documents.courses.count { it.status == "published" },
                totalEnrollments = documents.enrollments.size,
            ),
            courses = documents.courses.map { it.toDashboard(enrollmentCounts, completionRates, language) },
        )
    }

    private fun CourseDocument.toDashboard(
        enrollmentCounts: Map<ObjectId, Int>,
        completionRates: Map<ObjectId, Int>,
        language: String?,
    ): InstructorCourseDashboard {
        val courseId = requireNotNull(id)
        return InstructorCourseDashboard(
            id = courseId.toHexString(),
            title = resolvedTitle(language),
            status = status,
            enrollmentCount = enrollmentCounts[courseId] ?: 0,
            completionRate = completionRates[courseId] ?: 0,
        )
    }
}
