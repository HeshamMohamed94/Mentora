package com.mentora.backend.instructor.repository

import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.enrollment.repository.EnrollmentDocument
import com.mentora.backend.progress.repository.ProgressDocument
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

data class InstructorDashboardDocuments(
    val courses: List<CourseDocument>,
    val enrollments: List<EnrollmentDocument>,
    val progress: List<ProgressDocument>,
)

class InstructorRepository(database: MongoDatabase) {
    private val courses = database.getCollection<CourseDocument>("courses")
    private val enrollments = database.getCollection<EnrollmentDocument>("enrollments")
    private val progress = database.getCollection<ProgressDocument>("progress")

    suspend fun dashboard(instructorId: ObjectId): InstructorDashboardDocuments {
        val ownedCourses = courses.find(eq("instructorId", instructorId)).sort(ascending("_id")).toList()
        val courseIds = ownedCourses.map { requireNotNull(it.id) }
        if (courseIds.isEmpty()) return InstructorDashboardDocuments(emptyList(), emptyList(), emptyList())

        return InstructorDashboardDocuments(
            courses = ownedCourses,
            enrollments = enrollments.find(`in`("courseId", courseIds)).toList(),
            progress = progress.find(`in`("courseId", courseIds)).toList(),
        )
    }
}
