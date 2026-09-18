package com.mentora.shared.data.repository.catalog

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.CourseTranslation
import com.mentora.shared.domain.model.LessonResource
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.FakePreferenceStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun repositoryFor(engine: MockEngine, locale: AppLocale = AppLocale.English): CatalogRepository {
    val apiClient = ApiClient(HttpClientFactory.create(engine, testEnvironment))
    return CatalogRepositoryImpl(apiClient, FakePreferenceStore(initialLocale = locale))
}

/** A realistic 3-section/4-lesson-per-section (12 total) course detail fixture, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 7's "not a trivial 1-lesson stub" test requirement. Every
 * section AND every lesson is deliberately serialized OUT of `order` sequence (sections as
 * order 2, 0, 1 on the wire; lessons within each section as order 3, 0, 2, 1) to genuinely exercise
 * the defensive client-side sort, not just accept whatever order the fixture happens to be typed
 * in. */
private fun courseDetailJson(status: String = "published") = """
{
  "data": {
    "id": "c1", "title": "Kotlin Mastery", "description": "Learn Kotlin end to end",
    "categoryId": "cat1", "level": "intermediate", "contentLanguage": "en",
    "priceDisplay": {"amount": 4999, "currency": "USD"}, "thumbnailMediaId": "m1",
    "status": "$status", "ratingSeed": 4.7, "instructorId": "i1", "instructorName": "Jane Doe",
    "sections": [
      {"sectionId": "s2", "title": "Section C", "order": 2, "lessons": [
        {"lessonId": "l203", "title": "L4", "description": "d", "order": 3, "videoMediaId": "v9", "resources": []},
        {"lessonId": "l200", "title": "L1", "description": "d", "order": 0, "videoMediaId": "v6", "resources": []},
        {"lessonId": "l202", "title": "L3", "description": "d", "order": 2, "videoMediaId": "v8", "resources": []},
        {"lessonId": "l201", "title": "L2", "description": "d", "order": 1, "videoMediaId": "v7", "resources": []}
      ]},
      {"sectionId": "s0", "title": "Section A", "order": 0, "lessons": [
        {"lessonId": "l003", "title": "L4", "description": "d", "order": 3, "videoMediaId": null, "resources": [{"label": "Slides", "url": "http://x/slides"}]},
        {"lessonId": "l000", "title": "L1", "description": "d", "order": 0, "videoMediaId": "v1", "resources": []},
        {"lessonId": "l002", "title": "L3", "description": "d", "order": 2, "videoMediaId": "v3", "resources": []},
        {"lessonId": "l001", "title": "L2", "description": "d", "order": 1, "videoMediaId": "v2", "resources": []}
      ]},
      {"sectionId": "s1", "title": "Section B", "order": 1, "lessons": [
        {"lessonId": "l103", "title": "L4", "description": "d", "order": 3, "videoMediaId": "v5", "resources": []},
        {"lessonId": "l100", "title": "L1", "description": "d", "order": 0, "videoMediaId": null, "resources": []},
        {"lessonId": "l102", "title": "L3", "description": "d", "order": 2, "videoMediaId": "v4", "resources": []},
        {"lessonId": "l101", "title": "L2", "description": "d", "order": 1, "videoMediaId": null, "resources": []}
      ]}
    ],
    "translations": {"ar": {"title": "إتقان Kotlin", "description": "تعلم Kotlin بعمق"}}
  },
  "meta": {"requestId": "r1"}
}
""".trimIndent()

class CatalogRepositoryImplTest {

    // ---- listCategories ----

