package com.mentora.shared.domain.usecase.media

import com.mentora.shared.config.ApiEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `GET /api/v1/media/{id}/file` (`MediaService.publicFile()`/`MediaRoutes.kt:57-59`) sits OUTSIDE
 * `authenticate("jwt-auth")` and needs no signed token at all — genuinely public/unsigned, unlike
 * the lesson-video `/stream` route's `?token=`. So [ResolveThumbnailUrlUseCase] is pure string
 * construction: no query parameter, no network call, no [kotlin.test.Test]-visible suspension.
 */
class ResolveThumbnailUrlUseCaseTest {

    @Test
    fun `builds the plain unsigned public file url, no token query parameter`() {
        val useCase = ResolveThumbnailUrlUseCase(ApiEnvironment.custom("http://10.0.2.2:8080"))

        val url = useCase("thumb-1")

        assertEquals("http://10.0.2.2:8080/api/v1/media/thumb-1/file", url)
    }

    @Test
    fun `resolves against the environment's base url like any other resolved url`() {
        val useCase = ResolveThumbnailUrlUseCase(ApiEnvironment.custom("https://mentora.example.com/"))

        val url = useCase("avatar-9")

        assertEquals("https://mentora.example.com/api/v1/media/avatar-9/file", url)
    }
}
