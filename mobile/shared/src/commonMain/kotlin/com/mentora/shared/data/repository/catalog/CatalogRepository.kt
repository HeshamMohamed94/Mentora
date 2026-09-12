package com.mentora.shared.data.repository.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseSummary

/**
 * Caller-chosen search/filter parameters for `GET /api/v1/courses`, verified against
 * `CourseRoutes.kt:34-41`/`CourseService.kt`'s `CourseListQuery`. [category] is the plain
 * ObjectId hex string id — courses are never addressed by slug (there isn't one on [Course]).
 *
 * Deliberately excludes `language`: that query parameter is not a caller-chosen filter here, it is
 * threaded automatically from [com.mentora.shared.settings.PreferenceStore.locale] by
 * [CatalogRepositoryImpl] on every read (`execution/PHASE_3_KMP_PLAN.md` Task 7 AC #5) via
 * [com.mentora.shared.data.network.localeQueryParam] — so a caller can never accidentally pass a UI
 * locale where a [CourseLevel]/content-language filter belongs, or vice versa.
 *
 * Deliberately excludes `status`: `GET /api/v1/courses` has no such filter — the list is always
 * published-only (`CourseRepository.kt:97`'s hardcoded `eq("status", "published")`), never a
 * caller-controllable choice.
 */
data class CourseFilters(
    val category: String? = null,
    val level: CourseLevel? = null,
    val maxPrice: Int? = null,
    val query: String? = null,
)

/**
 * The only catalog network surface `domain/usecase/catalog` use cases are allowed to depend on —
 * mirrors [com.mentora.shared.data.repository.user.UserRepository]'s "interface + Impl" pattern.
 *
 * Categories and courses are grouped into one interface here (rather than split into a separate
 * `CategoryRepository`/`CourseRepository`) because both are small, read-only, and always consumed
 * together by the catalog/search screens Phase 4 builds on top — one Koin binding, one fake needed
 * in tests, per `execution/PHASE_3_KMP_PLAN.md` Task 7's "your call, document the choice."
 */
interface CatalogRepository {
    /** `GET /api/v1/categories` → a plain `List<Category>`, never `CursorPage` (see [Category]'s kdoc). */
    suspend fun listCategories(): ApiResult<List<Category>>

    /**
     * `GET /api/v1/courses` with [filters] plus cursor-based pagination. `?language=` is appended
     * automatically from the active [com.mentora.shared.settings.PreferenceStore.locale] — see
     * [CourseFilters]'s kdoc.
     *
     * On this LIST endpoint, `?language=` is BOTH a metadata-language resolver (which locale's
     * `title`/`description` to display) AND a result-set FILTER: it narrows results to courses
     * whose `contentLanguage` matches the requested locale OR that have a translation entry for it
     * (`CourseRepository.kt:100-103`, D57) — real, verified backend behavior, not a guess. A
     * course is never hidden just because its base `contentLanguage` differs, as long as it has a
     * usable translation for the requested locale.
     *
     * [limit] is optional — when omitted, the backend applies its own default/max (`Pagination
     * .kt`'s `PageRequest.fromCall`).
     */
    suspend fun searchCourses(
        filters: CourseFilters = CourseFilters(),
        cursor: String? = null,
        limit: Int? = null,
    ): ApiResult<CursorPage<CourseSummary>>

    /**
     * `GET /api/v1/courses/{id}` → the full [Course] detail, including `sections`/`translations`.
     * `?language=` is appended the same way as [searchCourses]; here it is ONLY a metadata
     * resolver — there is no result set to filter for a single-course fetch.
     *
     * A draft/unpublished course this caller cannot access surfaces as an ordinary 404
     * `ApiResult.Failure(ApiErrorCode.CourseNotFound)` (never 403) — including for a non-enrolled
     * caller. An ALREADY-enrolled student instead gets a normal 200 [ApiResult.Success] whose
     * [Course.status] is [com.mentora.shared.domain.model.CourseStatus.Draft] (D64 — see that
     * enum's kdoc). `shared` builds no special-case handling around either branch: both are just
     * this endpoint's ordinary response, decoded generically by
     * [com.mentora.shared.data.network.ApiClient].
     */
    suspend fun getCourseDetails(id: String): ApiResult<Course>
}
