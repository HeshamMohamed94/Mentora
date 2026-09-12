package com.mentora.shared.data.repository.catalog

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.network.dto.CategoryDto
import com.mentora.shared.data.network.dto.CourseDto
import com.mentora.shared.data.network.dto.CourseSummaryDto
import com.mentora.shared.data.network.dto.CourseTranslationDto
import com.mentora.shared.data.network.dto.LessonDto
import com.mentora.shared.data.network.dto.LessonResourceDto
import com.mentora.shared.data.network.dto.PriceDisplayDto
import com.mentora.shared.data.network.dto.SectionDto
import com.mentora.shared.data.network.localeQueryParam
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.CourseTranslation
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.LessonResource
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.Section
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.PreferenceStore

/**
 * The real [CatalogRepository]. Every read appends `?language=` from [preferenceStore]'s current
 * [AppLocale] via [localeQueryParam] — the one shared mechanism `execution/PHASE_3_KMP_PLAN.md`
 * Task 7 AC #5 asks Task 8 (checkout preview)/Task 12 (learning-path detail) to reuse rather than
 * re-implementing.
 */
internal class CatalogRepositoryImpl(
    private val apiClient: ApiClient,
    private val preferenceStore: PreferenceStore,
) : CatalogRepository {

    override suspend fun listCategories(): ApiResult<List<Category>> =
        when (val result = apiClient.get<List<CategoryDto>>("/api/v1/categories")) {
            is ApiResult.Success -> ApiResult.Success(result.data.map { it.toDomain() })
            is ApiResult.Failure -> result
        }

    override suspend fun searchCourses(
        filters: CourseFilters,
        cursor: String?,
        limit: Int?,
    ): ApiResult<CursorPage<CourseSummary>> {
        val queryParams = courseListQueryParams(filters, cursor, limit, preferenceStore.locale.value)
        return when (val result = apiClient.getPage<CourseSummaryDto>("/api/v1/courses", queryParams)) {
            is ApiResult.Success -> ApiResult.Success(
                CursorPage(items = result.data.items.map { it.toDomain() }, nextCursor = result.data.nextCursor),
            )
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getCourseDetails(id: String): ApiResult<Course> {
        val (key, value) = localeQueryParam(preferenceStore.locale.value)
        return when (val result = apiClient.get<CourseDto>("/api/v1/courses/$id", mapOf(key to value))) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    private fun CategoryDto.toDomain(): Category = Category(id, name, slug, courseCount)

    private fun PriceDisplayDto.toDomain(): PriceDisplay = PriceDisplay(amount, currency)

    private fun LessonResourceDto.toDomain(): LessonResource = LessonResource(label, url)

    private fun LessonDto.toDomain(): Lesson =
        Lesson(lessonId, title, description, order, videoMediaId, resources.map { it.toDomain() })

    /** Sorted by [Lesson.order] defensively — see [Section]'s kdoc for why, given the backend
     * already sends lessons pre-sorted. */
    private fun SectionDto.toDomain(): Section =
        Section(sectionId, title, order, lessons.map { it.toDomain() }.sortedBy { it.order })

    private fun CourseTranslationDto.toDomain(): CourseTranslation = CourseTranslation(title, description)

    private fun CourseSummaryDto.toDomain(): CourseSummary = CourseSummary(
        id = id, title = title, description = description, categoryId = categoryId,
        level = level, contentLanguage = contentLanguage, priceDisplay = priceDisplay.toDomain(),
        thumbnailMediaId = thumbnailMediaId, ratingSeed = ratingSeed,
        instructorId = instructorId, instructorName = instructorName,
    )

    /** Sorted by [Section.order] defensively — see [Section]'s kdoc. */
    private fun CourseDto.toDomain(): Course = Course(
        id = id, title = title, description = description, categoryId = categoryId,
        level = level, contentLanguage = contentLanguage, priceDisplay = priceDisplay.toDomain(),
        thumbnailMediaId = thumbnailMediaId, status = status, ratingSeed = ratingSeed,
        instructorId = instructorId, instructorName = instructorName,
        sections = sections.map { it.toDomain() }.sortedBy { it.order },
        translations = translations.mapValues { (_, translation) -> translation.toDomain() },
    )
}

/**
 * Builds the exact `GET /api/v1/courses` query-parameter map: each of [filters]'s fields is
 * included only when set (never sent as an empty/blank string placeholder), plus `cursor`/`limit`
 * when present, plus the active [locale] threaded per `execution/PHASE_3_KMP_PLAN.md` Task 7 AC #5.
 * A standalone top-level function (rather than logic buried inside [CatalogRepositoryImpl]) so the
 * exact param set is directly unit-testable without a `MockEngine` round trip.
 */
fun courseListQueryParams(
    filters: CourseFilters,
    cursor: String?,
    limit: Int?,
    locale: AppLocale,
): Map<String, String> = buildMap {
    filters.category?.let { put("category", it) }
    filters.level?.let { put("level", it.wireValue) }
    filters.maxPrice?.let { put("maxPrice", it.toString()) }
    filters.query?.let { put("q", it) }
    cursor?.let { put("cursor", it) }
    limit?.let { put("limit", it.toString()) }
    val (localeKey, localeValue) = localeQueryParam(locale)
    put(localeKey, localeValue)
}
