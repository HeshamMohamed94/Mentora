package com.mentora.shared.data.repository.learningpath

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.LearningPathDetail

/**
 * The only learning-path network surface `domain/usecase/learningpath` use cases are allowed to
 * depend on — mirrors [com.mentora.shared.data.repository.certificate.CertificateRepository]'s
 * "interface + Impl" pattern (`execution/PHASE_3_KMP_PLAN.md` Task 12).
 *
 * Exposes no path-authoring/creation/course-reordering method — no such backend write path exists
 * (verified: `LearningPathRoutes.kt` defines exactly `GET /learning-paths`, `GET /learning-paths/{id}`,
 * `POST .../follow`, `DELETE .../follow` — nothing else).
 */
interface LearningPathRepository {
    /**
     * `GET /api/v1/learning-paths` → a plain, UNPAGINATED `List<LearningPath>` — never
     * `CursorPage<LearningPath>` (see [LearningPath]'s kdoc). Not under `authenticate` at all
     * server-side (`LearningPathRoutes.kt:20`) — callable with no auth state whatsoever, guest or
     * authenticated, with an identical response either way.
     */
    suspend fun listLearningPaths(): ApiResult<List<LearningPath>>

    /**
     * `GET /api/v1/learning-paths/{id}?language=` → [LearningPathDetail]. `?language=` is threaded
     * from the active [com.mentora.shared.settings.PreferenceStore] locale via
     * [com.mentora.shared.data.network.localeQueryParam] — the same shared mechanism Task 7 built
     * for the course list/detail reads.
     *
     * Optional-auth, guest-safe server-side (`authenticate("jwt-auth", optional = true)`) — a guest
     * gets back [LearningPathDetail.progressPercent] `null` and [LearningPathDetail.isFollowing]
     * `false`; a nonexistent [id] surfaces as an ordinary 404 `LEARNING_PATH_NOT_FOUND`
     * [ApiResult.Failure].
     */
    suspend fun getLearningPath(id: String): ApiResult<LearningPathDetail>

    /**
     * `POST /api/v1/learning-paths/{id}/follow` → the resulting `isFollowing` value (always `true`
     * on success — `LearningPathService.follow()` responds unconditionally, never re-queried).
     * Student role required server-side (`authenticate("jwt-auth")` + `call.student()`); the CSRF
     * header is attached globally by `HttpClientFactory` (Task 3), nothing extra needed here.
     * Idempotent: following an already-followed path a second time is an ordinary
     * [ApiResult.Success]`(true)`, never an error (the backend's `follows.updateOne(..., upsert =
     * true)` with `setOnInsert` is a no-op on a second call).
     */
    suspend fun follow(id: String): ApiResult<Boolean>

    /**
     * `DELETE /api/v1/learning-paths/{id}/follow` → the resulting `isFollowing` value (always
     * `false` on success). Idempotent: unfollowing a path that isn't followed is an ordinary
     * [ApiResult.Success]`(false)`, never an error (`follows.deleteOne(...)` is a no-op if no
     * matching document exists).
     */
    suspend fun unfollow(id: String): ApiResult<Boolean>
}
