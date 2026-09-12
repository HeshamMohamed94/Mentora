package com.mentora.shared.domain.model

/**
 * A single item of `GET /api/v1/learning-paths` — mirrors the backend's `LearningPathSummary`
 * (`backend/src/main/kotlin/com/mentora/backend/learningpaths/service/LearningPathService.kt:13-15`)
 * field-for-field: `id, title, description, courseCount`.
 *
 * `GET /api/v1/learning-paths` returns a plain JSON array as the envelope's `data` — never
 * `CursorPage<LearningPath>` — see `LearningPathRoutes.kt:20`'s bare `get { call.respondData(service.list()) }`
 * (not under `authenticate`, so this endpoint is guest-accessible with no auth state required to call it).
 *
 * Deliberately has NO `isFollowing` field: `LearningPathSummary` on the backend genuinely has no such
 * property (it is a structurally different type from `LearningPathResponse`, not just a summary with
 * the field defaulted to `false`) — `shared` must never synthesize/fabricate one here.
 *
 * Named `LearningPath` (not `LearningPathSummary`) for the list item, paired with `LearningPathDetail`
 * for `GET /api/v1/learning-paths/{id}` — the bare-name-for-... asymmetric convention already used by
 * `CourseSummary`/`Course` (Task 7), per `execution/PHASE_3_KMP_PLAN.md`'s module tree (§4), which
 * lists `LearningPath, LearningPathDetail` (not `LearningPathSummary`).
 */
data class LearningPath(
    val id: String,
    val title: String,
    val description: String,
    val courseCount: Int,
)
