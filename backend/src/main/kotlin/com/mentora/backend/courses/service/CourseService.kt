package com.mentora.backend.courses.service

import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Page
import com.mentora.backend.common.PageRequest
import com.mentora.backend.common.Role
import com.mentora.backend.common.toPage
import com.mentora.backend.courses.repository.CourseDocument
import com.mentora.backend.courses.repository.CourseRepository
import com.mentora.backend.courses.repository.CourseResource
import com.mentora.backend.courses.repository.Lesson
import com.mentora.backend.courses.repository.PriceDisplay
import com.mentora.backend.courses.repository.PublishedCourseFilter
import com.mentora.backend.courses.repository.Section
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.UUID

@Serializable data class PriceDisplayDto(val amount: Int, val currency: String)
@Serializable data class ResourceDto(val label: String, val url: String)
@Serializable data class LessonResponse(
    val lessonId: String, val title: String, val description: String, val order: Int,
    val videoMediaId: String?, val resources: List<ResourceDto>,
)
@Serializable data class SectionResponse(
    val sectionId: String, val title: String, val order: Int, val lessons: List<LessonResponse>,
)
@Serializable data class CourseSummary(
    val id: String, val title: String, val description: String, val categoryId: String, val level: String,
    val contentLanguage: String, val priceDisplay: PriceDisplayDto, val thumbnailMediaId: String?,
    val ratingSeed: Double, val instructorId: String,
)
@Serializable data class CourseResponse(
    val id: String, val title: String, val description: String, val categoryId: String, val level: String,
    val contentLanguage: String, val priceDisplay: PriceDisplayDto, val thumbnailMediaId: String?,
    val status: String, val ratingSeed: Double, val instructorId: String, val sections: List<SectionResponse>,
)
@Serializable data class CreateCourseRequest(
    val title: String, val description: String, val categoryId: String, val level: String,
    val contentLanguage: String, val priceDisplay: PriceDisplayDto, val thumbnailMediaId: String? = null,
)
@Serializable data class UpdateCourseRequest(
    val title: String? = null, val description: String? = null, val categoryId: String? = null,
    val level: String? = null, val contentLanguage: String? = null, val priceDisplay: PriceDisplayDto? = null,
    val thumbnailMediaId: String? = null,
)
@Serializable data class SectionTitleRequest(val title: String)
@Serializable data class ReorderSectionsRequest(val sectionIds: List<String>)
@Serializable data class CreateLessonRequest(
    val title: String, val description: String, val videoMediaId: String? = null,
    val resources: List<ResourceDto> = emptyList(),
)
@Serializable data class UpdateLessonRequest(
    val title: String? = null, val description: String? = null, val videoMediaId: String? = null,
    val resources: List<ResourceDto>? = null,
)
@Serializable data class ReorderLessonsRequest(val lessonIds: List<String>)
data class CourseListQuery(
    val category: String?, val level: String?, val maxPrice: String?, val keyword: String?, val page: PageRequest,
)

