package com.mentora.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ApiEnvironment.resolveUrl]'s only job — never produce a double `//` or a missing `/` regardless
 * of whether [ApiEnvironment.baseUrl] has a trailing slash or the relative path has a leading one.
 * `execution/PHASE_3_KMP_PLAN.md` Task 13's test list calls this out explicitly.
 */
class ApiEnvironmentTest {

    @Test
    fun `base without trailing slash and path with leading slash`() {
        val environment = ApiEnvironment.custom("http://10.0.2.2:8080")

        assertEquals(
            "http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc",
            environment.resolveUrl("/api/v1/media/m1/stream?token=abc"),
        )
    }

    @Test
    fun `base with trailing slash and path with leading slash`() {
        val environment = ApiEnvironment.custom("http://10.0.2.2:8080/")

        assertEquals(
            "http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc",
            environment.resolveUrl("/api/v1/media/m1/stream?token=abc"),
        )
    }

    @Test
    fun `base without trailing slash and path without leading slash`() {
        val environment = ApiEnvironment.custom("http://10.0.2.2:8080")

        assertEquals(
            "http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc",
            environment.resolveUrl("api/v1/media/m1/stream?token=abc"),
        )
    }

    @Test
    fun `base with trailing slash and path without leading slash`() {
        val environment = ApiEnvironment.custom("http://10.0.2.2:8080/")

        assertEquals(
            "http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc",
            environment.resolveUrl("api/v1/media/m1/stream?token=abc"),
        )
    }

    @Test
    fun `never collapses a path that only contains a single leading slash into an empty segment`() {
        val environment = ApiEnvironment.custom("https://mentora.example.com")

        assertEquals(
            "https://mentora.example.com/api/v1/media/m1/file",
            environment.resolveUrl("/api/v1/media/m1/file"),
        )
    }
}
