package com.mentora.shared.data.repository.catalog

import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.settings.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CourseListQueryParamsTest {

    @Test
    fun `every filter is included when set`() {
        val params = courseListQueryParams(
            filters = CourseFilters(category = "cat1", level = CourseLevel.Advanced, maxPrice = 5000, query = "kotlin"),
            cursor = "cursor-1",
            limit = 20,
            locale = AppLocale.English,
        )

        assertEquals(
            mapOf(
                "category" to "cat1",
                "level" to "advanced",
                "maxPrice" to "5000",
                "q" to "kotlin",
                "cursor" to "cursor-1",
                "limit" to "20",
                "language" to "en",
            ),
            params,
        )
    }

    @Test
    fun `every filter is omitted when unset, only language remains`() {
        val params = courseListQueryParams(
            filters = CourseFilters(),
            cursor = null,
            limit = null,
            locale = AppLocale.English,
        )

        assertEquals(mapOf("language" to "en"), params)
        assertFalse(params.containsKey("category"))
        assertFalse(params.containsKey("level"))
        assertFalse(params.containsKey("maxPrice"))
        assertFalse(params.containsKey("q"))
        assertFalse(params.containsKey("cursor"))
        assertFalse(params.containsKey("limit"))
    }

    @Test
    fun `category alone is included without the other filters`() {
        val params = courseListQueryParams(CourseFilters(category = "cat1"), null, null, AppLocale.English)

        assertEquals("cat1", params["category"])
        assertFalse(params.containsKey("level"))
        assertFalse(params.containsKey("maxPrice"))
        assertFalse(params.containsKey("q"))
    }

    @Test
    fun `level alone is included as its wire value`() {
        val params = courseListQueryParams(CourseFilters(level = CourseLevel.Beginner), null, null, AppLocale.English)

        assertEquals("beginner", params["level"])
        assertFalse(params.containsKey("category"))
    }

    @Test
    fun `maxPrice alone is included as a plain integer string`() {
        val params = courseListQueryParams(CourseFilters(maxPrice = 999), null, null, AppLocale.English)

        assertEquals("999", params["maxPrice"])
    }

    @Test
    fun `query alone is included under the q key`() {
        val params = courseListQueryParams(CourseFilters(query = "android"), null, null, AppLocale.English)

        assertEquals("android", params["q"])
    }

    @Test
    fun `language reflects the active locale`() {
        val params = courseListQueryParams(CourseFilters(), null, null, AppLocale.Arabic)

        assertEquals("ar", params["language"])
    }
}
