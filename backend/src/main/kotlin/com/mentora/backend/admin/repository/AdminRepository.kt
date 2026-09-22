package com.mentora.backend.admin.repository

import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.enrollment.repository.EnrollmentDocument
import com.mentora.backend.users.repository.UserDocument
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Filters.regex
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.util.regex.Pattern

data class AdminDashboardCounts(
    val totalCourses: Long,
    val publishedCourses: Long,
    val totalStudents: Long,
    val totalInstructors: Long,
)

class AdminRepository(database: MongoDatabase) {
    private val courses = database.getCollection<CourseDocument>("courses")
    private val users = database.getCollection<UserDocument>("users")
    private val enrollments = database.getCollection<EnrollmentDocument>("enrollments")

    suspend fun dashboard(): AdminDashboardCounts = AdminDashboardCounts(
        totalCourses = courses.countDocuments(),
        publishedCourses = courses.countDocuments(eq("status", "published")),
        totalStudents = users.countDocuments(eq("role", Role.student.name)),
        totalInstructors = users.countDocuments(eq("role", Role.instructor.name)),
    )

    suspend fun listCourses(query: String?, page: PageRequest): List<CourseDocument> {
        val filters = pageFilters(page, query?.takeIf { it.isNotBlank() }?.let(::courseSearch))
        val matches = if (filters.isEmpty()) courses.find() else courses.find(and(filters))
        return matches.sort(ascending("_id")).limit(page.limit + 1).toList()
    }

    suspend fun listUsers(role: Role, query: String?, page: PageRequest): List<UserDocument> {
        val filters = buildList {
            add(eq("role", role.name))
            query?.takeIf { it.isNotBlank() }?.let { add(userSearch(it)) }
            page.cursor?.let { add(gt("_id", it)) }
        }
        return users.find(and(filters)).sort(ascending("_id")).limit(page.limit + 1).toList()
    }

    suspend fun usersByIds(ids: List<ObjectId>): List<UserDocument> =
        if (ids.isEmpty()) emptyList() else users.find(`in`("_id", ids)).toList()

    suspend fun enrollmentsByCourseIds(ids: List<ObjectId>): List<EnrollmentDocument> =
        if (ids.isEmpty()) emptyList() else enrollments.find(`in`("courseId", ids)).toList()

    suspend fun enrollmentsByUserIds(ids: List<ObjectId>): List<EnrollmentDocument> =
        if (ids.isEmpty()) emptyList() else enrollments.find(`in`("userId", ids)).toList()

    suspend fun coursesByInstructorIds(ids: List<ObjectId>): List<CourseDocument> =
        if (ids.isEmpty()) emptyList() else courses.find(`in`("instructorId", ids)).toList()

    private fun pageFilters(page: PageRequest, filter: Bson?): List<Bson> = buildList {
        filter?.let(::add)
        page.cursor?.let { add(gt("_id", it)) }
    }

    private fun userSearch(query: String): Bson {
        val escapedQuery = Pattern.quote(query)
        return or(regex("name", escapedQuery, "i"), regex("email", escapedQuery, "i"))
    }

    /** Substring match on `title`/`description`, deliberately NOT MongoDB `$text` search: `$text`
     * ranks by relevance score, but this list is paginated by sorting on `_id` ascending (§ Pagination
     * contract) with no relevance sort applied — combined with `$text`'s broad OR-across-terms
     * matching, that silently dropped genuinely-relevant results (e.g. a just-created course) past
     * the first page whenever the catalog held more matches than the page limit (Phase 8 B1, D-10).
     * `userSearch` above already uses this same regex pattern for the identical reason. */
    private fun courseSearch(query: String): Bson {
        val escapedQuery = Pattern.quote(query)
        return or(regex("title", escapedQuery, "i"), regex("description", escapedQuery, "i"))
    }
}