class CourseService(
    private val repository: CourseRepository,
    private val categories: CategoryService,
) {
    suspend fun list(query: CourseListQuery): Page<CourseSummary> {
        val price = query.maxPrice?.toIntOrNull()?.takeIf { it >= 0 }
            ?: query.maxPrice?.let { throw ApiException.Validation(fields = mapOf("maxPrice" to "INVALID")) }
        val filter = PublishedCourseFilter(
            query.category?.let { objectId(it, "category") }, query.level?.let { validateLevel(it) }, price,
            query.keyword?.trim(), query.page.cursor, query.page.limit,
        )
        val documents = repository.listPublished(filter)
        return documents.toPage(query.page.limit) { requireNotNull(it.id) }.let { result ->
            Page(result.items.map { it.toSummary() }, result.nextCursor)
        }
    }

    suspend fun get(id: String, principal: MentoraPrincipal?): CourseResponse {
        val course = findCourse(id)
        if (course.status == DRAFT && principal?.role != Role.admin && principal?.userId != course.instructorId) {
            throw courseNotFound()
        }
        return course.toResponse()
    }

    suspend fun create(principal: MentoraPrincipal, request: CreateCourseRequest): CourseResponse {
        val categoryId = objectId(request.categoryId, "categoryId")
        categories.requireExists(categoryId)
        val now = Clock.System.now()
        val course = repository.insert(CourseDocument(
            instructorId = principal.userId,
            title = required(request.title, "title"),
            description = required(request.description, "description"),
            categoryId = categoryId,
            level = validateLevel(request.level),
            contentLanguage = validateLanguage(request.contentLanguage),
            priceDisplay = validatePrice(request.priceDisplay),
            thumbnailMediaId = request.thumbnailMediaId?.let { objectId(it, "thumbnailMediaId") },
            status = DRAFT,
            ratingSeed = 4.5,
            createdAt = now,
            updatedAt = now,
        ))
        categories.adjustCourseCount(categoryId, 1)
        return course.toResponse()
    }

    suspend fun update(principal: MentoraPrincipal, id: String, request: UpdateCourseRequest): CourseResponse {
        val current = ownedCourse(principal, id)
        val nextCategoryId = request.categoryId?.let { objectId(it, "categoryId") } ?: current.categoryId
        if (nextCategoryId != current.categoryId) categories.requireExists(nextCategoryId)
        val updated = current.copy(
            title = request.title?.let { required(it, "title") } ?: current.title,
            description = request.description?.let { required(it, "description") } ?: current.description,
            categoryId = nextCategoryId,
            level = request.level?.let { validateLevel(it) } ?: current.level,
            contentLanguage = request.contentLanguage?.let { validateLanguage(it) } ?: current.contentLanguage,
            priceDisplay = request.priceDisplay?.let { validatePrice(it) } ?: current.priceDisplay,
            thumbnailMediaId = request.thumbnailMediaId?.let { objectId(it, "thumbnailMediaId") }
                ?: current.thumbnailMediaId,
            updatedAt = Clock.System.now(),
        )
        val saved = save(updated)
        if (nextCategoryId != current.categoryId) {
            categories.adjustCourseCount(current.categoryId, -1)
            categories.adjustCourseCount(nextCategoryId, 1)
        }
        return saved.toResponse()
    }

    suspend fun addSection(principal: MentoraPrincipal, id: String, request: SectionTitleRequest): CourseResponse {
        val course = ownedCourse(principal, id)
        val section = Section(UUID.randomUUID().toString(), required(request.title, "title"), course.sections.size)
        return save(course.copy(sections = course.sections + section, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun updateSection(principal: MentoraPrincipal, id: String, sectionId: String, request: SectionTitleRequest): CourseResponse {
        val course = ownedCourse(principal, id)
        ensureSection(course, sectionId)
        val sections = course.sections.map { if (it.sectionId == sectionId) it.copy(title = required(request.title, "title")) else it }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun deleteSection(principal: MentoraPrincipal, id: String, sectionId: String): CourseResponse {
        val course = ownedCourse(principal, id)
        ensureSection(course, sectionId)
        val sections = course.sections.filterNot { it.sectionId == sectionId }.mapIndexed { index, section -> section.copy(order = index) }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun reorderSections(principal: MentoraPrincipal, id: String, request: ReorderSectionsRequest): CourseResponse {
        val course = ownedCourse(principal, id)
        validateExactIds(request.sectionIds, course.sections.map { it.sectionId }, "sectionIds")
        val byId = course.sections.associateBy { it.sectionId }
        val sections = request.sectionIds.mapIndexed { index, sectionId -> requireNotNull(byId[sectionId]).copy(order = index) }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun addLesson(
        principal: MentoraPrincipal, id: String, sectionId: String, request: CreateLessonRequest,
    ): CourseResponse {
        val course = ownedCourse(principal, id)
        val section = ensureSection(course, sectionId)
        val lesson = Lesson(
            UUID.randomUUID().toString(), required(request.title, "title"), required(request.description, "description"),
            section.lessons.size, request.videoMediaId?.let { objectId(it, "videoMediaId") }, validateResources(request.resources),
        )
        val sections = course.sections.map { if (it.sectionId == sectionId) it.copy(lessons = it.lessons + lesson) else it }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun updateLesson(
        principal: MentoraPrincipal, id: String, sectionId: String, lessonId: String, request: UpdateLessonRequest,
    ): CourseResponse {
        val course = ownedCourse(principal, id)
        val section = ensureSection(course, sectionId)
        ensureLesson(section, lessonId)
        val lessons = section.lessons.map { lesson -> if (lesson.lessonId != lessonId) lesson else lesson.copy(
            title = request.title?.let { required(it, "title") } ?: lesson.title,
            description = request.description?.let { required(it, "description") } ?: lesson.description,
            videoMediaId = request.videoMediaId?.let { objectId(it, "videoMediaId") } ?: lesson.videoMediaId,
            resources = request.resources?.let { validateResources(it) } ?: lesson.resources,
        ) }
        val sections = course.sections.map { if (it.sectionId == sectionId) it.copy(lessons = lessons) else it }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun deleteLesson(principal: MentoraPrincipal, id: String, sectionId: String, lessonId: String): CourseResponse {
        val course = ownedCourse(principal, id)
        val section = ensureSection(course, sectionId)
        ensureLesson(section, lessonId)
        val lessons = section.lessons.filterNot { it.lessonId == lessonId }.mapIndexed { index, lesson -> lesson.copy(order = index) }
        val sections = course.sections.map { if (it.sectionId == sectionId) it.copy(lessons = lessons) else it }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun reorderLessons(
        principal: MentoraPrincipal, id: String, sectionId: String, request: ReorderLessonsRequest,
    ): CourseResponse {
        val course = ownedCourse(principal, id)
        val section = ensureSection(course, sectionId)
        validateExactIds(request.lessonIds, section.lessons.map { it.lessonId }, "lessonIds")
        val byId = section.lessons.associateBy { it.lessonId }
        val lessons = request.lessonIds.mapIndexed { index, lessonId -> requireNotNull(byId[lessonId]).copy(order = index) }
        val sections = course.sections.map { if (it.sectionId == sectionId) it.copy(lessons = lessons) else it }
        return save(course.copy(sections = sections, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun publish(principal: MentoraPrincipal, id: String): CourseResponse {
        val course = ownedCourse(principal, id)
        val fields = linkedMapOf<String, String>()
        if (course.title.isBlank()) fields["title"] = "REQUIRED"
        if (course.description.isBlank()) fields["description"] = "REQUIRED"
        if (course.categoryId.toHexString().isBlank()) fields["categoryId"] = "REQUIRED"
        if (course.priceDisplay.amount < 0 || course.priceDisplay.currency.isBlank()) fields["priceDisplay"] = "REQUIRED"
        if (course.thumbnailMediaId == null) fields["thumbnail"] = "REQUIRED"
        if (course.sections.isEmpty()) fields["curriculum"] = "NO_SECTIONS"
        course.sections.filter { it.lessons.isEmpty() }.forEach { fields["section.${it.sectionId}.lessons"] = "NO_LESSONS" }
        course.sections.flatMap { it.lessons }.filter { it.videoMediaId == null }
            .forEach { fields["lesson.${it.lessonId}.videoMediaId"] = "REQUIRED" }
        if (fields.isNotEmpty()) throw ApiException.Validation("The course is not ready to publish.", fields)
        return save(course.copy(status = PUBLISHED, updatedAt = Clock.System.now())).toResponse()
    }

    suspend fun unpublish(principal: MentoraPrincipal, id: String): CourseResponse {
        val course = findCourse(id)
        if (principal.role != Role.admin && principal.userId != course.instructorId) throw ApiException.ForbiddenNotOwner()
        return save(course.copy(status = DRAFT, updatedAt = Clock.System.now())).toResponse()
    }

    private suspend fun ownedCourse(principal: MentoraPrincipal, id: String): CourseDocument {
        val course = findCourse(id)
        if (course.instructorId != principal.userId) throw ApiException.ForbiddenNotOwner()
        return course
    }
    private suspend fun findCourse(id: String) = repository.findById(objectId(id, "id")) ?: throw courseNotFound()
    private suspend fun save(course: CourseDocument) = repository.replace(course) ?: throw courseNotFound()
    private fun ensureSection(course: CourseDocument, id: String) = course.sections.firstOrNull { it.sectionId == id }
        ?: throw ApiException.NotFound("SECTION_NOT_FOUND", "The section was not found.")
    private fun ensureLesson(section: Section, id: String) = section.lessons.firstOrNull { it.lessonId == id }
        ?: throw ApiException.NotFound("LESSON_NOT_FOUND", "The lesson was not found.")
    private fun validateExactIds(given: List<String>, existing: List<String>, field: String) {
        if (given.size != existing.size || given.toSet().size != given.size || given.toSet() != existing.toSet()) {
            throw ApiException.Validation(fields = mapOf(field to "MUST_MATCH_EXISTING_IDS"))
        }
    }
    private fun required(value: String, field: String) = value.trim().also {
        if (it.isBlank()) throw ApiException.Validation(fields = mapOf(field to "REQUIRED"))
    }
    private fun validateLevel(value: String) = value.lowercase().also {
        if (it !in LEVELS) throw ApiException.Validation(fields = mapOf("level" to "UNSUPPORTED"))
    }
    private fun validateLanguage(value: String) = value.lowercase().also {
        if (it !in LANGUAGES) throw ApiException.Validation(fields = mapOf("contentLanguage" to "UNSUPPORTED"))
    }
    private fun validatePrice(dto: PriceDisplayDto): PriceDisplay {
        if (dto.amount < 0) throw ApiException.Validation(fields = mapOf("priceDisplay.amount" to "INVALID"))
        val currency = dto.currency.trim().uppercase()
        if (!Regex("[A-Z]{3}").matches(currency)) throw ApiException.Validation(fields = mapOf("priceDisplay.currency" to "INVALID"))
        return PriceDisplay(dto.amount, currency)
    }
    private fun validateResources(resources: List<ResourceDto>) = resources.mapIndexed { index, resource ->
        val label = required(resource.label, "resources.$index.label")
        val url = required(resource.url, "resources.$index.url")
        CourseResource(label, url)
    }
    private fun objectId(value: String, field: String) = try { ObjectId(value) } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf(field to "INVALID"))
    }
    private fun courseNotFound() = ApiException.NotFound("COURSE_NOT_FOUND", "The course was not found.")
    private fun CourseDocument.toSummary() = CourseSummary(
        requireNotNull(id).toHexString(), title, description, categoryId.toHexString(), level, contentLanguage,
        priceDisplay.toDto(), thumbnailMediaId?.toHexString(), ratingSeed, instructorId.toHexString(),
    )
    private fun CourseDocument.toResponse() = CourseResponse(
        requireNotNull(id).toHexString(), title, description, categoryId.toHexString(), level, contentLanguage,
        priceDisplay.toDto(), thumbnailMediaId?.toHexString(), status, ratingSeed, instructorId.toHexString(),
        sections.sortedBy { it.order }.map { it.toResponse() },
    )
    private fun PriceDisplay.toDto() = PriceDisplayDto(amount, currency)
    private fun Section.toResponse() = SectionResponse(sectionId, title, order, lessons.sortedBy { it.order }.map { it.toResponse() })
    private fun Lesson.toResponse() = LessonResponse(
        lessonId, title, description, order, videoMediaId?.toHexString(), resources.map { ResourceDto(it.label, it.url) },
    )
    private companion object {
        const val DRAFT = "draft"
        const val PUBLISHED = "published"
        val LEVELS = setOf("beginner", "intermediate", "advanced")
        val LANGUAGES = setOf("en", "ar")
    }
}