    @Test
    fun `listCategories maps a plain array response not a CursorPage`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"cat1","name":"Programming","slug":"programming","courseCount":3},{"id":"cat2","name":"Design","slug":"design","courseCount":1}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).listCategories()

        require(result is ApiResult.Success)
        assertEquals(
            listOf(
                Category("cat1", "Programming", "programming", 3),
                Category("cat2", "Design", "design", 1),
            ),
            result.data,
        )
    }

    // ---- getCourseDetails: realistic 3x4 mapping + defensive sort + raw ratingSeed/priceDisplay ----

    @Test
    fun `getCourseDetails maps a realistic 3-section 12-lesson course and sorts by order`() = runTest {
        val engine = MockEngine {
            respond(content = courseDetailJson(), status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        val result = repositoryFor(engine).getCourseDetails("c1")

        require(result is ApiResult.Success)
        val course = result.data
        assertEquals("c1", course.id)
        assertEquals(CourseLevel.Intermediate, course.level)
        assertEquals(ContentLanguage.English, course.contentLanguage)
        assertEquals(CourseStatus.Published, course.status)
        assertEquals(PriceDisplay(4999, "USD"), course.priceDisplay)
        assertEquals(4.7, course.ratingSeed)

        assertEquals(3, course.sections.size)
        assertEquals(listOf("s0", "s1", "s2"), course.sections.map { it.sectionId })
        assertEquals(listOf(0, 1, 2), course.sections.map { it.order })

        course.sections.forEach { section ->
            assertEquals(4, section.lessons.size, "section ${section.sectionId} should have exactly 4 lessons")
            assertEquals(listOf(0, 1, 2, 3), section.lessons.map { it.order }, "lessons in ${section.sectionId} must be order-sorted")
        }
        assertEquals(12, course.sections.sumOf { it.lessons.size })

        val firstLessonOfFirstSection = course.sections.first().lessons.first()
        assertEquals("l000", firstLessonOfFirstSection.lessonId)
        assertEquals("v1", firstLessonOfFirstSection.videoMediaId)
        val lastLessonOfFirstSection = course.sections.first().lessons.last()
        assertEquals(listOf(LessonResource("Slides", "http://x/slides")), lastLessonOfFirstSection.resources)
        assertNull(lastLessonOfFirstSection.videoMediaId)

        assertEquals(mapOf("ar" to CourseTranslation("إتقان Kotlin", "تعلم Kotlin بعمق")), course.translations)
    }

    @Test
    fun `getCourseDetails maps status Draft as-is for an enrolled caller D64 without special handling`() = runTest {
        val engine = MockEngine {
            respond(content = courseDetailJson(status = "draft"), status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        val result = repositoryFor(engine).getCourseDetails("c1")

        require(result is ApiResult.Success)
        assertEquals(CourseStatus.Draft, result.data.status)
    }

    @Test
    fun `getCourseDetails surfaces a draft-not-accessible course as an ordinary 404 CourseNotFound failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"COURSE_NOT_FOUND","message":"The course was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getCourseDetails("c1")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.CourseNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }

    @Test
    fun `getCourseDetails appends language=ar from PreferenceStore when the active locale is Arabic`() = runTest {
        var seenQuery: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters["language"]
            respond(content = courseDetailJson(), status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        repositoryFor(engine, locale = AppLocale.Arabic).getCourseDetails("c1")

        assertEquals("ar", seenQuery)
    }

    @Test
    fun `getCourseDetails appends language=en from PreferenceStore when the active locale is English`() = runTest {
        var seenQuery: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters["language"]
            respond(content = courseDetailJson(), status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        repositoryFor(engine, locale = AppLocale.English).getCourseDetails("c1")

        assertEquals("en", seenQuery)
    }

    // ---- searchCourses: query params + locale threading + raw ratingSeed/priceDisplay ----

    @Test
    fun `searchCourses sends every set filter and the active locale in the request URL`() = runTest {
        var seenQuery: Parameters? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters
            respond(
                content = """{"data":[],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        repositoryFor(engine, locale = AppLocale.Arabic).searchCourses(
            filters = CourseFilters(category = "cat1", level = CourseLevel.Advanced, maxPrice = 1000, query = "kotlin"),
            cursor = "cursor-1",
            limit = 10,
        )

        val query = requireNotNull(seenQuery)
        assertEquals("cat1", query["category"])
        assertEquals("advanced", query["level"])
        assertEquals("1000", query["maxPrice"])
        assertEquals("kotlin", query["q"])
        assertEquals("cursor-1", query["cursor"])
        assertEquals("10", query["limit"])
        assertEquals("ar", query["language"])
    }

    @Test
    fun `searchCourses maps CourseSummary fields including raw ratingSeed and priceDisplay amount`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"c1","title":"Kotlin","description":"desc","categoryId":"cat1","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":2500,"currency":"USD"},"thumbnailMediaId":"m1","ratingSeed":4.5,"instructorId":"i1","instructorName":"Jane"}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).searchCourses()

        require(result is ApiResult.Success)
        val summary = result.data.items.single()
        assertEquals(
            CourseSummary(
                id = "c1", title = "Kotlin", description = "desc", categoryId = "cat1",
                level = CourseLevel.Beginner, contentLanguage = ContentLanguage.English,
                priceDisplay = PriceDisplay(2500, "USD"), thumbnailMediaId = "m1",
                ratingSeed = 4.5, instructorId = "i1", instructorName = "Jane",
            ),
            summary,
        )
    }

    // ---- cursor paging across two pages ----

    @Test
    fun `searchCourses pages through two sequential responses via nextCursor`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request: HttpRequestData ->
            requestCount++
            val cursor = request.url.parameters["cursor"]
            if (cursor == null) {
                respond(
                    content = """{"data":[{"id":"c1","title":"Kotlin","description":"d","categoryId":"cat1","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"USD"},"thumbnailMediaId":null,"ratingSeed":4.0,"instructorId":"i1","instructorName":"Jane"}],"meta":{"requestId":"r1","nextCursor":"cursor-2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = """{"data":[{"id":"c2","title":"Swift","description":"d","categoryId":"cat1","level":"advanced","contentLanguage":"en","priceDisplay":{"amount":200,"currency":"USD"},"thumbnailMediaId":null,"ratingSeed":4.9,"instructorId":"i2","instructorName":"John"}],"meta":{"requestId":"r2"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        val repository = repositoryFor(engine)

        val firstPage = repository.searchCourses(cursor = null)
        require(firstPage is ApiResult.Success)
        assertEquals(listOf("c1"), firstPage.data.items.map { it.id })
        assertEquals("cursor-2", firstPage.data.nextCursor)

        val secondPage = repository.searchCourses(cursor = firstPage.data.nextCursor)
        require(secondPage is ApiResult.Success)
        assertEquals(listOf("c2"), secondPage.data.items.map { it.id })
        assertNull(secondPage.data.nextCursor)

        assertEquals(2, requestCount)
    }
}
