package com.mentora.backend.admin.service

import com.mentora.backend.admin.repository.AdminRepository
import com.mentora.backend.common.Page
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.toPage
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.users.repository.UserDocument
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class AdminDashboardResponse(
    val totalCourses: Int,
    val publishedCourses: Int,
    val draftCourses: Int,
    val totalStudents: Int,
    val totalInstructors: Int,
)

@Serializable data class AdminCourseResponse(
    val id: String,
    val title: String,
    val instructorName: String,
    val status: String,
    val enrollmentCount: Int,
)

@Serializable data class AdminUserResponse(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Instant,
    val enrollmentCount: Int,
)

@Serializable data class AdminInstructorResponse(
    val id: String,
    val name: String,
    val email: String,
    val courseCount: Int,
    val publishedCount: Int,
)

class AdminService(private val repository: AdminRepository) {
    suspend fun dashboard(): AdminDashboardResponse {
        val counts = repository.dashboard()
        return AdminDashboardResponse(
            totalCourses = counts.totalCourses.toInt(),
            publishedCourses = counts.publishedCourses.toInt(),
            draftCourses = (counts.totalCourses - counts.publishedCourses).toInt(),
            totalStudents = counts.totalStudents.toInt(),
            totalInstructors = counts.totalInstructors.toInt(),
        )
    }

    suspend fun courses(query: String?, page: PageRequest): Page<AdminCourseResponse> {
        val coursePage = repository.listCourses(query?.trim(), page).toPage(page.limit) { requireNotNull(it.id) }
        val courseIds = coursePage.items.map { requireNotNull(it.id) }
        val instructorNames = repository.usersByIds(coursePage.items.map { it.instructorId }.distinct())
            .associate { requireNotNull(it.id) to it.name }
        val enrollmentCounts = repository.enrollmentsByCourseIds(courseIds).groupingBy { it.courseId }.eachCount()
        return Page(coursePage.items.map { it.toResponse(instructorNames, enrollmentCounts) }, coursePage.nextCursor)
    }

    suspend fun users(query: String?, page: PageRequest): Page<AdminUserResponse> {
        val userPage = repository.listUsers(Role.student, query?.trim(), page).toPage(page.limit) { requireNotNull(it.id) }
        val userIds = userPage.items.map { requireNotNull(it.id) }
        val enrollmentCounts = repository.enrollmentsByUserIds(userIds).groupingBy { it.userId }.eachCount()
        return Page(userPage.items.map { it.toUserResponse(enrollmentCounts) }, userPage.nextCursor)
    }

    suspend fun instructors(query: String?, page: PageRequest): Page<AdminInstructorResponse> {
        val instructorPage = repository.listUsers(Role.instructor, query?.trim(), page)
            .toPage(page.limit) { requireNotNull(it.id) }
        val instructorIds = instructorPage.items.map { requireNotNull(it.id) }
        val coursesByInstructor = repository.coursesByInstructorIds(instructorIds).groupBy { it.instructorId }
        return Page(
            instructorPage.items.map { it.toInstructorResponse(coursesByInstructor) },
            instructorPage.nextCursor,
        )
    }

    private fun CourseDocument.toResponse(
        instructorNames: Map<ObjectId, String>,
        enrollmentCounts: Map<ObjectId, Int>,
    ): AdminCourseResponse {
        val courseId = requireNotNull(id)
        return AdminCourseResponse(
            id = courseId.toHexString(),
            title = title,
            instructorName = requireNotNull(instructorNames[instructorId]),
            status = status,
            enrollmentCount = enrollmentCounts[courseId] ?: 0,
        )
    }

    private fun UserDocument.toUserResponse(enrollmentCounts: Map<ObjectId, Int>): AdminUserResponse {
        val userId = requireNotNull(id)
        return AdminUserResponse(
            id = userId.toHexString(), name = name, email = email, createdAt = createdAt,
            enrollmentCount = enrollmentCounts[userId] ?: 0,
        )
    }

    private fun UserDocument.toInstructorResponse(
        coursesByInstructor: Map<ObjectId, List<CourseDocument>>,
    ): AdminInstructorResponse {
        val instructorId = requireNotNull(id)
        val ownedCourses = coursesByInstructor[instructorId].orEmpty()
        return AdminInstructorResponse(
            id = instructorId.toHexString(), name = name, email = email,
            courseCount = ownedCourses.size, publishedCount = ownedCourses.count { it.status == "published" },
        )
    }
}
